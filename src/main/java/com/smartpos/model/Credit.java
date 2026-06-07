package com.smartpos.model;

import javafx.beans.property.*;

public class Credit {

    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty customerName = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty products = new SimpleStringProperty();
    private final DoubleProperty totalAmount = new SimpleDoubleProperty();
    private final DoubleProperty paid = new SimpleDoubleProperty();

    private final StringProperty createdBy = new SimpleStringProperty();

    public Credit(long id,
                  String customerName,
                  String phone,
                  String products,
                  double totalAmount,
                  double paid) {

        this.id.set(id);
        this.customerName.set(customerName);
        this.phone.set(phone);
        this.products.set(products);
        this.totalAmount.set(totalAmount);
        this.paid.set(paid);
    }

    // ================= GETTERS =================

    public long getId() { return id.get(); }
    public String getCustomerName() { return customerName.get(); }
    public String getPhone() { return phone.get(); }
    public String getProducts() { return products.get(); }
    public double getTotalAmount() { return totalAmount.get(); }
    public double getPaid() { return paid.get(); }

    public double getBalance() {
        return totalAmount.get() - paid.get();
    }

    public String getCreatedBy() { return createdBy.get(); }

    // ================= PROPERTY ACCESS =================

    public LongProperty idProperty() { return id; }
    public StringProperty customerNameProperty() { return customerName; }
    public StringProperty phoneProperty() { return phone; }
    public StringProperty productsProperty() { return products; }
    public DoubleProperty totalAmountProperty() { return totalAmount; }
    public DoubleProperty paidProperty() { return paid; }
    public StringProperty createdByProperty() { return createdBy; }
}