package com.azs.controllers;

import com.azs.ApiClient;
import com.azs.model.UserSession;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

public class NewTransactionController implements Initializable {

    // === Шаг 1: Выбор топлива ===
    @FXML private RadioButton rbFuel92;
    @FXML private RadioButton rbFuel95;
    @FXML private RadioButton rbFuel98;
    @FXML private RadioButton rbFuel100;
    @FXML private RadioButton rbFuelDT;
    private ToggleGroup fuelGroup;

    // === Шаг 2: Количество ===
    @FXML private TextField tfAmount;
    @FXML private TextField tfLiters;

    // === Шаг 3: Колонки ===
    @FXML private Label lblNozzle1Status, lblNozzle2Status, lblNozzle3Status, lblNozzle4Status;
    @FXML private RadioButton rbNozzle1, rbNozzle2, rbNozzle3, rbNozzle4;
    private ToggleGroup nozzleGroup;

    // === Итог ===
    @FXML private Label lblTotalFuelType, lblTotalPrice, lblTotalLiters, lblTotalAmount;

    // === Бонусная карта ===
    @FXML private TextField tfPhone;
    @FXML private Button btnFindClient;
    @FXML private Label lblClientInfo;
    @FXML private Label lblBonusBalance;
    @FXML private Label lblClientName;

    // === Расчет ===
    @FXML private TextField tfCashIn;
    @FXML private TextField tfBonusSpend;
    @FXML private Button btnCalculate;

    // === Проверка ===
    @FXML private Label lblCheckTotal, lblCheckCashIn, lblCheckBonus, lblCheckChange;

    // === Оплата ===
    @FXML private Button btnPayCash;
    @FXML private Button btnPayCard;
    @FXML private Button btnExit;

    // === Статус ===
    @FXML private Label statusLabel;
    @FXML private Label lblServerStatus;

    // === Данные ===
    private JsonArray fuelsData = new JsonArray();
    private JsonObject currentFuel;
    private JsonObject currentAzsData;
    private JsonObject currentUser;

    private double currentPrice = 0.0;
    private double liters = 0.0;
    private double amount = 0.0;
    private double totalAmount = 0.0;
    private double cashIn = 0.0;
    private double bonusSpend = 0.0;
    private double change = 0.0;
    private double userBonusBalance = 0.0;
    private int userId = 0;

    private int selectedNozzle = 0;
    private int azsId;
    private boolean isUpdating = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Инициализация контроллера новой транзакции...");

        azsId = UserSession.getAzsId();
        System.out.println("АЗС ID: " + azsId);

        // Инициализация групп переключателей
        initToggleGroups();

        // Настройка обработчиков событий
        setupEventHandlers();

        // Загрузка данных
        loadFuelPrices();
        loadNozzleStatus();

