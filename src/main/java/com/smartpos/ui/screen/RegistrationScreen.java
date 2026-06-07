package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.app.ScreenType;
import com.smartpos.util.LicenseManager;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class RegistrationScreen {
    private final AppContext context;
    private final SceneManager sceneManager;

    public RegistrationScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox formContainer = new VBox(16);
        formContainer.setAlignment(Pos.CENTER_LEFT);
        formContainer.setMaxWidth(450);
        formContainer.setMaxHeight(Region.USE_PREF_SIZE);
        formContainer.setPadding(new Insets(40));
        formContainer.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-radius: 10; -fx-background-radius: 10;");

        Label title = new Label("SmartPOS System Activation");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");
        formContainer.setAlignment(Pos.CENTER);

        // --- SHOP NAME TRACK (New Line) ---
        Label shopLabel = new Label("Shop Name:");
        shopLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");
        TextField shopNameField = new TextField();
        shopNameField.setPromptText("Enter your shop name...");
        shopNameField.setMaxWidth(Double.MAX_VALUE);

        // --- DEVICE ID TRACK ---
        Label uuidLabel = new Label("Hardware Device ID:");
        uuidLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");
        
        TextField uuidField = new TextField(LicenseManager.getMachineUUID());
        uuidField.setEditable(false);
        HBox.setHgrow(uuidField, Priority.ALWAYS);
        
        Button copyBtn = new Button("Copy ID");
        copyBtn.setPrefWidth(90);
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(uuidField.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("Copied!");
            copyBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(event -> {
                copyBtn.setText("Copy ID");
                copyBtn.setStyle("");
            });
            delay.play();
        });
        
        HBox uuidRow = new HBox(10, uuidField, copyBtn);

        // --- ACTIVATION KEY TRACK ---
        Label keyLabel = new Label("Activation License Key:");
        keyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");

        TextField keyInput = new TextField();
        keyInput.setPromptText("Enter permanent license key...");
        keyInput.setMaxWidth(Double.MAX_VALUE);
        
        Label feedback = new Label();
        feedback.setStyle("-fx-font-weight: bold;");
        feedback.setWrapText(true);
        
        Button activateBtn = new Button("VERIFY & ACTIVATE MACHINE");
        activateBtn.setMaxWidth(Double.MAX_VALUE);
        activateBtn.setPrefHeight(40);
        activateBtn.getStyleClass().add("button-primary");
        activateBtn.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // Logic
        Runnable attemptActivation = () -> {
            String shopName = shopNameField.getText().trim();
            String key = keyInput.getText().trim();

            if (shopName.isEmpty() || key.isEmpty()) {
                feedback.setText("Validation Failure: All fields are required.\nCall 0726993378");
                feedback.setTextFill(Color.RED);
                return;
            }

            if (LicenseManager.isMasterKey(key)) {
                sceneManager.show(ScreenType.LOGIN);
            } else if (LicenseManager.isValidKey(key)) {
                // Assuming LicenseManager.saveLicenseKey(key, shopName) is updated
                if (LicenseManager.saveLicenseKey(key, shopName)) {
                    sceneManager.show(ScreenType.LOGIN);
                } else {
                    feedback.setText("Storage Failure: Could not write data to license file.\nCall 0726993378");
                    feedback.setTextFill(Color.RED);
                }
            } else {
                feedback.setText("Activation Rejected: Invalid key.\nCall 0726993378");
                feedback.setTextFill(Color.RED);
            }
        };

        activateBtn.setOnAction(e -> attemptActivation.run());

        formContainer.getChildren().addAll(
            title, new Separator(),
            shopLabel, shopNameField, 
            uuidLabel, uuidRow, 
            keyLabel, keyInput, 
            feedback, activateBtn
        );
        
        StackPane wrapper = new StackPane(formContainer);
        wrapper.setStyle("-fx-background-color: #f5f6fa;");
        root.setCenter(wrapper);
        
        return root;
    }
}