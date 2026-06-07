package com.smartpos.service;

import com.smartpos.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SettingsService {

    /**
     * Pulls all systemic app configurations, injecting fallback default structural parameters if absent.
     */
    public Map<String, String> getAll() {
        // Escaped 'key' and 'value' keywords using standard SQL boundaries to prevent parsing glitches
        String sql = "SELECT [key], [value] FROM app_settings";
        Map<String, String> map = new HashMap<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
            
            // Clean, predictable fallback parameters for immediate JavaFX UI structural rendering
            map.putIfAbsent("theme", "light");
            map.putIfAbsent("backgroundColor", "#f4f4f4");
            
            return map;
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Failed to extract systemic application settings map.", ex);
        }
    }

    /**
     * Updates or inserts a configuration setting key atomic parameter block cleanly.
     */
    public void set(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Configuration parameter setting updates require a valid non-empty key identity token.");
        }

        String sql = "INSERT INTO app_settings([key], [value]) VALUES(?, ?) " +
                     "ON CONFLICT([key]) DO UPDATE SET [value] = EXCLUDED.[value]";
                     
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, key.trim());
            ps.setString(2, value != null ? value.trim() : "");
            ps.executeUpdate();
            
        } catch (SQLException ex) {
            throw new RuntimeException("Database Error: Failed to commit unique structural configuration update for key: " + key, ex);
        }
    }
}