        // Обновление статуса сервера
        updateServerStatus();
    }

    private void initToggleGroups() {
        // Группа для топлива
        fuelGroup = new ToggleGroup();
        rbFuel92.setToggleGroup(fuelGroup);
        rbFuel95.setToggleGroup(fuelGroup);
        rbFuel98.setToggleGroup(fuelGroup);
        rbFuel100.setToggleGroup(fuelGroup);
        rbFuelDT.setToggleGroup(fuelGroup);

        // Группа для колонок
        nozzleGroup = new ToggleGroup();
        rbNozzle1.setToggleGroup(nozzleGroup);
        rbNozzle2.setToggleGroup(nozzleGroup);
        rbNozzle3.setToggleGroup(nozzleGroup);
        rbNozzle4.setToggleGroup(nozzleGroup);
    }

    private void setupEventHandlers() {
        // Обработка выбора топлива
        fuelGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handleFuelSelection((RadioButton) newVal);
            }
        });

        // Обработка ввода суммы/литров
        tfAmount.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdating && newVal != null && !newVal.isEmpty()) {
                try {
                    amount = Double.parseDouble(newVal.replace(",", "."));
                    if (currentPrice > 0) {
                        liters = amount / currentPrice;
                        isUpdating = true;
                        tfLiters.setText(String.format("%.2f", liters));
                        isUpdating = false;
                        updateTotal();
                    }
                } catch (NumberFormatException e) {
                    // Игнорируем неверный ввод
                }
            }
        });

        tfLiters.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdating && newVal != null && !newVal.isEmpty()) {
                try {
                    liters = Double.parseDouble(newVal.replace(",", "."));
                    amount = liters * currentPrice;
                    isUpdating = true;
                    tfAmount.setText(String.format("%.2f", amount));
                    isUpdating = false;
                    updateTotal();
                } catch (NumberFormatException e) {
                    // Игнорируем неверный ввод
                }
            }
        });

        // Обработка выбора колонки
        nozzleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handleNozzleSelection((RadioButton) newVal);
            }
        });

        // Поиск клиента по телефону
        btnFindClient.setOnAction(e -> findClientByPhone());

        // Расчет сдачи
        btnCalculate.setOnAction(e -> calculateChange());

        // Оплата
        btnPayCash.setOnAction(e -> processPayment("Наличные"));
        btnPayCard.setOnAction(e -> processPayment("Банковская карта"));

        // Выход
        btnExit.setOnAction(e -> handleExit());
    }

    private void loadFuelPrices() {
        ApiClient.getFuelPrices().thenAccept(response -> {
            Platform.runLater(() -> {
                if (response.get("success").getAsBoolean()) {
                    JsonObject data = response.getAsJsonObject("data");
                    fuelsData = new JsonArray();

                    // Заполняем данные о топливе
                    if (data.has("ai92_raw")) {
                        JsonObject fuel92 = new JsonObject();
                        fuel92.addProperty("id", 1);
                        fuel92.addProperty("name", "АИ-92");
                        fuel92.addProperty("price", data.get("ai92_raw").getAsDouble());
                        fuelsData.add(fuel92);
                        rbFuel92.setText("АИ-92 - " + data.get("ai92").getAsString() + " BYN");
                    }

                    if (data.has("ai95_raw")) {
                        JsonObject fuel95 = new JsonObject();
                        fuel95.addProperty("id", 2);
                        fuel95.addProperty("name", "АИ-95");
                        fuel95.addProperty("price", data.get("ai95_raw").getAsDouble());
                        fuelsData.add(fuel95);
                        rbFuel95.setText("АИ-95 - " + data.get("ai95").getAsString() + " BYN");
                        currentPrice = data.get("ai95_raw").getAsDouble();
                        currentFuel = fuel95;
                    }

                    if (data.has("ai98_raw")) {
                        JsonObject fuel98 = new JsonObject();
                        fuel98.addProperty("id", 3);
                        fuel98.addProperty("name", "АИ-98");
                        fuel98.addProperty("price", data.get("ai98_raw").getAsDouble());
                        fuelsData.add(fuel98);
                        rbFuel98.setText("АИ-98 - " + data.get("ai98").getAsString() + " BYN");
                    }

                    if (data.has("ai100_raw")) {
                        JsonObject fuel100 = new JsonObject();
                        fuel100.addProperty("id", 4);
                        fuel100.addProperty("name", "АИ-100");
                        fuel100.addProperty("price", data.get("ai100_raw").getAsDouble());
                        fuelsData.add(fuel100);
                        rbFuel100.setText("АИ-100 - " + data.get("ai100").getAsString() + " BYN");
                    }

                    if (data.has("dt_raw") || data.has("dtk5_raw")) {
                        JsonObject fuelDT = new JsonObject();
                        fuelDT.addProperty("id", 5);
                        fuelDT.addProperty("name", "ДТ-К5");
                        double price = data.has("dt_raw") ? data.get("dt_raw").getAsDouble() : data.get("dtk5_raw").getAsDouble();
                        fuelDT.addProperty("price", price);
                        fuelsData.add(fuelDT);
                        rbFuelDT.setText("ДТ-К5 - " + String.format("%.2f", price) + " BYN");
                    }

                    System.out.println("Загружено " + fuelsData.size() + " видов топлива");

                    // Выбираем АИ-95 по умолчанию после загрузки данных
                    rbFuel95.setSelected(true);
                    updateTotal();
                } else {
                    showAlert("Ошибка загрузки цен на топливо", Alert.AlertType.ERROR);
                }
            });
        });
    }

    private void loadNozzleStatus() {
        ApiClient.getNozzlesStatus(azsId).thenAccept(response -> {
            Platform.runLater(() -> {
                if (response.get("success").getAsBoolean()) {
                    currentAzsData = response.getAsJsonObject("nozzles");
                    updateNozzleUI();
                } else {
                    showAlert("Ошибка загрузки статуса колонок", Alert.AlertType.ERROR);
                }
            });
        });
    }

    private void updateNozzleUI() {
        if (currentAzsData == null) return;

        // Обновляем статусы колонок
        String[] nozzleStatuses = new String[4];
        String[] nozzleFields = {"nozzle_1", "nozzle_2", "nozzle_3", "nozzle_4"};

        for (int i = 0; i < 4; i++) {
            if (currentAzsData.has(nozzleFields[i])) {
                nozzleStatuses[i] = currentAzsData.get(nozzleFields[i]).getAsString();
            } else {
                nozzleStatuses[i] = "not_available";
            }
        }

        // Обновляем UI для каждой колонки
        updateSingleNozzleUI(1, lblNozzle1Status, rbNozzle1, nozzleStatuses[0]);
        updateSingleNozzleUI(2, lblNozzle2Status, rbNozzle2, nozzleStatuses[1]);
        updateSingleNozzleUI(3, lblNozzle3Status, rbNozzle3, nozzleStatuses[2]);
        updateSingleNozzleUI(4, lblNozzle4Status, rbNozzle4, nozzleStatuses[3]);
    }

    private void updateSingleNozzleUI(int nozzleNum, Label statusLabel, RadioButton radioButton, String status) {
        String statusText = getStatusText(status);
        Color statusColor = getStatusColor(status);

        statusLabel.setText(statusText);
        statusLabel.setTextFill(statusColor);

        // Блокируем недоступные колонки
        boolean isAvailable = "active".equals(status);
        radioButton.setDisable(!isAvailable);

        // Если колонка доступна и еще не выбрана - выбираем первую доступную
        if (isAvailable && selectedNozzle == 0) {
            radioButton.setSelected(true);
            selectedNozzle = nozzleNum;
        }
    }

    private String getStatusText(String status) {
        switch (status) {
            case "active":
                return "Свободна";
            case "not_active":
                return "Неактивна";
            case "not_available":
                return "Недоступна";
            case "busy":
                return "Занята";
            default:
                return status;
        }
    }

    private Color getStatusColor(String status) {
        switch (status) {
            case "active":
                return Color.GREEN;
            case "not_active":
                return Color.RED;
            case "not_available":
                return Color.GRAY;
            case "busy":
                return Color.ORANGE;
            default:
                return Color.BLACK;
        }
    }

    private void handleFuelSelection(RadioButton selected) {
        String fuelName = selected.getText().split(" - ")[0];

        // Находим выбранное топливо в массиве
        for (int i = 0; i < fuelsData.size(); i++) {
            JsonObject fuel = fuelsData.get(i).getAsJsonObject();
            if (fuel.get("name").getAsString().equals(fuelName)) {
                currentFuel = fuel;
                currentPrice = fuel.get("price").getAsDouble();
                break;
            }
        }

        updateTotal();
    }

    private void handleNozzleSelection(RadioButton selected) {
        if (selected == rbNozzle1) selectedNozzle = 1;
        else if (selected == rbNozzle2) selectedNozzle = 2;
        else if (selected == rbNozzle3) selectedNozzle = 3;
        else if (selected == rbNozzle4) selectedNozzle = 4;
    }

    private void findClientByPhone() {
        String phone = tfPhone.getText().trim();
        if (phone.isEmpty()) {
            showAlert("Введите номер телефона", Alert.AlertType.WARNING);
            return;
        }

        // Форматируем номер телефона
        if (!phone.startsWith("+")) {
            phone = "+" + phone.replaceAll("[^0-9]", "");
        }

        // Отправляем запрос на сервер для поиска пользователя
        searchUserByPhone(phone);
    }

    private void searchUserByPhone(String phone) {
        // Создаем URL для поиска пользователя
        String url = ApiClient.getServerUrl() + "/api/users/search?phone=" + phone;

        CompletableFuture.supplyAsync(() -> {
            try {
                URL apiUrl = new URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        return gson.fromJson(response.toString(), JsonObject.class);
                    }
                } else {
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("message", "Ошибка сервера: " + responseCode);
                    return error;
                }
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Ошибка подключения: " + e.getMessage());
                return error;
            }
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.get("success").getAsBoolean()) {
                    currentUser = result.getAsJsonObject("user");
                    userId = currentUser.get("id").getAsInt();
                    userBonusBalance = currentUser.get("balance").getAsDouble();

                    lblClientName.setText(currentUser.get("name").getAsString());
                    lblClientInfo.setText("Клиент найден");
                    lblBonusBalance.setText("Баланс бонусов: " + String.format("%.2f", userBonusBalance) + " BYN");

                    // Устанавливаем подсказку для списания бонусов
                    tfBonusSpend.setPromptText("Максимум: " + String.format("%.2f", userBonusBalance) + " BYN");

                    statusLabel.setText("✅ Клиент найден: " + currentUser.get("name").getAsString());
                } else {
                    lblClientName.setText("Гость");
                    lblClientInfo.setText("Клиент не найден");
                    lblBonusBalance.setText("");
                    currentUser = null;
                    userId = 0;
                    userBonusBalance = 0.0;
                    statusLabel.setText("ℹ️ Клиент не найден, будет создана гостовая транзакция");
                }
            });
        });
    }

    private void calculateChange() {
        try {
            // Получаем введенные значения
            if (tfCashIn.getText() != null && !tfCashIn.getText().isEmpty()) {
                cashIn = Double.parseDouble(tfCashIn.getText().replace(",", "."));
            } else {
                cashIn = 0.0;
            }

            if (tfBonusSpend.getText() != null && !tfBonusSpend.getText().isEmpty()) {
                bonusSpend = Double.parseDouble(tfBonusSpend.getText().replace(",", "."));

                // Проверяем, что не списывается больше бонусов, чем есть
                if (bonusSpend > userBonusBalance) {
                    showAlert("Недостаточно бонусов на счете. Максимум: " +
                            String.format("%.2f", userBonusBalance) + " BYN", Alert.AlertType.WARNING);
                    bonusSpend = 0.0;
                    tfBonusSpend.setText("");
                    return;
                }
            } else {
                bonusSpend = 0.0;
            }

            // Пересчитываем итоговую сумму с учетом бонусов
            double finalAmount = totalAmount - bonusSpend;
            if (finalAmount < 0) finalAmount = 0;

            // Рассчитываем сдачу
            if (cashIn > 0) {
                change = cashIn - finalAmount;
                if (change < 0) {
                    showAlert("Внесено недостаточно средств. Необходимо еще: " +
                            String.format("%.2f", -change) + " BYN", Alert.AlertType.WARNING);
                    return;
                }
            } else {
                change = 0.0;
            }

            // Обновляем UI проверки
            Platform.runLater(() -> {
                lblCheckTotal.setText(String.format("%.2f BYN", totalAmount));
                lblCheckCashIn.setText(String.format("%.2f BYN", cashIn));
                lblCheckBonus.setText(String.format("%.2f BYN", bonusSpend));
                lblCheckChange.setText(String.format("%.2f BYN", change));
            });

            statusLabel.setText("✅ Расчет выполнен. Готово к оплате.");

        } catch (NumberFormatException e) {
            showAlert("Введите корректные числовые значения", Alert.AlertType.ERROR);
        }
    }

    private void updateTotal() {
        if (currentFuel == null || currentPrice == 0) return;

        // Пересчитываем значения если есть ввод
        if (!tfLiters.getText().isEmpty()) {
            try {
                liters = Double.parseDouble(tfLiters.getText().replace(",", "."));
            } catch (NumberFormatException e) {
                liters = 0.0;
            }
        }

        if (!tfAmount.getText().isEmpty()) {
            try {
                amount = Double.parseDouble(tfAmount.getText().replace(",", "."));
            } catch (NumberFormatException e) {
                amount = 0.0;
            }
        }

        // Рассчитываем общую сумму
        totalAmount = liters * currentPrice;

        Platform.runLater(() -> {
            lblTotalFuelType.setText(currentFuel.get("name").getAsString());
            lblTotalPrice.setText(String.format("%.2f BYN/л", currentPrice));
            lblTotalLiters.setText(String.format("%.2f л", liters));
            lblTotalAmount.setText(String.format("%.2f BYN", totalAmount));
        });
    }

    private void processPayment(String paymentMethod) {
        // Проверяем обязательные поля
        if (currentFuel == null) {
            showAlert("Выберите тип топлива", Alert.AlertType.WARNING);
            return;
        }

        if (liters <= 0 || totalAmount <= 0) {
            showAlert("Введите корректное количество топлива", Alert.AlertType.WARNING);
            return;
        }

        if (selectedNozzle == 0) {
            showAlert("Выберите колонку", Alert.AlertType.WARNING);
            return;
        }

        if (paymentMethod.equals("Наличные") && cashIn <= 0) {
            showAlert("Введите сумму внесенных наличных", Alert.AlertType.WARNING);
            return;
        }

        if (paymentMethod.equals("Наличные") && change < 0) {
            showAlert("Недостаточно внесенных средств", Alert.AlertType.WARNING);
            return;
        }

        // Создаем объект транзакции
        JsonObject transaction = new JsonObject();
        transaction.addProperty("fuel_id", currentFuel.get("id").getAsInt());
        transaction.addProperty("fuel_type", currentFuel.get("name").getAsString());
        transaction.addProperty("azs_id", azsId);
        transaction.addProperty("user_id", userId); // 0 для гостя
        transaction.addProperty("nozzle", selectedNozzle);
        transaction.addProperty("liters", liters);
        transaction.addProperty("price_per_liter", currentPrice);
        transaction.addProperty("total_amount", totalAmount);
        transaction.addProperty("cash_in", cashIn);
        transaction.addProperty("change", change);
        transaction.addProperty("bonus_spent", bonusSpend);
        transaction.addProperty("payment_method", paymentMethod);
        transaction.addProperty("status", "Успешно");
        transaction.addProperty("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        statusLabel.setText("⏳ Сохранение транзакции...");

        // Отправляем транзакцию на сервер
        saveTransaction(transaction);
    }

    private void saveTransaction(JsonObject transaction) {
        // Создаем финальные копии переменных для использования в лямбда-выражениях
        final double finalTotalAmount = totalAmount;
        final double finalLiters = liters;
        final int finalAzsId = azsId;
        final int finalSelectedNozzle = selectedNozzle;
        final double finalBonusSpend = bonusSpend;
        final int finalUserId = userId;

        System.out.println("📤 Отправка транзакции на сервер:");
        System.out.println("   Топливо: " + transaction.get("fuel_type").getAsString());
        System.out.println("   Литры: " + transaction.get("liters").getAsDouble());
        System.out.println("   Сумма: " + transaction.get("total_amount").getAsDouble());
        System.out.println("   АЗС ID: " + transaction.get("azs_id").getAsInt());
        System.out.println("   Колонка: " + transaction.get("nozzle").getAsInt());
        System.out.println("   Пользователь ID: " + transaction.get("user_id").getAsInt());

        ApiClient.createTransaction(transaction).thenAccept(result -> {
            Platform.runLater(() -> {
                System.out.println("📥 Ответ от сервера: " + result.toString());

                if (result.get("success").getAsBoolean()) {
                    // Обновляем статус колонки на "busy"
                    updateNozzleStatus(finalAzsId, finalSelectedNozzle, "busy");

                    // Обновляем статистику пользователя
                    if (finalUserId > 0) {
                        updateUserStats(finalUserId, finalBonusSpend, finalTotalAmount, finalLiters);
                    }

                    showAlert("Транзакция успешно сохранена!", Alert.AlertType.INFORMATION);
                    statusLabel.setText("✅ Транзакция успешно завершена!");

                    // Обновляем статистику в UserSession
                    UserSession.setTodaysTotal(UserSession.getTodaysTotal() + finalTotalAmount);
                    UserSession.setTodaysTransactions(UserSession.getTodaysTransactions() + 1);
                    UserSession.setTodaysLiters(UserSession.getTodaysLiters() + finalLiters);

                    // Сбрасываем форму
                    resetForm();

                } else {
                    String errorMessage = result.get("message").getAsString();
                    System.err.println("❌ Ошибка сохранения транзакции: " + errorMessage);
                    showAlert("Ошибка сохранения транзакции: " + errorMessage, Alert.AlertType.ERROR);
                    statusLabel.setText("❌ Ошибка сохранения транзакции");
                }
            });
        });
    }

    private void updateNozzleStatus(int azsId, int nozzleNumber, String newStatus) {
        ApiClient.updateNozzleStatus(azsId, nozzleNumber, newStatus).thenAccept(response -> {
            Platform.runLater(() -> {
                if (response.get("success").getAsBoolean()) {
                    System.out.println("Статус колонки обновлен на: " + newStatus);

                    // Через 3 минуты возвращаем статус "active" (имитация заправки)
                    if (newStatus.equals("busy")) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(180000); // 3 минуты
                                Platform.runLater(() -> {
                                    ApiClient.updateNozzleStatus(azsId, nozzleNumber, "active")
                                            .thenAccept(res -> {
                                                if (res.get("success").getAsBoolean()) {
                                                    loadNozzleStatus(); // Обновляем UI
                                                    System.out.println("Колонка " + nozzleNumber + " снова доступна");
                                                }
                                            });
                                });
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            });
        });
    }

    private void updateUserStats(int userId, double bonusSpent, double totalAmount, double liters) {
        // Создаем URL для обновления баланса пользователя
        String url = ApiClient.getServerUrl() + "/api/users/" + userId + "/update-balance";

        // Рассчитываем новые бонусы (например, 1% от суммы)
        final double bonusEarned = totalAmount * 0.01;
        final double newBalance = userBonusBalance - bonusSpent + bonusEarned;

        CompletableFuture.runAsync(() -> {
            try {
                // Создаем JSON объект с данными для обновления
                JsonObject updateData = new JsonObject();
                updateData.addProperty("balance", newBalance);
                updateData.addProperty("bonus_spent", bonusSpent);
                updateData.addProperty("bonus_earned", bonusEarned);
                updateData.addProperty("total_spent_increment", totalAmount);
                updateData.addProperty("total_liters_increment", liters);

                // Отправляем запрос на обновление
                URL apiUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                com.google.gson.Gson gson = new com.google.gson.Gson();
                String jsonInput = gson.toJson(updateData);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInput.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("Пользователь ID " + userId +
                            ": баланс обновлен на " + newBalance + " BYN");
                } else {
                    System.err.println("Ошибка обновления баланса пользователя: " + responseCode);
                }
            } catch (Exception e) {
                System.err.println("Ошибка обновления статистики пользователя: " + e.getMessage());
            }
        });
    }

    private void resetForm() {
        // Сбрасываем все поля
        tfAmount.clear();
        tfLiters.clear();
        tfPhone.clear();
        tfCashIn.clear();
        tfBonusSpend.clear();

        // Сбрасываем выбор колонки
        nozzleGroup.selectToggle(null);
        selectedNozzle = 0;

        // Сбрасываем клиента
        lblClientName.setText("Гость");
        lblClientInfo.setText("");
        lblBonusBalance.setText("");
        currentUser = null;
        userId = 0;
        userBonusBalance = 0.0;

        // Сбрасываем расчет
        cashIn = 0.0;
        bonusSpend = 0.0;
        change = 0.0;
        lblCheckTotal.setText("0.00 BYN");
        lblCheckCashIn.setText("0.00 BYN");
        lblCheckBonus.setText("0.00 BYN");
        lblCheckChange.setText("0.00 BYN");

        // Сбрасываем количество
        liters = 0.0;
        amount = 0.0;
        totalAmount = 0.0;

        // Выбираем АИ-95 по умолчанию
        rbFuel95.setSelected(true);
        updateTotal();

        // Обновляем статус колонок
        loadNozzleStatus();
    }

    private void updateServerStatus() {
        ApiClient.checkServerStatus().thenAccept(online -> {
            Platform.runLater(() -> {
                if (online) {
                    lblServerStatus.setText("🟢 Online");
                    lblServerStatus.setStyle("-fx-text-fill: #2ecc71;");
                } else {
                    lblServerStatus.setText("🔴 Offline");
                    lblServerStatus.setStyle("-fx-text-fill: #e74c3c;");
                }
            });
        });
    }

    private void showAlert(String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle("Информация");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    private void handleExit() {
        // Закрываем окно
        javafx.stage.Stage stage = (javafx.stage.Stage) btnExit.getScene().getWindow();
        stage.close();
    }
}