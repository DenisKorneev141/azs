package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import com.azs.ApiClient;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;

public class PricesController {
    @FXML private Label ai92Price;
    @FXML private Label ai95Price;
    @FXML private Label ai98Price;
    @FXML private Label ai100Price;
    @FXML private Label dtk5Price;
    @FXML private Label dtPrice; // Для обычного дизеля
    @FXML private Label loadingLabel;

    @FXML
    private void initialize() {
        System.out.println("PricesController инициализирован");
        loadFuelPrices();
    }

    private void loadFuelPrices() {
        // Показываем индикатор загрузки
        showLoadingState();

        CompletableFuture<JsonObject> future = ApiClient.getFuelPrices();

        future.thenAccept(response -> {
            Platform.runLater(() -> {
                System.out.println("Получен ответ от сервера: " + response);

                if (response.has("success") && response.get("success").getAsBoolean()) {
                    if (response.has("data")) {
                        JsonObject fuelData = response.getAsJsonObject("data");
                        updateFuelPrices(fuelData);
                        hideLoadingState();
                    } else {
                        System.err.println("Нет поля 'data' в ответе сервера");
                        setDefaultPrices();
                    }
                } else {
                    String errorMsg = response.has("message") ?
                            response.get("message").getAsString() : "Неизвестная ошибка";
                    System.err.println("Ошибка загрузки цен: " + errorMsg);
                    setDefaultPrices();
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                System.err.println("Исключение при загрузке цен: " + e.getMessage());
                e.printStackTrace();
                setDefaultPrices();
            });
            return null;
        });
    }

    private void updateFuelPrices(JsonObject fuelData) {
        System.out.println("Обновление цен из данных: " + fuelData);

        // Обновляем каждую цену, если она есть в ответе
        if (fuelData.has("ai92")) {
            ai92Price.setText(fuelData.get("ai92").getAsString() + " BYN");
            System.out.println("AI-92: " + fuelData.get("ai92").getAsString());
        } else {
            ai92Price.setText("—");
        }

        if (fuelData.has("ai95")) {
            ai95Price.setText(fuelData.get("ai95").getAsString() + " BYN");
            System.out.println("AI-95: " + fuelData.get("ai95").getAsString());
        } else {
            ai95Price.setText("—");
        }

        if (fuelData.has("ai98")) {
            ai98Price.setText(fuelData.get("ai98").getAsString() + " BYN");
            System.out.println("AI-98: " + fuelData.get("ai98").getAsString());
        } else {
            ai98Price.setText("—");
        }

        if (fuelData.has("ai100")) {
            ai100Price.setText(fuelData.get("ai100").getAsString() + " BYN");
            System.out.println("AI-100: " + fuelData.get("ai100").getAsString());
        } else {
            ai100Price.setText("—");
        }

        if (fuelData.has("dtk5")) {
            dtk5Price.setText(fuelData.get("dtk5").getAsString() + " BYN");
            System.out.println("ДТК-5: " + fuelData.get("dtk5").getAsString());
        } else if (fuelData.has("dt")) {
            dtk5Price.setText(fuelData.get("dt").getAsString() + " BYN");
            System.out.println("ДТ: " + fuelData.get("dt").getAsString());
        } else {
            dtk5Price.setText("—");
        }

        // Для обычного дизеля (если есть отдельное поле)
        if (dtPrice != null && fuelData.has("dt")) {
            dtPrice.setText(fuelData.get("dt").getAsString() + " BYN");
        }
    }

    private void setDefaultPrices() {
        System.out.println("Установка цен по умолчанию");
        ai92Price.setText("2.50 BYN");
        ai95Price.setText("2.60 BYN");
        ai98Price.setText("2.83 BYN");
        ai100Price.setText("2.93 BYN");
        dtk5Price.setText("2.60 BYN");
        if (dtPrice != null) {
            dtPrice.setText("2.55 BYN");
        }
        hideLoadingState();
    }

    private void showLoadingState() {
        if (loadingLabel != null) {
            loadingLabel.setVisible(true);
            loadingLabel.setText("Загрузка цен...");
        }
        // Делаем цены серыми пока идет загрузка
        ai92Price.setText("...");
        ai95Price.setText("...");
        ai98Price.setText("...");
        ai100Price.setText("...");
        dtk5Price.setText("...");
        if (dtPrice != null) {
            dtPrice.setText("...");
        }
    }

    private void hideLoadingState() {
        if (loadingLabel != null) {
            loadingLabel.setVisible(false);
        }
    }

    @FXML
    private void refreshPrices() {
        System.out.println("Обновление цен...");
        loadFuelPrices();
    }
}