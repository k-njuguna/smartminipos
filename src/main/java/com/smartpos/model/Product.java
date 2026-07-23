package com.smartpos.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Product {
    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final DoubleProperty price = new SimpleDoubleProperty();
    private final LongProperty stock = new SimpleLongProperty();
    private final ObjectProperty<Long> lowStockThreshold = new SimpleObjectProperty<>(); // Supports null for fallback
    private final BooleanProperty status = new SimpleBooleanProperty(true);


    // Original clean constructor (Defaults threshold to null)
    public Product(long id, String name, double price, long stock) {
    this(id, name, price, stock, null, true);
}

// Designated constructor handling threshold and active status
public Product(long id, String name, double price, long stock, Long threshold, boolean active) {
    this.id.set(id);
    this.name.set(name);
    this.price.set(price);
    this.stock.set(stock);
    this.lowStockThreshold.set(threshold);
    this.status.set(active);
}

// Getters and Setters
public long getId() { return id.get(); }
public String getName() { return name.get(); }
public double getPrice() { return price.get(); }
public long getStock() { return stock.get(); }
public Long getLowStockThreshold() { return lowStockThreshold.get(); }
public boolean isActive() { return status.get(); }

public void setName(String value) { this.name.set(value); }
public void setPrice(double value) { this.price.set(value); }
public void setStock(long value) { this.stock.set(value); }
public void setLowStockThreshold(Long value) { this.lowStockThreshold.set(value); }
public void setActive(boolean value) { this.status.set(value); }

// JavaFX Properties for direct UI binding templates
public LongProperty idProperty() { return id; }
public StringProperty nameProperty() { return name; }
public DoubleProperty priceProperty() { return price; }
public LongProperty stockProperty() { return stock; }
public ObjectProperty<Long> lowStockThresholdProperty() { return lowStockThreshold; }
public BooleanProperty activeProperty() { return status; }

@Override
public String toString() { return getName(); }

}
   /* public Product(long id, String name, double price, long stock) {
        this(id, name, price, stock, null);
    }

    // New designated constructor handling the custom override threshold
    public Product(long id, String name, double price, long stock, Long threshold) {
        this.id.set(id);
        this.name.set(name);
        this.price.set(price);
        this.stock.set(stock);
        this.lowStockThreshold.set(threshold);
    }

    // Getters and Setters
    public long getId() { return id.get(); }
    public String getName() { return name.get(); }
    public double getPrice() { return price.get(); }
    public long getStock() { return stock.get(); }
    public Long getLowStockThreshold() { return lowStockThreshold.get(); }

    public void setName(String value) { this.name.set(value); }
    public void setPrice(double value) { this.price.set(value); }
    public void setStock(long value) { this.stock.set(value); }
    public void setLowStockThreshold(Long value) { this.lowStockThreshold.set(value); }

    // JavaFX Properties for direct UI binding templates
    public LongProperty idProperty() { return id; }
    public StringProperty nameProperty() { return name; }
    public DoubleProperty priceProperty() { return price; }
    public LongProperty stockProperty() { return stock; }
    public ObjectProperty<Long> lowStockThresholdProperty() { return lowStockThreshold; }
    
    @Override
    public String toString() { return getName();   }
}*/