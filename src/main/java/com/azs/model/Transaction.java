// Transaction.java
package com.azs.model;

import java.time.LocalDateTime;

public class Transaction {
    private int id;
    private LocalDateTime time;
    private String fuelType;
    private double liters;
    private double amount;
    private String paymentMethod;
    private String status;

    public Transaction(int id, LocalDateTime time, String fuelType,
                       double liters, double amount, String paymentMethod, String status) {
        this.id = id;
        this.time = time;
        this.fuelType = fuelType;
        this.liters = liters;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
    }

    // Геттеры
    public int getId() { return id; }
    public LocalDateTime getTime() { return time; }
    public String getFuelType() { return fuelType; }
    public double getLiters() { return liters; }
    public double getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }

    public String getFormattedTime() {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    public String getFormattedAmount() {
        return String.format("%,.2f ₽", amount);
    }

    public String getFormattedLiters() {
        return String.format("%.1f л", liters);
    }
}