package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.app.ScreenType;
import com.smartpos.model.CartItem;
import com.smartpos.model.Product;
import com.smartpos.service.ReportService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class MainPosScreen {
    private final AppContext context;
    private final SceneManager sceneManager;
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final Label totalLabel = new Label("Total: 0.00");
    
    // Simple, non-shouting real-time daily revenue tracking label
    private final Label dailyTotalLabel = new Label("Today's Sales: 0.00");

    private final List<Button> quickButtons = new ArrayList<>();
    private final List<Product> quickProducts = new ArrayList<>();

    public MainPosScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    public Parent build() {
        // Dynamic Reset: Wipe old UI caches before drawing layout panels
        quickButtons.clear();
        quickProducts.clear();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        
        HBox header = new HBox(10);
        VBox leftPane = new VBox(10);
        VBox centerPane = new VBox(2);
        VBox rightPane = new VBox(8);
        HBox footer = new HBox(10);
        HBox mainContent = new HBox(3);
       
        Region spacer1 = new Region();
        Region spacer2 = new Region();
        Region spacer3 = new Region();
        Region spacer4 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox.setHgrow(spacer3, Priority.ALWAYS);
        HBox.setHgrow(spacer4, Priority.ALWAYS);
        HBox.setHgrow(mainContent, Priority.ALWAYS);
        
        header.setPadding(new Insets(10));
        footer.setPadding(new Insets(10));
        rightPane.setPadding(new Insets(10));
        centerPane.setPadding(new Insets(10));
        rightPane.setPrefWidth(320);
        leftPane.setPrefWidth(250);
        centerPane.setPrefWidth(600);

        Label userGreeting = new Label("Welcome: " + context.currentUser().username());
        Label title = new Label("SMART POS MAIN PAGE:\t"+context.getShopName());
        Label feedback = new Label();
        Label lowStockAlert = new Label();
        Label cartLabel = new Label("Items on cart");
        Label leftPaneTitle = new Label("Sell Items using entry");
        Label righttPaneTitle = new Label("Quick Access Favorites");
        
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold;");
        userGreeting.setStyle("-fx-font-size: 30px; -fx-font-weight: bold;");        
        cartLabel.setStyle("-fx-font-size: 20px;");        
        leftPaneTitle.setStyle("-fx-font-size: 20px;");
        righttPaneTitle.setStyle("-fx-font-size: 20px;");
        feedback.getStyleClass().add("error"); 

        // Clean styling rules for your daily sales metric
        dailyTotalLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555555; -fx-font-weight: normal;");

        TableView<CartItem> table = new TableView<>(cart);
        TableColumn<CartItem, String> cName = new TableColumn<>("Product");
        cName.setCellValueFactory(v -> v.getValue().nameProperty());
        TableColumn<CartItem, Number> cQty = new TableColumn<>("Qty");
        cQty.setCellValueFactory(v -> v.getValue().quantityProperty());
        TableColumn<CartItem, Number> cPrice = new TableColumn<>("Price");
        cPrice.setCellValueFactory(v -> v.getValue().unitPriceProperty());
        TableColumn<CartItem, Number> cSub = new TableColumn<>("Subtotal");
        cSub.setCellValueFactory(v -> v.getValue().subtotalProperty());
        table.getColumns().addAll(cName, cQty, cPrice, cSub);

        TextField searchInput = new TextField();
        searchInput.setPromptText("Type name to search product...");
        VBox.setMargin(searchInput, new Insets(18, 0, 4, 0));

        ComboBox<Product> matchedProducts = new ComboBox<>();
        matchedProducts.setPromptText("Select matching item");
        matchedProducts.setMaxWidth(Double.MAX_VALUE);

        TextField qtyInput = new TextField();
        qtyInput.setPromptText("Quantity");

        Button addToCartButton = new Button("Add to Cart");

        ComboBox<String> paymentMethod = new ComboBox<>();
        paymentMethod.getItems().addAll("CASH", "CARD", "MOBILE");
        paymentMethod.setValue("CASH");
        paymentMethod.setMaxWidth(Double.MAX_VALUE);

        // DYNAMIC LOW STOCK CHECK
        long globalThreshold; 
        try { 
            globalThreshold = Long.parseLong(context.settingsService().getAll().getOrDefault("lowStockThreshold", "5")); 
        } catch (Exception ex) { 
            globalThreshold = 5; 
        } 
        
        var lowStockItems = context.productService().findLowStock((int) globalThreshold, 5); 
        StringBuilder sb = new StringBuilder();
        int alertCount = 0;

        for (Product item : lowStockItems) {
            long explicitThreshold = (item.getLowStockThreshold() != null) ? item.getLowStockThreshold() : globalThreshold;
            if (item.getStock() <= explicitThreshold) {
                if (alertCount == 0) {
                    sb.append("Low stock alerts:");
                }
                sb.append("\n- ").append(item.getName()).append(" (stock ").append(item.getStock()).append(")");
                alertCount++;
            }
        }

        if (alertCount == 0) {
            lowStockAlert.setText("Low stock alerts: none");
            lowStockAlert.setStyle("-fx-text-fill: green;");
        } else {
            lowStockAlert.setText(sb.toString());
            lowStockAlert.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
        }

        Button logout = new Button("Logout");    
        Button removeSelected = new Button("Delete Item");
        Button clearCart = new Button("Clear Cart");
        Button sellButton = new Button("SELL");

        sellButton.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;-fx-text-fill: #d32f2f;");
        addToCartButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;-fx-text-fill: #d32f2f;");
        HBox.setHgrow(removeSelected, Priority.ALWAYS);
        HBox.setHgrow(sellButton, Priority.ALWAYS);
        HBox.setHgrow(clearCart, Priority.ALWAYS);

        removeSelected.setMaxWidth(Double.MAX_VALUE);
        clearCart.setMaxWidth(Double.MAX_VALUE);
        sellButton.setMaxWidth(Double.MAX_VALUE);

        removeSelected.setPrefHeight(50);
        clearCart.setPrefHeight(50);
        sellButton.setPrefHeight(50);
        
        removeSelected.prefWidthProperty().bind(footer.widthProperty().multiply(0.2));
        sellButton.prefWidthProperty().bind(footer.widthProperty().multiply(0.6));
        clearCart.prefWidthProperty().bind(footer.widthProperty().multiply(0.2));    

        // RIGHT PANEL GRID SELECTION BUILDER
        rightPane.getChildren().add(righttPaneTitle);

        for (int i = 0; i < 6; i++) {
            Button btn = new Button("Empty Row Slot");
            btn.setPrefSize(100, 80);
            btn.setStyle("-fx-font-size: 16px;");
            btn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            btn.setDisable(true); 

            int index = i;
            btn.setOnAction(e -> {
                if (index < quickProducts.size() && quickProducts.get(index) != null) {
                    Product p = quickProducts.get(index);
                    try {
                        addToCart(p, 1);
                        feedback.setText("Quick added: " + p.getName());
                    } catch (Exception ex) {
                        feedback.setText(ex.getMessage());
                    }
                }
            });

            quickButtons.add(btn);
            quickProducts.add(null);
            rightPane.getChildren().add(btn);
        }

        // Run hydrations smoothly
        loadQuickButtons();
        recalcDailyTotal(); // Hydrate total sales metrics on start

        searchInput.textProperty().addListener((obs, oldText, newText) -> updateProductMatches(searchInput, matchedProducts, feedback));

        addToCartButton.setOnAction(e -> {
            try {
                int qty = Integer.parseInt(qtyInput.getText().trim());
                Product selected = matchedProducts.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    feedback.setText("Select a product from search results");
                    return;
                }
                addToCart(selected, qty);
                feedback.setText("Added " + selected.getName());
                
                searchInput.clear();
                qtyInput.clear();
                matchedProducts.getItems().clear();
                matchedProducts.getSelectionModel().clearSelection();
            } catch (NumberFormatException nfe) {
                feedback.setText("Invalid input: Quantity must be a number.");
            } catch (Exception ex) {
                feedback.setText(ex.getMessage());
            }
        });
        
        logout.setOnAction(e -> {
            context.activityLogService().log(context.currentUser().id(), "Logged out");
            context.logout();
            sceneManager.show(ScreenType.LOGIN);
        });
            
        sellButton.setOnAction(e -> {
            try {
                long saleId = context.saleService().checkout(cart, context.currentUser(), paymentMethod.getValue());
                cart.clear();
                recalcTotal();
                recalcDailyTotal(); // Auto-updates the logged-in user's stream calculation instantly
                feedback.setText("Sale completed successfully: #" + saleId);
            } catch (Exception ex) {
                feedback.setText(ex.getMessage());
            }
        });

        clearCart.setOnAction(e -> {
            cart.clear();
            recalcTotal();
            feedback.setText("Shopping cart emptied.");
        });

        removeSelected.setOnAction(e -> { 
            CartItem selectedItem = table.getSelectionModel().getSelectedItem(); 
            if (selectedItem == null) { 
                feedback.setText("Select a cart item to remove"); 
                return; 
            } 
            cart.remove(selectedItem); 
            recalcTotal(); 
            feedback.setText("Removed " + selectedItem.getName()); 
        }); 

        VBox.setVgrow(table, Priority.ALWAYS);
        
        // TOP RIGHT ACTIONS CONTAINER
        HBox topRightControls = new HBox(15);
        topRightControls.setAlignment(Pos.CENTER_RIGHT);
        topRightControls.getChildren().addAll(dailyTotalLabel, logout);

        header.getChildren().addAll(userGreeting, spacer1, title, spacer2, topRightControls);
        centerPane.getChildren().addAll(cartLabel, table);
        
        leftPane.getChildren().addAll(
            leftPaneTitle, searchInput, matchedProducts, 
            qtyInput, paymentMethod, addToCartButton, totalLabel, feedback, lowStockAlert
        );
        
        footer.getChildren().addAll(removeSelected, spacer3, sellButton, spacer4, clearCart);
        mainContent.getChildren().addAll(leftPane, centerPane, rightPane);

        root.setTop(header);
        root.setCenter(mainContent);
        root.setBottom(footer);
        
        return sceneManager.withAdminNav(root);
    }
   
    private void addToCart(Product product, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0.");
        }
        
        int alreadyInCartQty = 0;
        for (CartItem item : cart) {
            if (item.getProduct().getId() == product.getId()) {
                alreadyInCartQty = item.getQuantity();
                break;
            }
        }

        if ((qty + alreadyInCartQty) > product.getStock()) {
            throw new IllegalArgumentException("Insufficient stock! Available: " + product.getStock());
        }

        for (CartItem item : cart) {
            if (item.getProduct().getId() == product.getId()) {
                item.addQuantity(qty);
                recalcTotal();
                return;
            }
        }
        cart.add(new CartItem(product, qty));
        recalcTotal();
    }

    private void recalcTotal() {
        double total = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
        totalLabel.setText("Total: %.2f".formatted(total));
    }

    /**
     * Recalculates the day's total sales, strictly locked to the logged-in user.
     */
    private void recalcDailyTotal() {
        try {
            long currentUserId = context.currentUser().id();
            var data = context.reportService().getSalesByDate(LocalDate.now().toString()); 
            
            // Filters data to match only lines created by the logged-in user's ID
            double aggregateDayTotal = data.stream()
                                           .filter(line -> {
                                                String tu = line.cashier();
                                                String extractedCashier = (tu != null && tu.contains(" ")) 
                                                        ? tu.split(" ")[1] :
                                                        (tu != null ? tu : "System");
                                                return extractedCashier.equalsIgnoreCase(context.currentUser().username());
                                           })
                                            .mapToDouble(ReportService.SalesLine::total)
                                            .sum();

            dailyTotalLabel.setText("Today's Sales: %.2f".formatted(aggregateDayTotal));
        } catch (Exception ex) {
            dailyTotalLabel.setText("Today's Sales: 0.00");
        }
    }

    private void updateProductMatches(TextField searchInput, ComboBox<Product> matchedProducts, Label feedback) {
        String name = searchInput.getText().trim();

        if (name.isBlank()) {
            matchedProducts.getItems().clear();
            matchedProducts.getSelectionModel().clearSelection();
            feedback.setText("");
            return;
        }

        var matches = context.productService().searchByName(name);
        matchedProducts.getItems().setAll(matches);
        
        if (matches.isEmpty()) {
            matchedProducts.getSelectionModel().clearSelection();
            feedback.setText("No products found for: " + name);
            return;
        }
        
        matchedProducts.getSelectionModel().selectFirst();
        matchedProducts.show(); 
        feedback.setText("Found " + matches.size() + " matching product(s)");
    }

    private void loadQuickButtons() {
        var settings = context.settingsService().getAll();
        for (int i = 0; i < 6; i++) {
            String key = "quickBtnName" + i; 
            String productName = settings.get(key);

            if (productName == null || productName.isBlank()) continue;

            var items = context.productService().searchByName(productName.trim());
            if (!items.isEmpty()) {
                Product targetProduct = items.get(0);
                quickProducts.set(i, targetProduct);
                Button btn = quickButtons.get(i);
                btn.setText(targetProduct.getName());
                btn.setDisable(false);
            }
        }
    }
}