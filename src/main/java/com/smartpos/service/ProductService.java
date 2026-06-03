package com.smartpos.service;

import com.smartpos.db.DatabaseManager;
import com.smartpos.model.Product;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductService {

    private static final DateTimeFormatter ISO_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ActivityLogService activityLogService = new ActivityLogService();

    public List<Product> findAll() {
        String sql = "SELECT id, name, price, stock, low_stock_threshold FROM products ORDER BY id DESC";
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                products.add(readProduct(rs));
            }
            return products;
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Failed to fetch products layout dictionary.", ex);
        }
    }

    public List<Product> searchByName(String name) {
        String sql = "SELECT id, name, price, stock, low_stock_threshold FROM products WHERE name LIKE ? ORDER BY name ASC";
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + name + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(readProduct(rs));
                }
            }
            return products;
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Failed to execute search parameter pass.", ex);
        }
    }

    public List<Product> findLowStock(int threshold, int limit) {
        String sql = """
                SELECT id, name, price, stock, low_stock_threshold
                FROM products
                WHERE stock <= ?
                ORDER BY stock ASC, name ASC
                LIMIT ?
                """;
        List<Product> products = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(readProduct(rs));
                }
            }
            return products;
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Low stock retrieval metrics failed.", ex);
        }
    }

    public void createProduct(String name, double price, long stock, Long threshold, long userId) {
        String sql = "INSERT INTO products(name, price, stock, low_stock_threshold) VALUES (?,?,?,?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // Turn off auto-commit to make insertion atomic
            
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setDouble(2, price);
                ps.setLong(3, stock);
                
                if (threshold != null) {
                    ps.setLong(4, threshold);
                } else {
                    ps.setNull(4, Types.BIGINT);
                }
                
                ps.executeUpdate();
                long productId;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Product generation failed, token ID dropped.");
                    }
                    productId = keys.getLong(1);
                }
                
                insertStockMovement(conn, productId, (int) stock, "INITIAL_STOCK");
                
                // Optimized: Reuses active connection transaction block
                activityLogService.log(conn, userId, "Created product: " + name);
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Product creation transaction rolled back.", ex);
        }
    }

    public void updateProduct(long id, String name, double price, long stock, Long threshold, long userId) {
        String select = "SELECT stock FROM products WHERE id = ?";
        String update = "UPDATE products SET name=?, price=?, stock=?, low_stock_threshold=? WHERE id=?";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            long oldStock;
            try (PreparedStatement selectPs = conn.prepareStatement(select)) {
                selectPs.setLong(1, id);
                try (ResultSet rs = selectPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Product modifications aborted: Target ID missing.");
                    }
                    oldStock = rs.getLong("stock");
                }
            }
            
            try (PreparedStatement updatePs = conn.prepareStatement(update)) {
                updatePs.setString(1, name);
                updatePs.setDouble(2, price);
                updatePs.setLong(3, stock);
                
                if (threshold != null) {
                    updatePs.setLong(4, threshold);
                } else {
                    updatePs.setNull(4, Types.BIGINT);
                }
                
                updatePs.setLong(5, id);
                updatePs.executeUpdate();
            }
            
            int diff = (int) (stock - oldStock);
            if (diff != 0) {
                insertStockMovement(conn, id, diff, "MANUAL_ADJUSTMENT");
            }
            
            // Optimized: Reuses active connection transaction block to guarantee zero file-locks
            activityLogService.log(conn, userId, "Updated product: " + name);
            
            conn.commit();
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Product data update batch rolled back.", ex);
        }
    }

    private void insertStockMovement(Connection conn, long productId, int qtyChange, String reason) throws SQLException {
        String sql = "INSERT INTO stock_movements(product_id, change_qty, reason, timestamp, is_synced) VALUES (?,?,?,?,0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            ps.setInt(2, qtyChange);
            ps.setString(3, reason);
            ps.setString(4, LocalDateTime.now().format(ISO_DATETIME_FORMATTER));
            ps.executeUpdate();
        }
    }

    private Product readProduct(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String name = rs.getString("name");
        double price = rs.getDouble("price");
        long stock = rs.getLong("stock");
        
        long thresholdVal = rs.getLong("low_stock_threshold");
        Long threshold = rs.wasNull() ? null : thresholdVal;

        // References our beautifully clean, 5-parameter Product model
        return new Product(id, name, price, stock, threshold);
    }

    public void deleteProduct(long id, String name, long userId) {
        String checkSalesSql = "SELECT COUNT(*) FROM sale_items WHERE product_id = ?";
        String deleteSql = "DELETE FROM products WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // 1. Referential Integrity Lock Check
                try (PreparedStatement psCheck = conn.prepareStatement(checkSalesSql)) {
                    psCheck.setLong(1, id);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new RuntimeException("Cannot delete: This product has historical sales records. Try setting stock to 0 instead.");
                        }
                    }
                }

                // 2. Execute Deletion
                try (PreparedStatement psDelete = conn.prepareStatement(deleteSql)) {
                    psDelete.setLong(1, id);
                    int affectedRows = psDelete.executeUpdate();
                    
                    if (affectedRows == 0) {
                        throw new SQLException("Target product entry missing or already altered.");
                    }
                }

                // 3. Log using the identical transaction lane
                activityLogService.log(conn, userId, "Deleted product: " + name + " (ID: " + id + ")");
                
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Product deletion sequence failed.", ex);
        }
    }
}