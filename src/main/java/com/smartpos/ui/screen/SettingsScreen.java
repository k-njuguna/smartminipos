package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.model.Product;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.StringConverter;

import java.io.File;
import java.util.List;
import java.util.Map;

public class SettingsScreen {
    private final AppContext context;
    private final SceneManager sceneManager;

    public SettingsScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    public Parent build() {
        Map<String, String> settings = context.settingsService().getAll();
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #f9f9f9;");

        Label mainTitle = new Label("System Settings");
        mainTitle.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #333;");

        HBox columnsContainer = new HBox(20);
        columnsContainer.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(columnsContainer, Priority.ALWAYS);

        // ======= COLUMN 1: Theme & Visuals =======
        VBox col1 = createStyledColumn("Theme & Visuals");
        
        ComboBox<String> themeDropdown = new ComboBox<>();
        themeDropdown.getItems().addAll("Light", "Dark");
        themeDropdown.setValue(context.getActiveTerminalTheme());
        themeDropdown.setMaxWidth(Double.MAX_VALUE);
        
        themeDropdown.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                context.setActiveTerminalTheme(newVal);
                sceneManager.applyTheme();
            }
        });

        ComboBox<String> layoutDropdown = new ComboBox<>();
        layoutDropdown.getItems().addAll("Standard", "Compact Grid", "Touch-Optimized Wide");
        layoutDropdown.setValue(context.getSelectedLayoutMode());
        layoutDropdown.setMaxWidth(Double.MAX_VALUE);
        
        layoutDropdown.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                context.setSelectedLayoutMode(newVal);
            }
        });

        ColorPicker bgColorPicker = new ColorPicker(parseHex(settings.getOrDefault("backgroundColor", "#f4f4f4")));
        bgColorPicker.setMaxWidth(Double.MAX_VALUE);

        Button saveVisuals = new Button("Apply Visual Canvas Color");
        saveVisuals.setMaxWidth(Double.MAX_VALUE);
        saveVisuals.getStyleClass().add("button-primary");
        saveVisuals.setOnAction(e -> {
            context.settingsService().set("backgroundColor", toHex(bgColorPicker.getValue()));
            sceneManager.applyTheme();
        });

        col1.getChildren().addAll(
            new Label("Terminal Theme Style"), themeDropdown, 
            new Label("Point-of-Sale UI Layout Mode"), layoutDropdown,
            new Label("Canvas Background Color"), bgColorPicker, 
            saveVisuals
        );

        // ======= COLUMN 2: Backup & Disaster Recovery =======
        VBox col2 = createStyledColumn("Backup & Data");
        
        Button createBtn = new Button("Create Backup Archive");
        Button restoreBtn = new Button("Restore Backup Archive");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        restoreBtn.setMaxWidth(Double.MAX_VALUE);
        
        Label backupLog = new Label("System Data Engine Status: Ready");
        backupLog.setWrapText(true);
        backupLog.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        createBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Local Backup Destination Folder");
            File folder = dc.showDialog(null);
            if (folder != null) {
                try {
                    var path = context.backupService().createBackup(folder);
                    backupLog.setText("Archive Successful: " + path.getFileName());
                    backupLog.setStyle("-fx-font-size: 11px; -fx-text-fill: green;");
                } catch (Exception ex) {
                    backupLog.setText("Backup Aborted: " + ex.getMessage());
                    backupLog.setStyle("-fx-font-size: 11px; -fx-text-fill: red;");
                }
            }
        });

        restoreBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Local SmartPOS Database File (*.db)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database Files", "*.db"));
            File dbFile = fc.showOpenDialog(null);
            if (dbFile != null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Are you sure you want to restore this database? The current application instance will close down completely to apply changes.", ButtonType.YES, ButtonType.NO);
                alert.setTitle("Confirm Hot-Swap File Restoration");
                if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                    try {
                        context.backupService().restoreBackup(dbFile);
                        System.exit(0);
                    } catch (Exception ex) {
                        backupLog.setText("Restoration Failed: " + ex.getMessage());
                        backupLog.setStyle("-fx-font-size: 11px; -fx-text-fill: red;");
                    }
                }
            }
        });

        col2.getChildren().addAll(new Label("Database Snapshot Management"), createBtn, 
                new Separator(), new Label("Database Disaster Recovery"), restoreBtn, backupLog);

        // ======= COLUMN 3: POS Configuration =======
        VBox col3 = createStyledColumn("POS Configuration");
        
        VBox buttonsBox = new VBox(5);
        List<Product> products = context.productService().findAll();
        
        StringConverter<Product> productConverter = new StringConverter<>() {
            @Override public String toString(Product p) { return p == null ? "" : p.getName(); }
            @Override public Product fromString(String string) { return null; }
        };

        for (int i = 0; i < 6; i++) {
            int idx = i;
            ComboBox<Product> picker = new ComboBox<>();
            picker.setConverter(productConverter);
            picker.getItems().addAll(products);
            picker.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(picker, Priority.ALWAYS);

            // FIX: Match the exact key schema used by MainPosScreen ("quickBtnName" + i)
            String savedNameStr = settings.getOrDefault("quickBtnName" + i, "");
            if (!savedNameStr.isEmpty()) {
                products.stream()
                        .filter(p -> p.getName().equalsIgnoreCase(savedNameStr))
                        .findFirst()
                        .ifPresent(picker::setValue);
            }

            // FIX: Save using the product's name instead of ID string
            picker.valueProperty().addListener((o, old, newVal) -> 
                context.settingsService().set("quickBtnName" + idx, newVal != null ? newVal.getName() : ""));

            Button clear = new Button("X");
            clear.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
            clear.setOnAction(e -> picker.setValue(null));
            
            HBox row = new HBox(5, new Label("Button " + (i + 1) + ":"), picker, clear);
            row.setAlignment(Pos.CENTER_LEFT);
            buttonsBox.getChildren().add(row);
        }

        Label lowStockHeading = new Label("Global Default Stock Alert");
        lowStockHeading.setStyle("-fx-font-weight: bold;");
        
        TextField thresholdField = new TextField(settings.getOrDefault("lowStockThreshold", "5"));
        thresholdField.setPromptText("System fallback threshold (e.g., 5)");
        
        Label thresholdFeedback = new Label();
        thresholdFeedback.setStyle("-fx-font-size: 11px;");
        
        Button saveThreshold = new Button("Save Global Default");
        saveThreshold.setMaxWidth(Double.MAX_VALUE);
        saveThreshold.setOnAction(e -> {
            String cleanValue = thresholdField.getText().trim();
            if (cleanValue.isEmpty()) {
                context.settingsService().set("lowStockThreshold", "5");
                thresholdField.setText("5");
                thresholdFeedback.setText("Reset to default baseline fallback value (5).");
                thresholdFeedback.setTextFill(Color.BLUE);
                return;
            }
            try {
                long val = Long.parseLong(cleanValue);
                if (val < 0) {
                    thresholdFeedback.setText("Validation Failure: Threshold levels cannot be negative.");
                    thresholdFeedback.setTextFill(Color.RED);
                    return;
                }
                context.settingsService().set("lowStockThreshold", String.valueOf(val));
                thresholdFeedback.setText("Global metric successfully persisted.");
                thresholdFeedback.setTextFill(Color.GREEN);
            } catch (NumberFormatException nfe) {
                thresholdFeedback.setText("Validation Failure: Enter a valid sequence number.");
                thresholdFeedback.setTextFill(Color.RED);
            }
        });

        col3.getChildren().addAll(
            new Label("Register Quick-Access Hotkeys"), buttonsBox, 
            new Separator(), 
            lowStockHeading, thresholdField, saveThreshold, thresholdFeedback
        );

        columnsContainer.getChildren().addAll(col1, col2, col3);
        root.getChildren().addAll(mainTitle, columnsContainer);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        return sceneManager.withAdminNav(sp);
    }

    private VBox createStyledColumn(String title) {
        VBox col = new VBox(15);
        col.setPadding(new Insets(15));
        col.setMinWidth(300);
        col.setPrefWidth(350);
        col.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        Label l = new Label(title);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #0056b3;");
        col.getChildren().add(l);
        
        HBox.setHgrow(col, Priority.ALWAYS);
        return col;
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));
    }

    private Color parseHex(String hex) {
        try { return Color.web(hex); } catch (Exception e) { return Color.web("#f4f4f4"); }
    }
}