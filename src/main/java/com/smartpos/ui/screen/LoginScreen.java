package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.app.ScreenType;
import com.smartpos.model.User;
import com.smartpos.model.enums.Role;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginScreen {
    private final AppContext context;
    private final SceneManager sceneManager;

    public LoginScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    /**
     * Builds the standalone application authentication screen.
     */
    public Parent build() {
        VBox root = new VBox(14); // Slightly expanded gap spacing for a cleaner visual layout
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(false);

        Label title = new Label("SmartPOS Workspace");
        title.getStyleClass().add("header");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Standardized input control boundaries
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(240);
        usernameField.setMaxWidth(240);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(240);
        passwordField.setMaxWidth(240);

        Button loginButton = new Button("Sign In");
        loginButton.setPrefWidth(240);
        
        Label hintLabel = new Label("Do not have an account?\n call 0726993378 ");
        hintLabel.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px; -fx-text-alignment: center;");
        
        Label feedbackLabel = new Label();
        feedbackLabel.getStyleClass().add("error");
        feedbackLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-weight: bold;");

        // Form Submission Logic Action Wrapper
        Runnable attemptLogin = () -> {
            String rawUser = usernameField.getText();
            String rawPass = passwordField.getText();

            // Defensive boundary check before hitting SQLite
            if (rawUser == null || rawUser.isBlank() || rawPass == null || rawPass.isBlank()) {
                feedbackLabel.setText("Please enter both username and password.");
                return;
            }

            // Case-insensitivity logic is handled natively in the AuthService update we made earlier
            User user = context.authService().login(rawUser, rawPass).orElse(null);
            
            if (user == null) {
                feedbackLabel.setText("Access Denied: Invalid credentials.");
                return;
            }

            // Bind current operational context parameters
            context.setCurrentUser(user);
            context.activityLogService().log(user.id(), "User authentication session started.");

            // Routing validation
            if (user.role() == Role.USER) {
                sceneManager.show(ScreenType.MAIN_POS);
            } else {
                // Administrators default straight into management operations
                sceneManager.show(ScreenType.PRODUCTS);
            }
        };

        // UI Accessibility Optimization: Bind the submission action to mouse clicks AND keyboard hits
        loginButton.setOnAction(e -> attemptLogin.run());
        usernameField.setOnAction(e -> attemptLogin.run());
        passwordField.setOnAction(e -> attemptLogin.run());

        root.getChildren().addAll(title, usernameField, passwordField, loginButton, hintLabel, feedbackLabel);
        return root;
    }
}