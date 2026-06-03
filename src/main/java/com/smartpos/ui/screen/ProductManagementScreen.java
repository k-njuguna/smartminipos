package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.model.Product;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Optional;

public class ProductManagementScreen {
    private final AppContext context;
    private final SceneManager sceneManager;
    
    // Core data lists
    private final ObservableList<Product> masterData = FXCollections.observableArrayList();
    private FilteredList<Product> filteredData; // Made class-level to preserve state across refreshes
    
    private final TableView<Product> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ProductForm form = new ProductForm();

    public ProductManagementScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    /**
     * Builds the persistent desktop inventory management control screen.
     */
    public Parent build() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        HBox actionBar = new HBox(15);
        actionBar.setPadding(new Insets(0, 0, 15, 0));
        actionBar.setAlignment(Pos.CENTER_LEFT);
        
        searchField.setPromptText("Search products by name...");
        searchField.setPrefWidth(300);
        
        Button btnNew = new Button("+ New Product");
        btnNew.setOnAction(e -> {
            table.getSelectionModel().clearSelection();
            form.loadProduct(null); // Force form reset
        });

        actionBar.getChildren().addAll(new Label("Inventory Lookup:"), searchField, new Pane(), btnNew);
        HBox.setHgrow(actionBar.getChildren().get(2), Priority.ALWAYS);

        setupTable();
        
        // Bind the FilteredList layer to masterData
        filteredData = new FilteredList<>(masterData, p -> true);
        
        // Live search text listener logic (Filtered completely for Name strings)
        searchField.textProperty().addListener((obs, old, val) -> {
            filteredData.setPredicate(p -> {
                if (val == null || val.isBlank()) return true;
                String f = val.toLowerCase().trim();
                
                return p.getName() != null && p.getName().toLowerCase().contains(f);
            });
        });
        
        // Table views the filtered proxy data stream instead of raw masterData array
        table.setItems(filteredData);

