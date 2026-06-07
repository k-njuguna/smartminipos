package com.smartpos.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class CartItem {
    private final Product product;
    private final StringProperty name = new SimpleStringProperty();
    private final DoubleProperty unitPrice = new SimpleDoubleProperty();
    private final IntegerProperty quantity = new SimpleIntegerProperty();
    private final DoubleProperty subtotal = new SimpleDoubleProperty();

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.name.set(product.getName());
        this.unitPrice.set(product.getPrice());
        this.quantity.set(quantity);
        
        // Powerful JavaFX Binding Optimization:
        // Automatically updates 'subtotal' whenever 'unitPrice' or 'quantity' changes.
        // This eliminates manual recalculation bugs entirely!
        this.subtotal.bind(this.unitPrice.multiply(this.quantity));
    }

    /**
     * Increments the current quantity wrapper. 
     * The bound subtotal property reacts and recalculates itself instantly.
     */
    public void addQuantity(int qty) {
        this.quantity.set(this.quantity.get() + qty);
    }

    /**
     * Explicit setter for direct modifications (e.g., changing quantity manually in a TableView text field).
     */
    public void setQuantity(int qty) {
        this.quantity.set(qty);
    }

    public Product getProduct() { return product; }
    public String getName() { return name.get(); }
    public double getUnitPrice() { return unitPrice.get(); }
    public int getQuantity() { return quantity.get(); }
    public double getSubtotal() { return subtotal.get(); }

    public StringProperty nameProperty() { return name; }
    public DoubleProperty unitPriceProperty() { return unitPrice; }
    public IntegerProperty quantityProperty() { return quantity; }
    public DoubleProperty subtotalProperty() { return subtotal; }
}