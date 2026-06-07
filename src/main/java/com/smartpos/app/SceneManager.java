package com.smartpos.app;

import com.smartpos.model.enums.Role;
import com.smartpos.ui.screen.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Map;

public class SceneManager {
    private final Stage stage;
    private final AppContext context;
    private final Scene scene;
    private final BorderPane root;

    public SceneManager(Stage stage, AppContext context) {
        this.stage = stage;
        this.context = context;
        this.root = new BorderPane();
        this.scene = new Scene(root, 1200, 760);
        
        try {
            // Placeholder for CSS link
        } catch (NullPointerException e) {
            System.err.println("WARNING: App stylesheets framework path '/styles/app.css' missing.");

        }
        
        this.stage.setScene(scene);
    }

    public void show(ScreenType type) {
        // Enforce role authorization
        if (isProtectedScreen(type) && !isAdminAuthorized()) {
            System.out.print("Unauthorized access attempt blocked. Redirecting to Main POS.");
            show(ScreenType.MAIN_POS);
            return;
        }

        // 1. Build the screen content

        Parent screen = switch (type) {
            case REGISTRATION -> new RegistrationScreen(context, this).build();
            case LOGIN -> new LoginScreen(context, this).build();
            case MAIN_POS -> new MainPosScreen(context, this).build();
            case PRODUCTS -> new ProductManagementScreen(context, this).build();
            case REPORTS -> new SalesReportsScreen(context, this).build();
            case SYNC -> new SyncScreen(context, this).build();
            case SETTINGS -> new SettingsScreen(context, this).build();
            case USER_MANAGEMENT -> new UserManagementScreen(context, this).build();
            case CREDIT_MANAGEMENT -> new CreditManagementScreen(context).build();
        };

        // 2. Wrap with AdminShell if applicable
        Parent displayContent = withAdminNav(screen);

        // 3. Update the UI
        root.setCenter(displayContent);

        
        applyTheme();
        stage.show();
    }

    private boolean isProtectedScreen(ScreenType type) {
        return switch (type) {
            case PRODUCTS, REPORTS, SYNC, SETTINGS, USER_MANAGEMENT, CREDIT_MANAGEMENT -> true;

            default -> false;
        };
    }


    private boolean isAdminAuthorized() {
        return context.currentUser() != null && context.currentUser().role() == Role.ADMIN;
    }

    public Parent withAdminNav(Parent content) {

        if (!isAdminAuthorized()) {
            return content;
        }
        return new AdminShell(context, this, content).build();
    }

    public void applyTheme() {
        if (context == null || context.settingsService() == null) {
            root.setStyle("-fx-background-color: #f4f4f4;");
            return;
        }

        Map<String, String> settings = context.settingsService().getAll();
        String theme = settings.getOrDefault("theme", "light");
        String bg = settings.getOrDefault("backgroundColor", "#f4f4f4");
        
        root.setStyle("-fx-background-color: " + bg + ";");
        
        if ("dark".equalsIgnoreCase(theme)) {
            root.getStyleClass().remove("light-theme");
            if (!root.getStyleClass().contains("dark-theme")) {
                root.getStyleClass().add("dark-theme");
            }
        } else {
            root.getStyleClass().remove("dark-theme");
            if (!root.getStyleClass().contains("light-theme")) {
                root.getStyleClass().add("light-theme");
            }
        }
    }
}