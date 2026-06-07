package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.model.Credit;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;

public class CreditManagementScreen {

    private final AppContext context;
    private final TableView<Credit> activeTable = new TableView<>();
    private final TableView<Credit> closedTable = new TableView<>();
    private final ObservableList<Credit> activeList = FXCollections.observableArrayList();
    private final ObservableList<Credit> closedList = FXCollections.observableArrayList();
    private final Label feedback = new Label();

    public CreditManagementScreen(AppContext context) {
        this.context = context;
    }

    public Parent build() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        setupTable(activeTable, activeList);
        setupTable(closedTable, closedList);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.5);
        splitPane.getItems().addAll(
                new VBox(5, new Label("Active Credits:"), activeTable),
                new VBox(5, new Label("Closed Credits:"), closedTable)
        );

        Button payBtn = new Button("Make Payment");
        payBtn.setOnAction(e -> handlePayment());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> loadAll());

        Button historyBtn = new Button("View History");
        historyBtn.setOnAction(e -> showHistory());

        HBox bar = new HBox(10, payBtn, refreshBtn, historyBtn);
        layout.getChildren().addAll(splitPane, bar, feedback);

        loadAll();
        return layout;
    }

    private void setupTable(TableView<Credit> table, ObservableList<Credit> list) {
        table.getColumns().clear();
        String[] cols = {"Customer", "Phone", "Products", "Total", "Paid", "Balance", "Created By"};
        String[] props = {"customerName", "phone", "products", "totalAmount", "paid", "balance", "createdBy"};

        for (int i = 0; i < cols.length; i++) {
            TableColumn<Credit, ?> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(new PropertyValueFactory<>(props[i]));
            col.setPrefWidth(120);
            table.getColumns().add(col);
        }
        table.setItems(list);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void loadAll() {
        try {
            activeList.setAll(context.saleService().getCreditsByStatus("ACTIVE"));
            closedList.setAll(context.saleService().getCreditsByStatus("CLOSED"));
        } catch (Exception e) {
            feedback.setText("Error: " + e.getMessage());
        }
    }

    private void handlePayment() {
        Credit selected = activeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select an ACTIVE credit to pay.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Credit Payment");
        dialog.setHeaderText("Customer: " + selected.getCustomerName());
        dialog.setContentText("Amount:");

        dialog.showAndWait().ifPresent(val -> {
            try {
                double amount = Double.parseDouble(val);
                context.saleService().processCreditPayment(selected.getId(), amount, context.currentUser());
                feedback.setText("Payment successful");
                loadAll();
            } catch (Exception ex) {
                feedback.setText("Error: " + ex.getMessage());
            }
        });
    }

    private void showHistory() {
        // Allow selection from either table
        Credit selected = activeTable.getSelectionModel().getSelectedItem();
        if (selected == null) selected = closedTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            feedback.setText("Select a credit from either list to view history.");
            return;
        }

        ListView<String> list = new ListView<>();
        try {
            List<String> history = context.saleService().getCreditPaymentHistory(selected.getId());
            list.getItems().setAll(history);
        } catch (Exception e) {
            feedback.setText("Error loading history: " + e.getMessage());
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Payment History - " + selected.getCustomerName());
        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
}