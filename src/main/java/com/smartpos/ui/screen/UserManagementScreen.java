package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.model.User;
import com.smartpos.model.enums.Role;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.util.List;

public class UserManagementScreen {
    private final AppContext context;
    private final SceneManager sceneManager;

    public UserManagementScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    public Parent build() {
        VBox root = new VBox(25);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #f4f4f4;");

        Label title = new Label("User Management Console");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        Label userFeedback = new Label();
        userFeedback.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        HBox columnsContainer = new HBox(20);
        columnsContainer.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(columnsContainer, Priority.ALWAYS);

        StringConverter<User> userConverter = new StringConverter<>() {
            @Override public String toString(User u) { return (u == null) ? "" : u.username() + " (Role: " + u.role() + ")"; }
            @Override public User fromString(String s) { return null; }
        };

        // --- COLUMN 1: NEW USER ---
        VBox newUserPane = createStyledColumn("Add New User");
        TextField newUsername = new TextField();
        newUsername.setPromptText("Username");
        PasswordField newUserPassword = new PasswordField();
        newUserPassword.setPromptText("Password");
        ComboBox<Role> newUserRole = new ComboBox<>();
        newUserRole.getItems().addAll(Role.ADMIN, Role.USER);
        newUserRole.setValue(Role.USER);
        newUserRole.setMaxWidth(Double.MAX_VALUE);
        Button createUserBtn = new Button("Create Account");
        createUserBtn.setMaxWidth(Double.MAX_VALUE);
        newUserPane.getChildren().addAll(new Label("Account Details"), newUsername, newUserPassword, new Label("Role"), newUserRole, createUserBtn);

        // --- COLUMN 2: PASSWORD UPDATE (Isolated Selector) ---
        VBox pswdPane = createStyledColumn("Update Password");
        ComboBox<User> pswdUserList = new ComboBox<>();
        pswdUserList.setConverter(userConverter);
        pswdUserList.setMaxWidth(Double.MAX_VALUE);
        PasswordField resetPassword = new PasswordField();
        resetPassword.setPromptText("New password");
        Button changePasswordBtn = new Button("Update Password");
        changePasswordBtn.setMaxWidth(Double.MAX_VALUE);
        pswdPane.getChildren().addAll(new Label("Select User"), pswdUserList, new Separator(), new Label("New Key"), resetPassword, changePasswordBtn);

        // --- COLUMN 3: DANGER ZONE (Self-contained deletion) ---
        VBox deleteUserPane = createStyledColumn("Danger Zone");
        deleteUserPane.setStyle(deleteUserPane.getStyle() + "-fx-border-color: #e74c3c;");
        ComboBox<User> deleteUserList = new ComboBox<>();
        deleteUserList.setConverter(userConverter);
        deleteUserList.setMaxWidth(Double.MAX_VALUE);
        Button deleteUserBtn = new Button("Delete Selected User");
        deleteUserBtn.setMaxWidth(Double.MAX_VALUE);
        deleteUserBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        deleteUserPane.getChildren().addAll(new Label("Select User to Purge"), deleteUserList, new Separator(), deleteUserBtn);

        // Initial Data Populate
        refreshDropdowns(pswdUserList, deleteUserList);

        // Event Listeners
        createUserBtn.setOnAction(e -> {
            try {
                context.authService().createUser(newUsername.getText(), newUserPassword.getText(), newUserRole.getValue());
                refreshDropdowns(pswdUserList, deleteUserList);
                userFeedback.setText("User created successfully.");
                userFeedback.setTextFill(Color.GREEN);
            } catch (Exception ex) { userFeedback.setText(ex.getMessage()); userFeedback.setTextFill(Color.RED); }
        });

        changePasswordBtn.setOnAction(e -> {
            User target = pswdUserList.getValue();
            if (target == null) { userFeedback.setText("Select a user to update."); userFeedback.setTextFill(Color.ORANGE); return; }
            try {
                context.authService().updatePassword(target.id(), resetPassword.getText());
                userFeedback.setText("Password updated for " + target.username());
                userFeedback.setTextFill(Color.GREEN);
            } catch (Exception ex) { userFeedback.setText(ex.getMessage()); userFeedback.setTextFill(Color.RED); }
        });

        deleteUserBtn.setOnAction(e -> {
            User target = deleteUserList.getValue();
            if (target == null) { userFeedback.setText("Select a user to delete."); userFeedback.setTextFill(Color.ORANGE); return; }
            if (target.id() == context.currentUser().id()) { userFeedback.setText("Cannot delete self."); userFeedback.setTextFill(Color.RED); return; }
            
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Purge " + target.username() + "?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                try {
                    context.authService().deleteUser(target.id(),context.currentUser().id());
                    refreshDropdowns(pswdUserList, deleteUserList);
                    userFeedback.setText("User permanently purged.");
                    userFeedback.setTextFill(Color.BLUE);
                } catch (Exception ex) { userFeedback.setText(ex.getMessage()); userFeedback.setTextFill(Color.RED); }
            }
        });

        columnsContainer.getChildren().addAll(newUserPane, pswdPane, deleteUserPane);
        root.getChildren().addAll(title, columnsContainer, userFeedback);
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        return sceneManager.withAdminNav(sp);
    }

    private void refreshDropdowns(ComboBox<User> p1, ComboBox<User> p2) {
        List<User> users = context.authService().findAllUsers();
        p1.getItems().setAll(users);
        p2.getItems().setAll(users);
    }

    private VBox createStyledColumn(String title) {
        VBox v = new VBox(15);
        v.setPadding(new Insets(20));
        v.setMinWidth(320);
        v.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label l = new Label(title);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        v.getChildren().add(l);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }
}