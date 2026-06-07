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
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.util.Pair;

public class MainPosScreen {
    private final AppContext context;
    private final SceneManager sceneManager;
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final Label totalLabel = new Label("Total: 0.00");
    private final Label dailyTotalLabel = new Label("Today's Sales: 0.00");
    private final Label feedback = new Label();
    private final List<Button> quickButtons = new ArrayList<>();
    private final List<Product> quickProducts = new ArrayList<>();

    public MainPosScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    public Parent build() {
        quickButtons.clear();
        quickProducts.clear();

        // Using VBox as root to avoid nesting a BorderPane inside the AdminShell's BorderPane
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        
        HBox header = new HBox(10);
        VBox leftPane = new VBox(10);
        VBox centerPane = new VBox(2);
        VBox rightPane = new VBox(8);
        HBox footer = new HBox(10);
        HBox mainContent = new HBox(3);
        
        Region spacer1 = new Region(); Region spacer2 = new Region();
        Region spacer3 = new Region(); Region spacer4 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox.setHgrow(spacer3, Priority.ALWAYS);
        HBox.setHgrow(spacer4, Priority.ALWAYS);
        HBox.setHgrow(mainContent, Priority.ALWAYS);
        
        header.setPadding(new Insets(10));
        footer.setPadding(new Insets(10));
        rightPane.setPadding(new Insets(10));
        centerPane.setPadding(new Insets(10));
        rightPane.setPrefWidth(320); leftPane.setPrefWidth(250); centerPane.setPrefWidth(600);

        Label userGreeting = new Label("Welcome: " + context.currentUser().username());
        Label title = new Label("SMART POS MAIN PAGE:\t"+context.getShopName());
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

        TextField searchInput = new TextField(); searchInput.setPromptText("Type name to search product...");
        VBox.setMargin(searchInput, new Insets(18, 0, 4, 0));
        ComboBox<Product> matchedProducts = new ComboBox<>();
        matchedProducts.setPromptText("Select matching item");
        matchedProducts.setMaxWidth(Double.MAX_VALUE);
        TextField qtyInput = new TextField(); qtyInput.setPromptText("Quantity");
        Button addToCartButton = new Button("Add to Cart");
        ComboBox<String> paymentMethod = new ComboBox<>();
        paymentMethod.getItems().addAll("CASH", "CARD", "MOBILE");
        paymentMethod.setValue("CASH");
        paymentMethod.setMaxWidth(Double.MAX_VALUE);

        // Low stock alerting
        long globalThreshold = 5; 
        try { globalThreshold = Long.parseLong(context.settingsService().getAll().getOrDefault("lowStockThreshold", "5")); } catch (Exception ignored) {}
        var lowStockItems = context.productService().findLowStock((int) globalThreshold, 5);
        StringBuilder sb = new StringBuilder();
        int alertCount = 0;
        for (Product item : lowStockItems) {
            long explicitThreshold = (item.getLowStockThreshold() != null) ? item.getLowStockThreshold() : globalThreshold;
            if (item.getStock() <= explicitThreshold) {
                if (alertCount++ == 0) sb.append("Low stock alerts:");
                sb.append("\n- ").append(item.getName()).append(" (stock ").append(item.getStock()).append(")");
            }
        }
        lowStockAlert.setText(alertCount == 0 ? "Low stock alerts: none" : sb.toString());
        lowStockAlert.setStyle(alertCount == 0 ? "-fx-text-fill: green;" : "-fx-text-fill: #d32f2f; -fx-font-weight: bold;");

        Button logout = new Button("Logout");
        Button removeSelected = new Button("Delete Item");
        Button clearCart = new Button("Clear Cart");
        Button sellButton = new Button("SELL");
        Button creditButton = new Button("Credit");
        sellButton.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;-fx-text-fill: #d32f2f;");
        addToCartButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;-fx-text-fill: #d32f2f;");
        removeSelected.setMaxWidth(Double.MAX_VALUE); clearCart.setMaxWidth(Double.MAX_VALUE);
        sellButton.setMaxWidth(Double.MAX_VALUE); creditButton.setMaxWidth(Double.MAX_VALUE);
        removeSelected.setPrefHeight(50); clearCart.setPrefHeight(50);
        sellButton.setPrefHeight(50); creditButton.setPrefHeight(50);
        removeSelected.prefWidthProperty().bind(footer.widthProperty().multiply(0.20));
        sellButton.prefWidthProperty().bind(footer.widthProperty().multiply(0.50));
        creditButton.prefWidthProperty().bind(footer.widthProperty().multiply(0.10));
        clearCart.prefWidthProperty().bind(footer.widthProperty().multiply(0.20));

        rightPane.getChildren().add(righttPaneTitle);
        for (int i = 0; i < 6; i++) {
            Button btn = new Button("Empty Row Slot");
            btn.setPrefSize(100, 80);
            btn.setStyle("-fx-font-size: 16px;");
            btn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            btn.setDisable(true);
            int index = i;
            btn.setOnAction(e -> { if (index < quickProducts.size() && quickProducts.get(index) != null) addToCart(quickProducts.get(index), 1); });
            quickButtons.add(btn); quickProducts.add(null);
            rightPane.getChildren().add(btn);
        }

        loadQuickButtons(); recalcDailyTotal();
        searchInput.textProperty().addListener((obs, old, n) -> updateProductMatches(searchInput, matchedProducts, feedback));
        addToCartButton.setOnAction(e -> {
            try {
                int qty = Integer.parseInt(qtyInput.getText().trim());
                Product selected = matchedProducts.getSelectionModel().getSelectedItem();
                if (selected == null) { feedback.setText("Select a product from search results"); return; }
                addToCart(selected, qty); feedback.setText("Added " + selected.getName());
                searchInput.clear(); qtyInput.clear(); matchedProducts.getSelectionModel().clearSelection();
            } catch (Exception ex) { feedback.setText(ex.getMessage()); }
        });
        
        logout.setOnAction(e -> { context.activityLogService().log(context.currentUser().id(), "Logged out"); context.logout(); sceneManager.show(ScreenType.LOGIN); });
        sellButton.setOnAction(e -> {
            try {
                long saleId = context.saleService().checkout(cart, context.currentUser(), paymentMethod.getValue());
                cart.clear(); recalcTotal(); recalcDailyTotal(); feedback.setText("Sale completed: #" + saleId);
            } catch (Exception ex) { feedback.setText(ex.getMessage()); }
        });
        creditButton.setOnAction(e -> handleCreditSale());
        clearCart.setOnAction(e -> { cart.clear(); recalcTotal(); feedback.setText("Cart emptied."); });
        removeSelected.setOnAction(e -> { CartItem s = table.getSelectionModel().getSelectedItem(); if (s != null) { cart.remove(s); recalcTotal(); feedback.setText("Removed " + s.getName()); } });

        VBox.setVgrow(table, Priority.ALWAYS);
        HBox topRightControls = new HBox(15, dailyTotalLabel, logout);
        topRightControls.setAlignment(Pos.CENTER_RIGHT);
        header.getChildren().addAll(userGreeting, spacer1, title, spacer2, topRightControls);
        centerPane.getChildren().addAll(cartLabel, table);
        leftPane.getChildren().addAll(leftPaneTitle, searchInput, matchedProducts, qtyInput, paymentMethod, addToCartButton, totalLabel, feedback, lowStockAlert);
        footer.getChildren().addAll(removeSelected, spacer3, sellButton, clearCart, creditButton);
        mainContent.getChildren().addAll(leftPane, centerPane, rightPane);
        root.getChildren().addAll(header, mainContent, footer);
        
        return root;
    }

