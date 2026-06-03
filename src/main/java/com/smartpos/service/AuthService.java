package com.smartpos.service;

import com.smartpos.db.DatabaseManager;
import com.smartpos.model.User;
import com.smartpos.model.enums.Role;
import com.smartpos.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AuthService {

    /**
     * Authenticates operators utilizing defensive case-insensitive matching profiles.
     */
    public Optional<User> login(String username, String password) {
        // Injected 'COLLATE NOCASE' to guarantee operators aren't locked out due to character case issues
        String sql = "SELECT id, username, password, role FROM users WHERE username = ? COLLATE NOCASE";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, username != null ? username.trim() : "");
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    
                    if (!PasswordUtil.matches(password, storedPassword)) {
                        return Optional.empty();
                    }
                    
                    long userId = rs.getLong("id");
                    
                    // In-place schema migration fallback if legacy rows exist
                    if (!PasswordUtil.isHashed(storedPassword)) {
                        updatePassword(userId, password);
                    }
                    
                    return Optional.of(new User(
                        userId, 
                        rs.getString("username"), 
                        "", // Keeps password reference strictly empty in runtime context
                        Role.valueOf(rs.getString("role"))
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Core login authentication execution failed.", ex);
        }
        return Optional.empty();
    }

    public List<User> findAllUsers() {
        String sql = "SELECT id, username, role FROM users ORDER BY username ASC";
        List<User> users = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                users.add(new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    "",
                    Role.valueOf(rs.getString("role"))
                ));
            }
            return users;
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Failed to pull systemic user directory listings.", ex);
        }
    }

    public void createUser(String username, String password, Role role) {
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.isBlank()) {
            throw new IllegalArgumentException("A valid non-empty username configuration is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Security profile require a valid user password assignment.");
        }
        
        String sql = "INSERT INTO users(username, password, role) VALUES (?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, cleanUsername);
            ps.setString(2, PasswordUtil.hashPassword(password));
            ps.setString(3, role.name());
            ps.executeUpdate();
            
        } catch (SQLException ex) {
            // Catches standard SQLite UNIQUE constraint failures gracefully if user name is taken
            if (ex.getErrorCode() == 19) { 
                throw new IllegalArgumentException("Registration Denied: Username already exists in the system.");
            }
            throw new RuntimeException("Database Error: User entry persistence task failed.", ex);
        }
    }

    /**
     * Safely drops an assigned user record after confirming identity boundaries.
     */
    public void deleteUser(long targetUserId, long currentSessionUserId) {
        if (targetUserId == currentSessionUserId) {
            throw new IllegalArgumentException("Operation Denied: Safety protocol prevents active administrators from self-deleting.");
        }

        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, targetUserId);
            if (ps.executeUpdate() == 0) {
                throw new RuntimeException("Target profile row index missing or altered.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Execution halted dropping user database sequence.", ex);
        }
    }

    public void updatePassword(long userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Updated security variables cannot pass blank password configurations.");
        }
        
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, PasswordUtil.hashPassword(newPassword));
            ps.setLong(2, userId);
            
            if (ps.executeUpdate() == 0) {
                throw new RuntimeException("Target profile row index missing or altered.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Password override updates failed.", ex);
        }
    }
}