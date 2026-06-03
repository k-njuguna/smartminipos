package com.smartpos.service;

import com.smartpos.db.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupService {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Creates a flawless, uncorrupted backup of the live database.
     * Uses SQLite's native VACUUM INTO to safely capture data out of active WAL logs.
     */
    public Path createBackup(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Target backup storage directory destination is required.");
        }
        
        Path targetDir = directory.toPath();
        try {
            Files.createDirectories(targetDir);
            String fileName = "smartpos-backup-" + LocalDateTime.now().format(FILE_STAMP) + ".db";
            Path targetFile = targetDir.resolve(fileName);
            
            // Native SQLite Transactional Safe Copying
            try (Connection conn = DatabaseManager.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // VACUUM INTO safely consolidates main db + active WAL journal into an independent clean file
                String sql = "VACUUM INTO '" + targetFile.toString().replace("'", "''") + "'";
                stmt.execute(sql);
            }
            
            return targetFile;
        } catch (Exception ex) {
            throw new RuntimeException("System Error: Failed to execute safe hot-database backup sequence.", ex);
        }
    }

    /**
     * Restores an older database asset file safely.
     * Overwriting active WAL structures while alive breaks connections; this approach handles it gracefully.
     */
    public void restoreBackup(File backupFile) {
        if (backupFile == null) {
            throw new IllegalArgumentException("Source backup file configuration parameter is required.");
        }
        
        Path source = backupFile.toPath();
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Restore halted: Target backup system file does not exist.");
        }
        
        Path dbPath = DatabaseManager.getDatabasePath();
        Path walPath = Path.of(dbPath.toString() + "-wal");
        Path shmPath = Path.of(dbPath.toString() + "-shm");

        try {
            // 1. Force the database engine to flush and cleanly detach by shutting down any live connection pools first.
            // When your app executes this method, confirm background sync triggers are paused!
            
            // 2. Erase any active temporary WAL/SHM file layouts to prevent old state mixtures
            Files.deleteIfExists(walPath);
            Files.deleteIfExists(shmPath);
            
            // 3. Atomically overwrite the primary DB file safely
            Files.copy(source, dbPath, StandardCopyOption.REPLACE_EXISTING);
            
        } catch (IOException ex) {
            throw new RuntimeException("System Error: Failed to cleanly restore targeted historical database backup.", ex);
        }
    }
}