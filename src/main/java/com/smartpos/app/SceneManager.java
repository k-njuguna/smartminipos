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
        
        // Ensure the CSS styles resource file is linked properly from the classpath layout
        try {
            //String css = getClass().getResource("/styles/app.css").toExternalForm();
            //this.scene.getStylesheets().add(css);
        } catch (NullPointerException e) {
            System.err.println("WARNING: App stylesheets framework path '/styles/app.css' missing from resources folder.");
        }
        
        this.stage.setScene(scene);
    }

    public void show(ScreenType type) {
        // Enforce role authorization up front before instantiating or building UI components
        if (isProtectedScreen(type) && !isAdminAuthorized()) {
            System.out.print("Unauthorized access attempt blocked. Redirecting to Main POS layout view.");
            show(ScreenType.MAIN_POS); // Safe iterative redirect break loop
            return;
        }

        Parent screen = switch (type) {
            case REGISTRATION -> new RegistrationScreen(context, this).build();
            case LOGIN -> new LoginScreen(context, this).build();
            case MAIN_POS -> new MainPosScreen(context, this).build();
            case PRODUCTS -> new ProductManagementScreen(context, this).build();
            case REPORTS -> new SalesReportsScreen(context, this).build();
            case SYNC -> new SyncScreen(context, this).build();
            case SETTINGS -> new SettingsScreen(context, this).build();
            case USER_MANAGEMENT -> new UserManagementScreen(context, this).build();
        };

        // Clear the old view layout completely to release old components from JVM garbage collector
        root.setCenter(null); 
        root.setCenter(screen);
        
        applyTheme();
        stage.show();
    }

    /**
     * Determines whether a specific target window layout type requires strict administrator privileges.
     */
    private boolean isProtectedScreen(ScreenType type) {
        return switch (type) {
            case PRODUCTS, REPORTS, SYNC, SETTINGS, USER_MANAGEMENT -> true;
            default -> false;
        };
    }

    /**
     * Verifies if the actively bound session user holds functional administrative permissions.
     */
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