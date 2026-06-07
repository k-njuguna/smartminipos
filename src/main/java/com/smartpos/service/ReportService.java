package com.smartpos.service;

import com.smartpos.db.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReportService {
    
    // Clean, immutable DTO Records matching your JavaFX TableView bindings
    public record SalesLine(String saleId, String timestamp, String cashier, double total, String itemsSold) {}
    public record LowStockLine(String productName, long stock) {}

    /**
     * Pulls summarized ledger entries for a target date string (Format: "yyyy-MM-dd").
     * Uses optimized relational indices instead of full-table scans.
     */
    public List<SalesLine> getSalesByDate(String dateStr) {
        if (dateStr == null || dateStr.trim().length() != 10) {
            throw new IllegalArgumentException("Invalid date query token. Format must follow 'yyyy-MM-dd'.");
        }

        // Lightning-Fast Indexed Range Bound Logic:
        // "2026-05-30" expands into >= "2026-05-30 00:00:00" and <= "2026-05-30 23:53:59"
        String startRange = dateStr.trim() + " 00:00:00";
        String endRange = dateStr.trim() + " 23:59:59";

        String sql = """
                SELECT s.id, s.timestamp, u.username, s.total, 
                       GROUP_CONCAT(p.name || ' (x' || si.quantity || ')', ', ') as items
                FROM sales s
                JOIN users u ON u.id = s.user_id
                JOIN sale_items si ON si.sale_id = s.id
                JOIN products p ON p.id = si.product_id
                WHERE s.timestamp >= ? AND s.timestamp <= ?
                GROUP BY s.id
                ORDER BY s.timestamp DESC
                """;
                
        List<SalesLine> rows = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, startRange);
            ps.setString(2, endRange);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SalesLine(
                        "#" + rs.getLong("id"), 
                        rs.getString("timestamp"), 
                        rs.getString("username"), 
                        rs.getDouble("total"),
                        rs.getString("items")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) { 
            throw new RuntimeException("Database Error: Failed to compile sales ledger metrics.", ex); 
        }
    }

    /**
     * Compiles complete master catalog stock summaries sorted from lowest to highest.
     */
    public List<LowStockLine> getFullInventoryReport() {
        String sql = "SELECT name, stock FROM products ORDER BY stock ASC, name ASC";
        List<LowStockLine> rows = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                rows.add(new LowStockLine(rs.getString("name"), rs.getLong("stock")));
            }
            return rows;
        } catch (SQLException ex) { 
            throw new RuntimeException("Database Error: Inventory data compilation task failed.", ex); 
        }
    }   
}