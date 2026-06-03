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

    public static Path getDatabasePath() {
        return DB_PATH;
    }

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
            
            // CLEANED: barcode removed
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
            
            // Remaining tables remain consistent
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
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(products)")) {
            boolean hasBarcode = false;
            boolean hasThreshold = false;
            
            while (rs.next()) {
                String name = rs.getString("name");
                if ("barcode".equals(name)) hasBarcode = true;
                if ("low_stock_threshold".equals(name)) hasThreshold = true;
            }

            if (hasBarcode) {
                // Perform migration: Rename, create clean table, copy data, drop old
                st.execute("ALTER TABLE products RENAME TO old_products");
                st.execute("""
                    CREATE TABLE products (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT NOT NULL,
                      price REAL NOT NULL,
                      stock INTEGER NOT NULL DEFAULT 0,
                      low_stock_threshold INTEGER DEFAULT NULL,
                      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                """);
                st.execute("INSERT INTO products (id, name, price, stock) SELECT id, name, price, stock FROM old_products");
                st.execute("DROP TABLE old_products");
            } else if (!hasThreshold) {
                st.execute("ALTER TABLE products ADD COLUMN low_stock_threshold INTEGER DEFAULT NULL");
            }
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