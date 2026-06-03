package com.smartpos.service;

import com.smartpos.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ActivityLogService {

    // Pre-compiled formatter for optimized local database indexing structures
    private static final DateTimeFormatter ISO_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Standard standalone log action. Opens a short-lived standalone connection.
     */
    public void log(long userId, String action) {
        String sql = "INSERT INTO activity_log(user_id, action, timestamp) VALUES (?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            logInternal(ps, userId, action);
            
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Failed to write individual application activity log.", ex);
        }
    }

    /**
     * Powerful Overloaded Transaction Log Method:
     * Injects the log statement directly into an active, upstream transactional database connection.
     * Prevents SQLite database file-locking bottlenecks during nested batch updates.
     */
    public void log(Connection conn, long userId, String action) throws SQLException {
        String sql = "INSERT INTO activity_log(user_id, action, timestamp) VALUES (?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            logInternal(ps, userId, action);
        }
    }

    /**
     * Consolidated internal payload mapping block.
     */
    private void logInternal(PreparedStatement ps, long userId, String action) throws SQLException {
        ps.setLong(1, userId);
        ps.setString(2, action);
        // Uses clean alphanumeric formats ensuring fast timeline filtering passes
        ps.setString(3, LocalDateTime.now().format(ISO_DATETIME_FORMATTER));
        ps.executeUpdate();
    }
}