package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.app.ScreenType;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The persistent application shell providing navigation and structure.
 */
public class AdminShell {
    private final AppContext context;
    private final SceneManager sceneManager;
    private final Parent content;

    public AdminShell(AppContext context, SceneManager sceneManager, Parent content) {
        this.context = context;
        this.sceneManager = sceneManager;
        this.content = content;
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.setCenter(content);

        // Navigation Bar Container
        HBox nav = new HBox(12);
        nav.setPadding(new Insets(12, 16, 12, 16));
        nav.getStyleClass().add("panel");
        nav.setStyle("-fx-alignment: CENTER_LEFT; -fx-background-color: #f4f4f4;");

        // Application Title
        Label title = new Label("SmartPOS Admin");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0 12 0 0;");

        // Navigation Buttons
        Button pos = createNavButton("Main POS", ScreenType.MAIN_POS);
        Button products = createNavButton("Products", ScreenType.PRODUCTS);
        Button reports = createNavButton("Sales Reports", ScreenType.REPORTS);
        Button credits = createNavButton("Credits", ScreenType.CREDIT_MANAGEMENT);
        Button sync = createNavButton("Cloud Sync", ScreenType.SYNC);
        Button settings = createNavButton("Settings", ScreenType.SETTINGS);
        Button users = createNavButton("User Management", ScreenType.USER_MANAGEMENT);

        // Spacer to push Logout to the far right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Logout Button
        Button logout = new Button("Logout");
        logout.getStyleClass().add("button-danger");
        logout.setOnAction(e -> {
            context.logout();
            sceneManager.show(ScreenType.LOGIN);
        });

       nav.getChildren().addAll(title, pos, products, reports, credits, sync, settings, users, spacer, logout);
        
        root.setTop(nav);
        return root;
    }

    /**
     * Helper to create navigation buttons consistently
     */
    private Button createNavButton(String text, ScreenType type) {
        Button btn = new Button(text);
        btn.setOnAction(e -> sceneManager.show(type));
        return btn;
    }
}