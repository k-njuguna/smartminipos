package com.smartpos.app;

import com.smartpos.model.User;
import com.smartpos.service.*;
import com.smartpos.service.sync.SyncServiceImpl;
import com.smartpos.service.sync.SyncService;
import com.smartpos.util.LicenseManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AppContext {
    private final AuthService authService = new AuthService();
    private final ProductService productService = new ProductService();
    private final SaleService saleService = new SaleService();
    private final ReportService reportService = new ReportService();
    private final SettingsService settingsService = new SettingsService();
    private final ActivityLogService activityLogService = new ActivityLogService();
    private final BackupService backupService = new BackupService();
    
    // Managed thread pool for asynchronous background tasks
    private final ExecutorService executor;
    
    // Volatile reference ensures thread-safe lazy loading across background synchronization tasks
    private volatile SyncService syncService;

    private User currentUser;
    private volatile boolean transactionInProgress = false; 

    // JavaFX Observable Properties for real-time UI binding
    private final StringProperty shopName = new SimpleStringProperty();
    private final StringProperty selectedLayoutMode = new SimpleStringProperty("Standard");
    private final StringProperty activeTerminalTheme = new SimpleStringProperty("light");

    public AppContext() {
        // Core constructor stays microsecond-fast.
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("pos-async-worker-");
            return thread;
        });

        // Initialize shop name from license file
        this.shopName.set(LicenseManager.getShopName());

        // Initialize properties safely matching your native settingsService.getAll() map layout
        try {
            Map<String, String> savedConfig = settingsService.getAll();
            
            String savedLayout = savedConfig.get("layout_mode");
            if (savedLayout != null) selectedLayoutMode.set(savedLayout);
            
            String savedTheme = savedConfig.get("theme");
            if (savedTheme != null) activeTerminalTheme.set(savedTheme);
        } catch (Exception e) {
            System.err.println("Notice: Default properties applied due to initial start context state: " + e.getMessage());
        }
    }

    // --- Shop Name Methods ---
    public StringProperty shopNameProperty() { return shopName; }
    public String getShopName() { return shopName.get(); }
    public void setShopName(String name) { this.shopName.set(name); }

    // Expose the executor for context.getExecutor().execute(...)
    public ExecutorService getExecutor() { 
        return executor; 
    }

    public AuthService authService() { return authService; }
    public ProductService productService() { return productService; }
    public SaleService saleService() { return saleService; }
    public ReportService reportService() { return reportService; }
    public SettingsService settingsService() { return settingsService; }
    public ActivityLogService activityLogService() { return activityLogService; }
    public BackupService backupService() { return backupService; }
    
    // --- Live UI Binding Properties ---
    
    public StringProperty selectedLayoutModeProperty() { 
        return selectedLayoutMode; 
    }
    public String getSelectedLayoutMode() { 
        return selectedLayoutMode.get(); 
    }
    public void setSelectedLayoutMode(String mode) {
        this.selectedLayoutMode.set(mode);
        executor.execute(() -> settingsService.set("layout_mode", mode));
    }

    public StringProperty activeTerminalThemeProperty() { 
        return activeTerminalTheme; 
    }
    public String getActiveTerminalTheme() { 
        return activeTerminalTheme.get(); 
    }
    public void setActiveTerminalTheme(String theme) {
        this.activeTerminalTheme.set(theme);
        executor.execute(() -> settingsService.set("theme", theme));
    }

    /**
     * Lazy Loading Pattern for SyncService
     */
    public SyncService syncService() {
        SyncService localRef = syncService;
        if (localRef == null) {
            synchronized (this) {
                localRef = syncService;
                if (localRef == null) {
                    syncService = localRef = new SyncServiceImpl(this);
                }
            }
        }
        return localRef;
    }

    // User Session Methods
    public User currentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }
    public void logout() { this.currentUser = null; }

    // --- Transaction Status Logic ---
    
    public boolean isTransactionInProgress() {
        return transactionInProgress;
    }

    public void setTransactionInProgress(boolean inProgress) {
        this.transactionInProgress = inProgress;
    }

    /**
     * Gracefully shuts down the background thread pool
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}