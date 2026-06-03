package com.smartpos.ui.screen;

import com.smartpos.app.AppContext;
import com.smartpos.app.SceneManager;
import com.smartpos.service.ReportService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SalesReportsScreen {
    private final AppContext context;
    private final SceneManager sceneManager;

    public SalesReportsScreen(AppContext context, SceneManager sceneManager) {
        this.context = context;
        this.sceneManager = sceneManager;
    }

    /**
     * Builds the full Business Intelligence dash matrix dashboard layout view.
     */
    public Parent build() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f4f7f6;");

        Label header = new Label("Comprehensive Business Intelligence Dashboard");
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox columns = new HBox(15);
        HBox.setHgrow(columns, Priority.ALWAYS);

        // --- COLUMN 1: DETAILED DAILY LEDGER ---
        VBox col1 = createColumn("Daily Sales Ledger", "#34495e");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        
        Label salesStatus = new Label();
        salesStatus.setStyle("-fx-font-weight: bold;");

        VBox summaryContainer = new VBox(5);
        summaryContainer.setPadding(new Insets(10));
        summaryContainer.setStyle("-fx-background-color: #fcfcfc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");
        
        Label totalDateSalesLabel = new Label("Total Date Sales: KES 0.00");
        totalDateSalesLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2b6cb0;");
        
        VBox userBreakdownBox = new VBox(3);
        summaryContainer.getChildren().addAll(totalDateSalesLabel, new Separator(), userBreakdownBox);
        
        TableView<ReportService.SalesLine> salesTable = new TableView<>();
        setupDetailedSalesTable(salesTable);

        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                refreshSales(salesTable, newV.toString(), salesStatus, totalDateSalesLabel, userBreakdownBox);
            }
        });
        
        refreshSales(salesTable, LocalDate.now().toString(), salesStatus, totalDateSalesLabel, userBreakdownBox);
        
        col1.getChildren().addAll(
            new Label("Select Target Operations Date:"), 
            datePicker, 
            salesStatus, 
            summaryContainer, 
            salesTable
        );

        // --- COLUMN 3: SYSTEM INVENTORY LEDGER ---
        VBox col3 = createColumn("Inventory Stock Status", "#c0392b");
        TableView<ReportService.LowStockLine> invTable = new TableView<>();
        setupInventoryTable(invTable);
        invTable.setItems(FXCollections.observableArrayList(context.reportService().getFullInventoryReport()));
        col3.getChildren().add(invTable);

        columns.getChildren().addAll(col1, col3);
        root.getChildren().addAll(header, columns);

        ScrollPane mainScroll = new ScrollPane(root);
        mainScroll.setFitToWidth(true);
        return sceneManager.withAdminNav(mainScroll);
    }

    private void refreshSales(TableView<ReportService.SalesLine> table, String date, Label status, Label totalSalesLabel, VBox userBreakdownBox) {
        List<ReportService.SalesLine> data = context.reportService().getSalesByDate(date);
        table.setItems(FXCollections.observableArrayList(data));
        
        userBreakdownBox.getChildren().clear();
        
        if (data.isEmpty()) {
            status.setText("No transactional logs found on: " + date);
            status.setTextFill(Color.RED);
            totalSalesLabel.setText("Total Date Sales: KES 0.00");
            Label emptyLabel = new Label("No cashier records active.");
            emptyLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #a0aec0;");
            userBreakdownBox.getChildren().add(emptyLabel);
        } else {
            status.setText("Total Day Records: " + data.size() + " active checkout(s)");
            status.setTextFill(Color.GREEN);
            
            double aggregateDayTotal = data.stream()
                                           .mapToDouble(ReportService.SalesLine::total)
                                           .sum();
            totalSalesLabel.setText(String.format("Total Date Sales: KES %.2f", aggregateDayTotal));
            
            Map<String, Double> salesPerUser = data.stream()
                .collect(Collectors.groupingBy(
                    line -> {
                        String tu = line.cashier();
                        if (tu != null && tu.contains(" ")) {
                            String[] parts = tu.split(" ");
                            return parts.length > 1 ? parts[1] : tu;
                        }
                        return tu != null ? tu : "System";
                    },
                    Collectors.summingDouble(ReportService.SalesLine::total)
                ));
            
            salesPerUser.forEach((user, totalValue) -> {
                Label userLabel = new Label(String.format(" • User: %-15s Total: KES %.2f", user, totalValue));
                userLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-text-fill: #4a5568;");
                userBreakdownBox.getChildren().add(userLabel);
            });
        }
    }

    private void setupDetailedSalesTable(TableView<ReportService.SalesLine> t) {
    TableColumn<ReportService.SalesLine, String> cTime = new TableColumn<>("Time Stamp");
    cTime.setCellValueFactory(v -> {
        String ts = v.getValue().timestamp();
        if (ts != null && ts.contains(" ")) {
            String[] parts = ts.split(" ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 1 ? parts[1] : ts);
        }
        return new javafx.beans.property.SimpleStringProperty(ts != null ? ts : "");
    });
    
    TableColumn<ReportService.SalesLine, String> cItems = new TableColumn<>("Items Sold Summary");
    cItems.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(v.getValue().itemsSold()));
    cItems.setPrefWidth(250); 

    // Isolated Cell Factory to enforce wrapping on items list
    cItems.setCellFactory(tc -> new TableCell<>() {
        private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();

        {
            textNode.setWrappingWidth(cItems.getWidth() - 12);
            setGraphic(textNode);
            setPadding(new Insets(6)); 

            // Explicitly updates wrapping boundaries when columns resize dynamically
            cItems.widthProperty().addListener((obs, oldW, newW) -> {
                if (newW != null) {
                    textNode.setWrappingWidth(newW.doubleValue() - 12);
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                textNode.setText("");
            } else {
                textNode.setText(item);
            }
        }
    });

    TableColumn<ReportService.SalesLine, String> cTotal = new TableColumn<>("Total Value");
    cTotal.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(String.format("KES %.2f", v.getValue().total())));
    
    TableColumn<ReportService.SalesLine, String> cUser = new TableColumn<>("Handled By");
    cUser.setCellValueFactory(v -> {
        String tu = v.getValue().cashier();
        if (tu != null && tu.contains(" ")) {
            String[] parts = tu.split(" ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 1 ? parts[1] : tu);
        }
        return new javafx.beans.property.SimpleStringProperty(tu != null ? tu : "System");
    });
    
    t.getColumns().addAll(cTime, cItems, cTotal, cUser);
    t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    t.setPrefHeight(600);
}

    private void setupInventoryTable(TableView<ReportService.LowStockLine> t) {
        TableColumn<ReportService.LowStockLine, String> cName = new TableColumn<>("Product Description");
        cName.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(v.getValue().productName()));
        
        TableColumn<ReportService.LowStockLine, String> cStock = new TableColumn<>("Available Stock");
        cStock.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(String.valueOf(v.getValue().stock())));
        
        t.getColumns().addAll(cName, cStock);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPrefHeight(600);
    }

    private VBox createColumn(String title, String color) {
        VBox v = new VBox(10);
        v.setPadding(new Insets(12));
        v.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 8; -fx-background-radius: 8;");
        HBox.setHgrow(v, Priority.ALWAYS);
        
        Label l = new Label(title);
        l.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        v.getChildren().add(l);
        return v;
    }
}