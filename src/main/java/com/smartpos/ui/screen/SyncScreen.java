package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class SyncScreen {
    private final AppContext context;
    private final SceneManager sceneManager;
    private Timeline uiRefreshTimeline;

    public SyncScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    /**
     * Builds the synchronized data pipeline management panel layout view.
     */
    public Parent build() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.TOP_CENTER);
        root.getStyleClass().add("panel");
        root.setStyle("-fx-background-color: white; -fx-background-radius: 10;");

        // --- Header Structural Segment ---
        Label title = new Label("Cloud Backup & Sync Portal");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1a237e;");

        Label subtitle = new Label("Critical operations ledger data is securely uploaded off-site automatically when the client station drops into idle routines.");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setStyle("-fx-text-fill: #546e7a; -fx-font-size: 13px;");

        // --- Status Monitors Terminal Block ---
        VBox statusBox = new VBox(12);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(20));
        statusBox.setMaxWidth(500);
        statusBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label statusLabel = new Label("Current Node State: " + context.syncService().getStatus());
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        
        Label lastSyncLabel = new Label("Last verified remote snapshot: " + context.syncService().getLastSyncTime());
        lastSyncLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        statusBox.getChildren().addAll(statusLabel, lastSyncLabel);

        // --- Action Operational Console Line ---
        Button syncNowBtn = new Button("FORCE SYNC CONSOLE NOW");
        syncNowBtn.setPrefWidth(240);
        syncNowBtn.setPrefHeight(40);
        syncNowBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 13px;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setMaxSize(28, 28);

        // Spacer to securely maintain control element centering metrics without layout shifts
        Region spacer = new Region();
        spacer.setPrefWidth(28);

        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER);
        actionRow.getChildren().addAll(spacer, syncNowBtn, progress);

        Label resultMsg = new Label("Ready to instantiate structural background link.");
        resultMsg.setWrapText(true);
        resultMsg.setAlignment(Pos.CENTER);
        resultMsg.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        // --- Execution Handler ---
        syncNowBtn.setOnAction(e -> {
            syncNowBtn.setDisable(true);
            progress.setVisible(true);
            spacer.setVisible(false); 
            resultMsg.setText("Establishing secure payload stream with Firebase Cloud...");
            resultMsg.setTextFill(Color.DARKBLUE);

            context.getExecutor().execute(() -> {
                try {
                    String result = context.syncService().syncNow();
                    
                    Platform.runLater(() -> {
                        resultMsg.setText(result);
                        if (result != null && result.toLowerCase().contains("success")) {
                            resultMsg.setTextFill(Color.GREEN);
                        } else {
                            resultMsg.setTextFill(Color.RED);
                        }
                        // Refresh the container instantly upon structural response
                        statusLabel.setText("Current Node State: " + context.syncService().getStatus());
                        lastSyncLabel.setText("Last verified remote snapshot: " + context.syncService().getLastSyncTime());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultMsg.setText("Fatal Pipe Interrupt: " + ex.getMessage());
                        resultMsg.setTextFill(Color.RED);
                    });
                } finally {
                    Platform.runLater(() -> {
                        progress.setVisible(false);
                        spacer.setVisible(true); 
                        syncNowBtn.setDisable(false);
                    });
                }
            });
        });

        root.getChildren().addAll(title, subtitle, statusBox, actionRow, resultMsg);

        // --- Instantiating Automated UI Refresh Engine ---
        setupAutorefreshEngine(statusLabel, lastSyncLabel, syncNowBtn);

        return root;

    }

    /**
     * Polls service conditions seamlessly every 2 seconds to match background worker events.
     */
    private void setupAutorefreshEngine(Label statusLabel, Label lastSyncLabel, Button syncNowBtn) {
        uiRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
            // Prevent polling mutations if a manual trigger lock is currently processing
            if (!syncNowBtn.isDisabled()) {
                String currentStatus = context.syncService().getStatus();
                statusLabel.setText("Current Node State: " + currentStatus);
                lastSyncLabel.setText("Last verified remote snapshot: " + context.syncService().getLastSyncTime());

                // Color code interface states cleanly based on background logic
                if (currentStatus.startsWith("Sync Failed") || currentStatus.equals("Error")) {
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #d32f2f;");
                } else if (currentStatus.equals("Synchronizing...")) {
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #f57c00;");
                } else {
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2e7d32;");
                }
            }
        }));
        uiRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        uiRefreshTimeline.play();
    }
}