package com.smartpos.db;

import com.smartpos.util.PasswordUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

public final class DatabaseManager {
    private static final Path DB_PATH;
    private static final String JDBC_URL;

    static {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path baseDir = (localAppData != null) ? Paths.get(localAppData, "SmartPOS") : Paths.get(System.getProperty("user.home"), "SmartPOS");
        DB_PATH = baseDir.resolve("offline_pos.db");
        JDBC_URL = "jdbc:sqlite:" + DB_PATH.toString();
    }

    private DatabaseManager() {}

    public static Path getDatabasePath() { return DB_PATH; }


    public static Connection getConnection() throws SQLException {
        File dbFolder = DB_PATH.getParent().toFile();
        if (!dbFolder.exists()) dbFolder.mkdirs();
        
        Connection conn = DriverManager.getConnection(JDBC_URL);
        conn.createStatement().execute("PRAGMA foreign_keys = ON;");
        return conn;
    }

    public static void initializeSchema() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            
            st.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL)");
            

            st.execute("""
                CREATE TABLE IF NOT EXISTS products (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  price REAL NOT NULL,
                  stock INTEGER NOT NULL DEFAULT 0,
                  low_stock_threshold INTEGER DEFAULT NULL,
                  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                  updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name)");
            

            st.execute("""
                CREATE TABLE IF NOT EXISTS sales (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  total REAL NOT NULL,
                  timestamp TEXT NOT NULL,
                  user_id INTEGER NOT NULL,
                  is_synced INTEGER NOT NULL DEFAULT 0,
                  FOREIGN KEY(user_id) REFERENCES users(id)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sale_items (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  sale_id INTEGER NOT NULL,
                  product_id INTEGER NOT NULL,
                  quantity INTEGER NOT NULL,
                  price REAL NOT NULL,
                  subtotal REAL NOT NULL,
                  FOREIGN KEY(sale_id) REFERENCES sales(id),
                  FOREIGN KEY(product_id) REFERENCES products(id)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS stock_movements (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  product_id INTEGER NOT NULL,
                  change_qty INTEGER NOT NULL,
                  reason TEXT NOT NULL,
                  timestamp TEXT NOT NULL,
                  is_synced INTEGER NOT NULL DEFAULT 0,
                  FOREIGN KEY(product_id) REFERENCES products(id)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS credits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_name TEXT NOT NULL,
                    phone TEXT,
                    timestamp TEXT NOT NULL,
                    curr_status TEXT NOT NULL,
                    user_id INTEGER,
                    FOREIGN KEY(user_id) REFERENCES users(id)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS credit_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    credit_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    price REAL NOT NULL,
                    FOREIGN KEY(credit_id) REFERENCES credits(id),
                    FOREIGN KEY(product_id) REFERENCES products(id)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS credit_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    credit_id INTEGER NOT NULL,
                    amount_paid REAL NOT NULL,
                    date_paid TEXT NOT NULL,
                    user_id INTEGER,
                    FOREIGN KEY(credit_id) REFERENCES credits(id),
                    FOREIGN KEY(user_id) REFERENCES users(id)
                )
            """);

            st.execute("CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, method TEXT NOT NULL, amount REAL NOT NULL, FOREIGN KEY(sale_id) REFERENCES sales(id))");
            st.execute("CREATE TABLE IF NOT EXISTS activity_log (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, action TEXT NOT NULL, timestamp TEXT NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id))");
            st.execute("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");

            handleSchemaMigrations(conn);
            
            st.execute("INSERT OR IGNORE INTO users(username,password,role) VALUES ('admin','" + PasswordUtil.hashPassword("admin@admin") + "','ADMIN')");
            st.execute("INSERT OR IGNORE INTO users(username,password,role) VALUES ('user','" + PasswordUtil.hashPassword("user123") + "','USER')");
            
            migrateLegacyPlaintextPasswords(conn);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize DB schema", ex);
        }
    }

    private static void handleSchemaMigrations(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            ResultSet rsProducts = st.executeQuery("PRAGMA table_info(products)");
            boolean hasThreshold = false;
            while (rsProducts.next()) {
                if ("low_stock_threshold".equals(rsProducts.getString("name"))) hasThreshold = true;
            }
            if (!hasThreshold) { st.execute("ALTER TABLE products ADD COLUMN low_stock_threshold INTEGER DEFAULT NULL"); }

        }
    }

    private static void migrateLegacyPlaintextPasswords(Connection conn) throws SQLException {
        String selectSql = "SELECT id, password FROM users";
        String updateSql = "UPDATE users SET password = ? WHERE id = ?";
        try (PreparedStatement selectPs = conn.prepareStatement(selectSql);
             ResultSet rs = selectPs.executeQuery();
             PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String password = rs.getString("password");
                if (!PasswordUtil.isHashed(password)) {
                    updatePs.setString(1, PasswordUtil.hashPassword(password));
                    updatePs.setLong(2, id);
                    updatePs.executeUpdate();
                }
            }
        }
    }
}