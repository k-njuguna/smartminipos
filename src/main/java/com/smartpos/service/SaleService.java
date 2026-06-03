package com.smartpos.service;

import com.smartpos.db.DatabaseManager;
import com.smartpos.model.CartItem;
import com.smartpos.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SaleService {
    
    private static final DateTimeFormatter ISO_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ActivityLogService activityLogService = new ActivityLogService();

    /**
     * Executes a complete checkout transaction securely.
     * Guarantees absolute ACID properties via single-compilation batch statement caching.
     */
    public long checkout(List<CartItem> cartItems, User user, String paymentMethod) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Checkout aborted: The shopping cart contains no valid line items.");
        }

        String saleSql = "INSERT INTO sales(total, timestamp, user_id, is_synced) VALUES (?,?,?,0)";
        String itemSql = "INSERT INTO sale_items(sale_id, product_id, quantity, price, subtotal) VALUES (?,?,?,?,?)";
        String productUpdateSql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
        String stockMovementSql = "INSERT INTO stock_movements(product_id, change_qty, reason, timestamp, is_synced) VALUES (?,?,?,?,0)";
        String paymentSql = "INSERT INTO payments(sale_id, method, amount) VALUES (?,?,?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            // Turn off auto-commit to secure our structural block boundary
            conn.setAutoCommit(false);

            double total = cartItems.stream().mapToDouble(CartItem::getSubtotal).sum();
            String currentTimestamp = LocalDateTime.now().format(ISO_DATETIME_FORMATTER);
            long saleId;

            try {
                // 1. Process Core Invoice Ledger Shell
                try (PreparedStatement salePs = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                    salePs.setDouble(1, total);
                    salePs.setString(2, currentTimestamp);
                    salePs.setLong(3, user.id());
                    salePs.executeUpdate();
                    
                    try (ResultSet keys = salePs.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Critical Error: Database failed to generate structural Invoice Key record.");
                        }
                        saleId = keys.getLong(1);
                    }
                }

                // Optimization: Compile statement maps OUTSIDE the loop.
                // SQLite parses these string tracks exactly once, maximizing multi-row throughput.
                try (PreparedStatement itemPs = conn.prepareStatement(itemSql);
                     PreparedStatement stockPs = conn.prepareStatement(productUpdateSql);
                     PreparedStatement movementPs = conn.prepareStatement(stockMovementSql)) {

                    for (CartItem item : cartItems) {
                        long productId = item.getProduct().getId();
                        int qty = item.getQuantity();

                        // A. Map Sale Item Row
                        itemPs.setLong(1, saleId);
                        itemPs.setLong(2, productId);
                        itemPs.setInt(3, qty);
                        itemPs.setDouble(4, item.getUnitPrice());
                        itemPs.setDouble(5, item.getSubtotal());
                        itemPs.executeUpdate();

                        // B. Deduct Live Stock Safely (Guarantees no negative stock)
                        stockPs.setInt(1, qty);
                        stockPs.setLong(2, productId);
                        stockPs.setInt(3, qty);
                        int updated = stockPs.executeUpdate();
                        if (updated == 0) {
                            throw new SQLException("Checkout Blocked: Insufficient stock available for product: " + item.getName());
                        }

                        // C. Log Historic Inventory Audit Movement Trail
                        movementPs.setLong(1, productId);
                        movementPs.setInt(2, -qty); // Negative drop notation
                        movementPs.setString(3, "SALE#" + saleId);
                        movementPs.setString(4, currentTimestamp);
                        movementPs.executeUpdate();
                    }
                }

                // 2. Process Payment Entry Block
                try (PreparedStatement paymentPs = conn.prepareStatement(paymentSql)) {
                    paymentPs.setLong(1, saleId);
                    paymentPs.setString(2, paymentMethod != null ? paymentMethod.trim() : "CASH");
                    paymentPs.setDouble(3, total);
                    paymentPs.executeUpdate();
                }

                // 3. Log Audit Entry utilizing the active upstream transaction block
                activityLogService.log(conn, user.id(), "Completed sale #" + saleId + " | Total Value: " + total);

                // Commit the entire transaction atomically
                conn.commit();
                return saleId;

            } catch (Exception transactionEx) {
                // Instantly rolls back changes if a step fails (e.g., intermediate stock out)
                conn.rollback();
                throw transactionEx;
            }

        } catch (Exception ex) {
            throw new RuntimeException("Transaction Engine Aborted: " + ex.getMessage(), ex);
        }
    }
}