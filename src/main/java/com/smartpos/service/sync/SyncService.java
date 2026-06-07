package com.smartpos.service.sync;

public interface SyncService {
    String getStatus();
    String syncNow();
    String getLastSyncTime(); // Add this line
}