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

public class AdminShell {
    private final AppContext context;
    private final SceneManager sceneManager;
    private final Parent content;

    public AdminShell(AppContext context, SceneManager sceneManager, Parent content) {
        this.context = context;
        this.sceneManager = sceneManager;
        this.content = content;
    }

    /**
     * Builds the persistent structural admin application shell frame containing navigation bars.
     */
    public Parent build() {
        BorderPane root = new BorderPane();
        root.setCenter(content);

        HBox nav = new HBox(12); // Slightly increased gap spacing for premium look
        nav.setPadding(new Insets(12, 16, 12, 16));
        nav.getStyleClass().add("panel");
        nav.setStyle("-fx-alignment: CENTER_LEFT;"); // Aligns components cleanly down the middle horizontal line

        Label title = new Label("SmartPOS Admin");
        title.getStyleClass().add("header");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0 12 0 0;");

        // Set up individual viewport action buttons
        Button pos = new Button("Main POS");
        pos.setOnAction(e -> sceneManager.show(ScreenType.MAIN_POS));
        
        Button products = new Button("Products");
        products.setOnAction(e -> sceneManager.show(ScreenType.PRODUCTS));
        
        Button reports = new Button("Sales Reports");
        reports.setOnAction(e -> sceneManager.show(ScreenType.REPORTS));
        
        Button sync = new Button("Cloud Sync");
        sync.setOnAction(e -> sceneManager.show(ScreenType.SYNC));
        
        Button settings = new Button("Settings");
        settings.setOnAction(e -> sceneManager.show(ScreenType.SETTINGS));
        
        Button users = new Button("User Management");
        users.setOnAction(e -> sceneManager.show(ScreenType.USER_MANAGEMENT));

        // UI Enhancement: Invisible expanding region component spacer
        // Pushes operational management tools left, and isolates the high-risk logout button safely to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logout = new Button("Logout");
        logout.getStyleClass().add("button-danger"); // Hook for custom red styling overrides
        logout.setOnAction(e -> {
            context.logout();
            sceneManager.show(ScreenType.LOGIN);
        });

        // FIXED: Re-inserted the missing 'sync' control node and populated the 'spacer' element
        nav.getChildren().addAll(title, pos, products, reports, sync, settings, users, spacer, logout);
        
        root.setTop(nav);
        return root;
    }
}