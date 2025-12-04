package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import com.azs.ApiClient;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

public class PricesController {
    @FXML private Label ai92Price;
    @FXML private Label ai95Price;
    @FXML private Label ai98Price;
    @FXML private Label ai100Price;
    @FXML private Label dtk5Price;

    @FXML
    private void initialize() {
        loadFuelPrices();
    }

    private void loadFuelPrices() {
        CompletableFuture<JsonObject> future = ApiClient.getFuelPrices();

        future.thenAccept(response -> {
            javafx.application.Platform.runLater(() -> {
                if (response.has("success") && response.get("success").getAsBoolean()) {
                    // Парсим цены из ответа сервера
                    if (response.has("ai92")) {
                        ai92Price.setText(response.get("ai92").getAsString() + " BYN");
                    }
                    if (response.has("ai95")) {
                        ai95Price.setText(response.get("ai95").getAsString() + " BYN");
                    }
                    if (response.has("ai98")) {
                        ai98Price.setText(response.get("ai98").getAsString() + " BYN");
                    }
                    if (response.has("ai100")) {
                        ai100Price.setText(response.get("ai100").getAsString() + " BYN");
                    }
                    if (response.has("dtk5")) {
                        dtk5Price.setText(response.get("dtk5").getAsString() + " BYN");
                    }
                } else {
                    // Если нет данных с сервера, показываем дефолтные значения
                    ai92Price.setText("2.50 BYN");
                    ai95Price.setText("2.60 BYN");
                    ai98Price.setText("2.83 BYN");
                    ai100Price.setText("2.93 BYN");
                    dtk5Price.setText("2.60 BYN");
                }
            });
        }).exceptionally(e -> {
            javafx.application.Platform.runLater(() -> {
                // В случае ошибки показываем дефолтные значения
                ai92Price.setText("2.50 BYN");
                ai95Price.setText("2.60 BYN");
                ai98Price.setText("2.83 BYN");
                ai100Price.setText("2.93 BYN");
                dtk5Price.setText("2.60 BYN");
            });
            return null;
        });
    }
}