    private void handleCreditSale() {
        if (cart.isEmpty()) { feedback.setText("Cart is empty!"); return; }
        boolean authorized = context.isAdmin();
        if (!authorized) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("PIN Authorization"); dialog.setHeaderText("Authorize Credit Transaction");
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && result.get().equals(context.settingsService().getAll().getOrDefault("userPin", ""))) authorized = true;
            else feedback.setText("Invalid PIN.");
        }
        if (authorized) {
            Dialog<Pair<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Credit Details");
            GridPane grid = new GridPane(); TextField nF = new TextField(); TextField pF = new TextField();
            grid.add(new Label("Name:"), 0, 0); grid.add(nF, 1, 0);
            grid.add(new Label("Phone:"), 0, 1); grid.add(pF, 1, 1);
            dialog.getDialogPane().setContent(grid); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(b -> b == ButtonType.OK ? new Pair<>(nF.getText(), pF.getText()) : null);
            dialog.showAndWait().ifPresent(pair -> {
                try { context.saleService().saveCreditSale(cart, context.currentUser(), pair.getKey(), pair.getValue()); feedback.setText("Credit recorded."); cart.clear(); recalcTotal(); }
                catch (Exception ex) { feedback.setText("Error: " + ex.getMessage()); }
            });
        }
    }
 
    private void addToCart(Product product, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be > 0.");
        int inCart = cart.stream().filter(i -> i.getProduct().getId() == product.getId()).mapToInt(CartItem::getQuantity).findFirst().orElse(0);
        if ((qty + inCart) > product.getStock()) throw new IllegalArgumentException("Insufficient stock!");
        for (CartItem item : cart) {
            if (item.getProduct().getId() == product.getId()) { item.addQuantity(qty); recalcTotal(); return; }
        }
        cart.add(new CartItem(product, qty)); recalcTotal();
    }

    private void recalcTotal() { totalLabel.setText("Total: %.2f".formatted(cart.stream().mapToDouble(CartItem::getSubtotal).sum())); }

    private void recalcDailyTotal() {
        try {
            var data = context.reportService().getSalesByDate(LocalDate.now().toString()); 
            double dayTotal = data.stream().filter(l -> {
                String u = l.cashier(); String c = (u != null && u.contains(" ")) ? u.split(" ")[1] : (u != null ? u : "System");
                return c.equalsIgnoreCase(context.currentUser().username());
            }).mapToDouble(ReportService.SalesLine::total).sum();
            dailyTotalLabel.setText("Today's Sales: %.2f".formatted(dayTotal));
        } catch (Exception ex) { dailyTotalLabel.setText("Today's Sales: 0.00"); }
    }

    private void updateProductMatches(TextField s, ComboBox<Product> m, Label f) {
        String name = s.getText().trim();
        if (name.isBlank()) { m.getItems().clear(); return; }
        var matches = context.productService().searchByName(name);
        m.getItems().setAll(matches);
        if (matches.isEmpty()) f.setText("No products found.");
        else { m.getSelectionModel().selectFirst(); m.show(); f.setText("Found " + matches.size() + " matches."); }
    }

    private void loadQuickButtons() {
        var settings = context.settingsService().getAll();
        for (int i = 0; i < 6; i++) {
            String pName = settings.get("quickBtnName" + i);
            if (pName == null || pName.isBlank()) continue;
            var items = context.productService().searchByName(pName.trim());
            if (!items.isEmpty()) {
                Product p = items.get(0); quickProducts.set(i, p);
                quickButtons.get(i).setText(p.getName()); quickButtons.get(i).setDisable(false);
            }
        }
    }
}