        VBox sidePanel = form.getLayout();
        sidePanel.setPrefWidth(350);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, p) -> form.loadProduct(p));

        root.setTop(actionBar);
        root.setCenter(table);
        root.setRight(sidePanel);

        refresh(); // Hydrate data from sqlite database service
        return sceneManager.withAdminNav(root);
    }

    private void setupTable() {
        TableColumn<Product, String> cName = new TableColumn<>("Product Name");
        cName.setCellValueFactory(v -> v.getValue().nameProperty());
        
        TableColumn<Product, Number> cPrice = new TableColumn<>("Unit Price");
        cPrice.setCellValueFactory(v -> v.getValue().priceProperty());
        cPrice.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty ? null : String.format("KES %.2f", price.doubleValue()));
            }
        });

        TableColumn<Product, Number> cStock = new TableColumn<>("Current Stock");
        cStock.setCellValueFactory(v -> v.getValue().stockProperty());
        cStock.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(stock.toString());
                    
                    // Resolve dynamic low stock coloring using row indices safely
                    Product rowProduct = null;
                    if (getTableView() != null && getIndex() < getTableView().getItems().size()) {
                        rowProduct = getTableView().getItems().get(getIndex());
                    }
                    
                    long alertThreshold;
                    if (rowProduct != null && rowProduct.getLowStockThreshold() != null && rowProduct.getLowStockThreshold() > 0) {
                        alertThreshold = rowProduct.getLowStockThreshold();
                    } else {
                        try {
                            String globalSetting = context.settingsService().getAll().getOrDefault("lowStockThreshold", "5");
                            alertThreshold = Long.parseLong(globalSetting);
                        } catch (NumberFormatException e) {
                            alertThreshold = 5;
                        }
                    }

                    if (stock.intValue() <= alertThreshold) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setTextFill(Color.BLACK);
                        setStyle("");
                    }
                }
            }
        });

        table.getColumns().addAll(cName, cPrice, cStock);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    public void refresh() {
        // Keeps user's active search filter intact while pulling the fresh list modifications from database service layer
        masterData.setAll(context.productService().findAll());
    }

    /**
     * Inner form controller module managing product detail updates.
     */
    private class ProductForm {
        private final TextField name = new TextField();
        private final TextField price = new TextField();
        private final TextField stock = new TextField();
        private final TextField customThreshold = new TextField();
        private final Label feedback = new Label();
        private final Button deleteBtn = new Button("Delete Product");
        private Product currentProduct;

        public VBox getLayout() {
            VBox layout = new VBox(10);
            layout.setPadding(new Insets(0, 0, 0, 15));
            
            customThreshold.setPromptText("Leave blank to use global default");

            Button saveBtn = new Button("Save Product Entry");
            saveBtn.setMaxWidth(Double.MAX_VALUE);
            saveBtn.getStyleClass().add("button-primary");
            saveBtn.setOnAction(e -> handleSave());

            deleteBtn.setMaxWidth(Double.MAX_VALUE);
            deleteBtn.setStyle("-fx-text-fill: white; -fx-background-color: #c0392b; -fx-font-weight: bold;");
            deleteBtn.setVisible(false);
            deleteBtn.setOnAction(e -> handleDelete());

            layout.getChildren().addAll(
                new Label("Product Editor Profile"), new Separator(),
                new Label("Product Name"), name,
                new Label("Unit Selling Price"), price,
                new Label("Current Stock Inventory"), stock,
                new Label("Custom Low Stock Warning Threshold (Optional)"), customThreshold,
                new Separator(),
                saveBtn, deleteBtn, feedback
            );
            return layout;
        }

        public void loadProduct(Product p) {
            this.currentProduct = p;
            feedback.setText(""); // Reset old diagnostic strings
            
            if (p == null) {
                name.clear(); price.clear(); stock.clear(); customThreshold.clear();
                deleteBtn.setVisible(false);
                feedback.setText("Status Mode: Create New Product");
                feedback.setTextFill(Color.GRAY);
                name.requestFocus(); 
            } else {
                name.setText(p.getName());
                price.setText(String.valueOf(p.getPrice()));
                stock.setText(String.valueOf(p.getStock()));
                
                if (p.getLowStockThreshold() != null && p.getLowStockThreshold() > 0) {
                    customThreshold.setText(String.valueOf(p.getLowStockThreshold()));
                } else {
                    customThreshold.clear();
                }
                
                deleteBtn.setVisible(true);
                feedback.setText("Status Mode: Modifying Product ID #" + p.getId());
                feedback.setTextFill(Color.BLUE);
            }
        }

        private void handleSave() {
            try {
                String pName = name.getText().trim();
                if (pName.isEmpty()) {
                    feedback.setText("Validation Failure: Product Name is required.");
                    feedback.setTextFill(Color.RED);
                    return;
                }
                
                double pPrice = Double.parseDouble(price.getText().trim());
                long pStock = Long.parseLong(stock.getText().trim());
                
                if (pPrice < 0 || pStock < 0) {
                    feedback.setText("Validation Failure: Prices and stock levels cannot be negative values.");
                    feedback.setTextFill(Color.RED);
                    return;
                }
                
                Long pThreshold = null;
                String threshText = customThreshold.getText().trim();
                if (!threshText.isEmpty()) {
                    pThreshold = Long.parseLong(threshText);
                }

                long userId = context.currentUser().id();

                if (currentProduct == null) {
                    // Database schema modification clean verification pass
                    context.productService().createProduct(pName, pPrice, pStock, pThreshold, userId);
                    feedback.setText("Product Created Successfully!");
                    feedback.setTextFill(Color.GREEN);
                    loadProduct(null); // Prepare for next rapid execution entry
                } else {
                    context.productService().updateProduct(currentProduct.getId(), pName, pPrice, pStock, pThreshold, userId);
                    feedback.setText("Product Details Updated Successfully!");
                    feedback.setTextFill(Color.GREEN);
                }
                refresh();
            } catch (NumberFormatException nfe) {
                feedback.setText("Validation Failure: Ensure price, stock, and threshold are numeric format strings.");
                feedback.setTextFill(Color.RED);
            } catch (Exception ex) {
                feedback.setText("Error Encountered: " + ex.getMessage());
                feedback.setTextFill(Color.RED);
            }
        }

        private void handleDelete() {
            if (currentProduct == null) return;

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Deletion");
            alert.setHeaderText("Permanently remove '" + currentProduct.getName() + "'?");
            alert.setContentText("This action changes storage parameters and cannot be undone.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    context.productService().deleteProduct(
                        currentProduct.getId(), 
                        currentProduct.getName(), 
                        context.currentUser().id()
                    );
                    table.getSelectionModel().clearSelection();
                    refresh();
                    loadProduct(null); // Fallback cleanly straight back to initialization mode
                    feedback.setText("System Notification: Product record deleted completely.");
                    feedback.setTextFill(Color.RED);
                } catch (Exception ex) {
                    feedback.setText("Deletion Aborted: " + ex.getMessage());
                    feedback.setTextFill(Color.RED);
                }
            }
        }
    }
}