package com.smartpos.service;

import com.smartpos.db.DatabaseManager;
import com.smartpos.model.CartItem;
import com.smartpos.model.User;
import com.smartpos.model.Credit;
import com.smartpos.model.Product;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SaleService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityLogService activityLogService = new ActivityLogService();

    // =========================================================
    // 1. CHECKOUT (CASH SALE)
    // =========================================================
    public long checkout(List<CartItem> cartItems, User user, String paymentMethod) {

        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty.");
        }

        String saleSql = "INSERT INTO sales(total, timestamp, user_id, is_synced) VALUES (?,?,?,0)";
        String itemSql = "INSERT INTO sale_items(sale_id, product_id, quantity, price, subtotal) VALUES (?,?,?,?,?)";
        String stockSql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
        String movementSql = "INSERT INTO stock_movements(product_id, change_qty, reason, timestamp, is_synced) VALUES (?,?,?,?,0)";
        String paymentSql = "INSERT INTO payments(sale_id, method, amount) VALUES (?,?,?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            double total = cartItems.stream().mapToDouble(CartItem::getSubtotal).sum();
            String ts = LocalDateTime.now().format(FORMATTER);
            long saleId;

            try {
                // SALE HEADER
                try (PreparedStatement ps = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDouble(1, total);
                    ps.setString(2, ts);
                    ps.setLong(3, user.id());
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("No sale ID generated");
                        saleId = rs.getLong(1);
                    }
                }

                // ITEMS + STOCK
                try (PreparedStatement itemPs = conn.prepareStatement(itemSql);
                     PreparedStatement stockPs = conn.prepareStatement(stockSql);
                     PreparedStatement movePs = conn.prepareStatement(movementSql)) {

                    for (CartItem item : cartItems) {
                        long productId = item.getProduct().getId();
                        int qty = item.getQuantity();

                        itemPs.setLong(1, saleId);
                        itemPs.setLong(2, productId);
                        itemPs.setInt(3, qty);
                        itemPs.setDouble(4, item.getUnitPrice());
                        itemPs.setDouble(5, item.getSubtotal());
                        itemPs.executeUpdate();

                        stockPs.setInt(1, qty);
                        stockPs.setLong(2, productId);
                        stockPs.setInt(3, qty);

                        if (stockPs.executeUpdate() == 0)
                            throw new SQLException("Insufficient stock: " + item.getProduct().getName());

                        movePs.setLong(1, productId);
                        movePs.setInt(2, -qty);
                        movePs.setString(3, "SALE#" + saleId);
                        movePs.setString(4, ts);
                        movePs.executeUpdate();
                    }
                }

                // PAYMENT
                try (PreparedStatement ps = conn.prepareStatement(paymentSql)) {
                    ps.setLong(1, saleId);
                    ps.setString(2, paymentMethod != null ? paymentMethod.trim() : "CASH");
                    ps.setDouble(3, total);
                    ps.executeUpdate();
                }

                activityLogService.log(conn, user.id(), "Sale #" + saleId);
                conn.commit();
                return saleId;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================
    // 2. CREDIT SALE
    // =========================================================
    public long saveCreditSale(List<CartItem> cartItems,
                                User user,
                                String customerName,
                                String phone) throws SQLException {

        String creditSql = "INSERT INTO credits(customer_name, phone, timestamp, curr_status, user_id) VALUES (?,?,?,?,?)";
        String itemSql = "INSERT INTO credit_items(credit_id, product_id, quantity, price) VALUES (?,?,?,?)";
        String stockSql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            String ts = LocalDateTime.now().format(FORMATTER);
            long creditId;

            try {
                // CREDIT HEADER
                try (PreparedStatement ps = conn.prepareStatement(creditSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, customerName);
                    ps.setString(2, phone != null && !phone.isBlank() ? phone : null);
                    ps.setString(3, ts);
                    ps.setString(4, "ACTIVE");
                    ps.setLong(5, user.id());
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        creditId = rs.getLong(1);
                    }
                }

                // ITEMS + STOCK
                try (PreparedStatement itemPs = conn.prepareStatement(itemSql);
                     PreparedStatement stockPs = conn.prepareStatement(stockSql)) {

                    for (CartItem item : cartItems) {
                        long productId = item.getProduct().getId();
                        int qty = item.getQuantity();

                        itemPs.setLong(1, creditId);
                        itemPs.setLong(2, productId);
                        itemPs.setInt(3, qty);
                        itemPs.setDouble(4, item.getUnitPrice());
                        itemPs.executeUpdate();

                        stockPs.setInt(1, qty);
                        stockPs.setLong(2, productId);
                        stockPs.setInt(3, qty);

                        if (stockPs.executeUpdate() == 0)
                            throw new SQLException("Insufficient stock: " + item.getProduct().getName());
                    }
                }

                activityLogService.log(conn, user.id(), "Credit #" + creditId + " for " + customerName);
                conn.commit();
                return creditId;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    // =========================================================
    // 3. CREDIT PAYMENT + AUTO CONVERSION
    // =========================================================
    public void processCreditPayment(long creditId, double amount, User user) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Calculate Balances
            double totalCost = 0, totalPaid = 0;
            String calcSql = "SELECT (SELECT SUM(price * quantity) FROM credit_items WHERE credit_id = ?) as cost, " +
                             "(SELECT IFNULL(SUM(amount_paid), 0) FROM credit_payments WHERE credit_id = ?) as paid";
            try (PreparedStatement ps = conn.prepareStatement(calcSql)) {
                ps.setLong(1, creditId); ps.setLong(2, creditId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { totalCost = rs.getDouble("cost"); totalPaid = rs.getDouble("paid"); }
                }
            }

            double balance = totalCost - totalPaid;
            if (balance <= 0.0001) throw new IllegalStateException("Credit already fully paid.");
            if (amount > balance) throw new IllegalArgumentException("Payment exceeds balance of " + balance);

            // 2. Insert Payment
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO credit_payments(credit_id, amount_paid, date_paid, user_id) VALUES (?,?,?,?)")) {
                ps.setLong(1, creditId); ps.setDouble(2, amount);
                ps.setString(3, LocalDateTime.now().format(FORMATTER)); ps.setLong(4, user.id());
                ps.executeUpdate();
            }

            // 3. Trigger Conversion to Sale if balance is 0
            if ((totalPaid + amount) >= (totalCost - 0.0001)) {
                long saleId;
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO sales(total, timestamp, user_id, is_synced) VALUES (?,?,?,0)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDouble(1, totalCost); ps.setString(2, LocalDateTime.now().format(FORMATTER)); ps.setLong(3, user.id());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); saleId = rs.getLong(1); }
                }

                List<CartItem> items = loadCreditItems(conn, creditId);
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO sale_items(sale_id, product_id, quantity, price, subtotal) VALUES (?,?,?,?,?)")) {
                    for (CartItem item : items) {
                        ps.setLong(1, saleId); ps.setLong(2, item.getProduct().getId());
                        ps.setInt(3, item.getQuantity()); ps.setDouble(4, item.getUnitPrice());
                        ps.setDouble(5, item.getUnitPrice() * item.getQuantity()); ps.executeUpdate();
                    }
                }
                conn.prepareStatement("UPDATE credits SET curr_status = 'CLOSED' WHERE id = " + creditId).executeUpdate();
                activityLogService.log(conn, user.id(), "Credit #" + creditId + " converted to Sale #" + saleId);
            }
            conn.commit();
        } catch (Exception e) { throw new SQLException(e); }
    }

    private List<CartItem> loadCreditItems(Connection conn, long creditId) throws SQLException {
        List<CartItem> items = new ArrayList<>();
        String sql = "SELECT ci.product_id, ci.quantity, ci.price, p.name FROM credit_items ci JOIN products p ON ci.product_id = p.id WHERE ci.credit_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, creditId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product p = new Product(rs.getLong("product_id"), rs.getString("name"), rs.getDouble("price"), 0);
                    items.add(new CartItem(p, rs.getInt("quantity")));
                }
            }
        }
        return items;
    }

    // =========================================================
    // 4. ACTIVE CREDITS
    // =========================================================
    public List<Credit> getActiveCredits() throws SQLException {
        List<Credit> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.customer_name, c.phone, u.username AS created_by,
                (SELECT GROUP_CONCAT(p.name, ', ') FROM credit_items ci JOIN products p ON ci.product_id = p.id WHERE ci.credit_id = c.id) AS products,
                (SELECT SUM(ci.price * ci.quantity) FROM credit_items ci WHERE ci.credit_id = c.id) AS total_cost,
                (SELECT IFNULL(SUM(cp.amount_paid),0) FROM credit_payments cp WHERE cp.credit_id = c.id) AS total_paid
            FROM credits c LEFT JOIN users u ON c.user_id = u.id WHERE c.curr_status = 'ACTIVE'
        """;
        try (Connection conn = DatabaseManager.getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Credit c = new Credit(rs.getLong("id"), rs.getString("customer_name"), rs.getString("phone"), rs.getString("products"), rs.getDouble("total_cost"), rs.getDouble("total_paid"));
                c.createdByProperty().set(rs.getString("created_by"));
                list.add(c);
            }
        }
        return list;
    }

    // =========================================================
    // 5. PAYMENT HISTORY
    // =========================================================
    public List<String> getCreditPaymentHistory(long creditId) throws SQLException {
        List<String> history = new ArrayList<>();
        String sql = "SELECT date_paid, amount_paid FROM credit_payments WHERE credit_id = ? ORDER BY date_paid DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, creditId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) history.add(rs.getString("date_paid") + " | " + rs.getDouble("amount_paid"));
            }
        }
        return history;
    }
    
    public List<Credit> getCreditsByStatus(String status) throws SQLException {
    List<Credit> list = new ArrayList<>();
    String sql = """
        SELECT c.id, c.customer_name, c.phone, u.username AS created_by,
            (SELECT GROUP_CONCAT(p.name, ', ') FROM credit_items ci JOIN products p ON ci.product_id = p.id WHERE ci.credit_id = c.id) AS products,
            (SELECT SUM(ci.price * ci.quantity) FROM credit_items ci WHERE ci.credit_id = c.id) AS total_cost,
            (SELECT IFNULL(SUM(cp.amount_paid),0) FROM credit_payments cp WHERE cp.credit_id = c.id) AS total_paid
        FROM credits c 
        LEFT JOIN users u ON c.user_id = u.id 
        WHERE c.curr_status = ?
    """;
    
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, status);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Credit c = new Credit(
                    rs.getLong("id"),
                    rs.getString("customer_name"),
                    rs.getString("phone"),
                    rs.getString("products"),
                    rs.getDouble("total_cost"),
                    rs.getDouble("total_paid")
                );
                c.createdByProperty().set(rs.getString("created_by"));
                list.add(c);
            }
        }
    }
    return list;
}
}