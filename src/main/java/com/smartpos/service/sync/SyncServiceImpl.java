package com.smartpos.service.sync;

import com.smartpos.app.AppContext;
import com.smartpos.db.DatabaseManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SyncServiceImpl implements SyncService {
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> windowExplorerTask = null;

    private final AppContext context;
    private final HttpClient httpClient;
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    
    private String status = "Idle";
    private String lastSyncTime = "Never";

    private static final String FIREBASE_BASE_URL = "https://your-project-id-default-rtdb.firebaseio.com";

    public SyncServiceImpl(AppContext context) {
        this.context = context;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        startBackgroundLoop();
    }

    private void startBackgroundLoop() {
        scheduler.scheduleAtFixedRate(this::startLookingForIdleWindow, 0, 4, TimeUnit.HOURS);
    }

    private synchronized void startLookingForIdleWindow() {
        if (windowExplorerTask != null && !windowExplorerTask.isDone()) {
            return;
        }
        this.status = "Searching for idle window...";
        windowExplorerTask = scheduler.scheduleAtFixedRate(() -> {
            if (isIdleFor(1)) {
                if (isInternetAvailable()) {
                    syncNow();
                    cancelWindowExplorer();
                } else {
                    this.status = "Sync Deferred: Retrying network...";
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private synchronized void cancelWindowExplorer() {
        if (windowExplorerTask != null) {
            windowExplorerTask.cancel(false);
            windowExplorerTask = null;
        }
    }

    @Override
    public String getStatus() {
        return this.status;
    }

    @Override
    public String getLastSyncTime() {
        return this.lastSyncTime;
    }

    @Override
    public String syncNow() {
        this.status = "Synchronizing...";
        int synchronizedSalesCount = 0;
        int synchronizedMovementsCount = 0;
        
        // Sanitize shop name to be URL-safe for Firebase paths
        String shopName = context.getShopName().replaceAll("[^a-zA-Z0-9]", "_");

        try (Connection conn = DatabaseManager.getConnection()) {
            
            // 1. TRANSFORM & SYNC UN-SYNCED SALES RECORDS
            String salesQuery = "SELECT id, total, timestamp, user_id FROM sales WHERE is_synced = 0";
            try (PreparedStatement salesPs = conn.prepareStatement(salesQuery);
                 ResultSet salesRs = salesPs.executeQuery()) {
                
                while (salesRs.next()) {
                    int saleId = salesRs.getInt("id");
                    double total = salesRs.getDouble("total");
                    String timestamp = salesRs.getString("timestamp");
                    int userId = salesRs.getInt("user_id");

                    String saleJson = """
                    {
                      "id": %d,
                      "total": %.2f,
                      "timestamp": "%s",
                      "userId": %d,
                      "shopName": "%s",
                      "desktopSyncedAt": "%s"
                    }
                    """.formatted(saleId, total, timestamp, userId, shopName, LocalDateTime.now().toString());

                    // Namespace the data under the shop name path
                    String targetUrl = FIREBASE_BASE_URL + "/sales/" + shopName + "/" + saleId + ".json";
                    
                    if (sendHttpRequest(targetUrl, saleJson)) {
                        try (PreparedStatement updatePs = conn.prepareStatement("UPDATE sales SET is_synced = 1 WHERE id = ?")) {
                            updatePs.setInt(1, saleId);
                            updatePs.executeUpdate();
                            synchronizedSalesCount++;
                        }
                    }
                }
            }

            // 2. TRANSFORM & SYNC UN-SYNCED STOCK MOVEMENTS
            String stockQuery = "SELECT id, product_id, change_qty, reason, timestamp FROM stock_movements WHERE is_synced = 0";
            try (PreparedStatement stockPs = conn.prepareStatement(stockQuery);
                 ResultSet stockRs = stockPs.executeQuery()) {
                
                while (stockRs.next()) {
                    int movementId = stockRs.getInt("id");
                    int productId = stockRs.getInt("product_id");
                    int changeQty = stockRs.getInt("change_qty");
                    String reason = stockRs.getString("reason");
                    String timestamp = stockRs.getString("timestamp");

                    String stockJson = """
                    {
                      "id": %d,
                      "productId": %d,
                      "changeQty": %d,
                      "reason": "%s",
                      "timestamp": "%s",
                      "shopName": "%s",
                      "desktopSyncedAt": "%s"
                    }
                    """.formatted(movementId, productId, changeQty, reason, timestamp, shopName, LocalDateTime.now().toString());

                    // Namespace the data under the shop name path
                    String targetUrl = FIREBASE_BASE_URL + "/stock_movements/" + shopName + "/" + movementId + ".json";

                    if (sendHttpRequest(targetUrl, stockJson)) {
                        try (PreparedStatement updatePs = conn.prepareStatement("UPDATE stock_movements SET is_synced = 1 WHERE id = ?")) {
                            updatePs.setInt(1, movementId);
                            updatePs.executeUpdate();
                            synchronizedMovementsCount++;
                        }
                    }
                }
            }

            this.lastSyncTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.status = "Idle";
            return "Success: Synced " + synchronizedSalesCount + " sales and " + synchronizedMovementsCount + " stock changes for " + shopName;

        } catch (Exception e) {
            this.status = "Error";
            return "Sync failed: " + e.getMessage();
        }
    }

    private boolean sendHttpRequest(String urlStr, String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            System.err.println("HTTP transmission failure: " + e.getMessage());
            return false;
        }
    }

    private boolean isIdleFor(int minutes) {
        long idleThreshold = TimeUnit.MINUTES.toMillis(minutes);
        return !context.isTransactionInProgress() && 
               (System.currentTimeMillis() - lastActivityTime.get()) > idleThreshold;
    }

    private boolean isInternetAvailable() {
        try {
            HttpRequest pingRequest = HttpRequest.newBuilder()
                    .uri(URI.create(FIREBASE_BASE_URL + "/.json"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(3))
                    .build();
            
            HttpResponse<Void> response = httpClient.send(pingRequest, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    public void recordActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }
}