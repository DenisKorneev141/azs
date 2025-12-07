package com.azs.controllers;

import com.azs.ApiClient;
import com.azs.model.Transaction;
import com.azs.model.UserSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

public class SalesController {
    // TableView и колонки
    @FXML private TableView<Transaction> salesTable;
    @FXML private TableColumn<Transaction, String> timeColumn;
    @FXML private TableColumn<Transaction, String> fuelColumn;
    @FXML private TableColumn<Transaction, String> litersColumn;
    @FXML private TableColumn<Transaction, String> amountColumn;
    @FXML private TableColumn<Transaction, String> paymentColumn;
    @FXML private TableColumn<Transaction, String> statusColumn;

    // Элементы фильтрации
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button filterButton;
    @FXML private Button resetFilterButton;

    // Кнопки отчетов
    @FXML private Button todayReportButton;
    @FXML private Button weekReportButton;
    @FXML private Button monthReportButton;
    @FXML private Button newSaleButton;
    @FXML private Button printSelectedButton;
    @FXML private Button generateReportButton;

    // Статистика
    @FXML private Label totalSalesLabel;
    @FXML private Label totalRevenueLabel;
    @FXML private Label totalLitersLabel;

    private ObservableList<Transaction> transactions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        System.out.println("✅ SalesController инициализирован");
        System.out.println("АЗС ID: " + UserSession.getAzsId());

        // Настройка таблицы с проверкой на null
        try {
            setupTableColumns();
        } catch (NullPointerException e) {
            System.err.println("❌ Ошибка настройки таблицы: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Загрузка данных
        loadSalesData();

        // Настройка кнопок
        setupButtonActions();
    }

    private void setupTableColumns() {
        System.out.println("🔄 Настройка колонок таблицы...");

        // Проверяем, что все колонки найдены
        if (timeColumn == null) System.err.println("❌ timeColumn is null");
        if (fuelColumn == null) System.err.println("❌ fuelColumn is null");
        if (litersColumn == null) System.err.println("❌ litersColumn is null");
        if (amountColumn == null) System.err.println("❌ amountColumn is null");
        if (paymentColumn == null) System.err.println("❌ paymentColumn is null");
        if (statusColumn == null) System.err.println("❌ statusColumn is null");

        // Настраиваем колонки таблицы
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("formattedTime"));
        fuelColumn.setCellValueFactory(new PropertyValueFactory<>("fuelType"));
        litersColumn.setCellValueFactory(new PropertyValueFactory<>("formattedLiters"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("formattedAmount"));
        paymentColumn.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Устанавливаем данные в таблицу
        salesTable.setItems(transactions);

        System.out.println("✅ Колонки таблицы настроены");
    }

    private void loadSalesData() {
        System.out.println("=== Загрузка данных в SalesController ===");

        // Загрузка статистики
        int transactionsAmount = UserSession.getTodaysTransactions();
        Double cashAmount = UserSession.getTodaysTotal();
        Double totalLiters = UserSession.getTodaysLiters();

        System.out.println("📊 Статистика из UserSession:");
        System.out.println("  Транзакций: " + transactionsAmount);
        System.out.println("  Сумма: " + cashAmount);
        System.out.println("  Литров: " + totalLiters);

        if (totalSalesLabel != null) {
            totalSalesLabel.setText(transactionsAmount + " транзакций");
            System.out.println("✅ Установлен totalSalesLabel: " + transactionsAmount);
        } else {
            System.err.println("❌ totalSalesLabel is null");
        }

        if (totalRevenueLabel != null) {
            totalRevenueLabel.setText(String.format("%,.2f BYN", cashAmount));
            System.out.println("✅ Установлен totalRevenueLabel: " + cashAmount);
        } else {
            System.err.println("❌ totalRevenueLabel is null");
        }

        if (totalLitersLabel != null) {
            totalLitersLabel.setText(String.format("%.1f л", totalLiters));
            System.out.println("✅ Установлен totalLitersLabel: " + totalLiters);
        } else {
            System.err.println("❌ totalLitersLabel is null");
        }

        // Загрузка реальных транзакций из базы данных
        loadRealTransactions();
    }

    private void loadRealTransactions() {
        System.out.println("🔄 Загрузка реальных транзакций из БД...");

        int azsId = UserSession.getAzsId();
        if (azsId <= 0) {
            System.err.println("❌ Ошибка: неверный azsId: " + azsId);
            loadTestTransactionsAsFallback();
            return;
        }

        CompletableFuture<JsonObject> future = ApiClient.getRecentTransactions(azsId, 50);
        future.thenAccept(response -> {
            javafx.application.Platform.runLater(() -> {
                try {
                    if (response != null && response.has("success") && response.get("success").getAsBoolean()) {
                        if (response.has("data")) {
                            JsonArray transactionsData = response.getAsJsonArray("data");
                            System.out.println("✅ Получено " + transactionsData.size() + " транзакций с сервера");

                            // Очищаем старые данные
                            transactions.clear();

                            // Добавляем новые данные
                            for (JsonElement element : transactionsData) {
                                JsonObject transJson = element.getAsJsonObject();
                                Transaction transaction = createTransactionFromJson(transJson);
                                if (transaction != null) {
                                    transactions.add(transaction);
                                }
                            }

                            System.out.println("✅ Загружено реальных транзакций: " + transactions.size());
                            System.out.println("✅ Таблица обновлена, строк: " + salesTable.getItems().size());

                            // Обновляем статистику если есть
                            updateStatisticsFromTransactions();
                        }
                    } else {
                        String errorMsg = "Ошибка загрузки данных";
                        if (response != null && response.has("message")) {
                            errorMsg = response.get("message").getAsString();
                        }
                        System.err.println("❌ " + errorMsg);
                        showAlert("Ошибка", errorMsg, Alert.AlertType.ERROR);
                        loadTestTransactionsAsFallback();
                    }
                } catch (Exception e) {
                    System.err.println("❌ Ошибка обработки данных: " + e.getMessage());
                    e.printStackTrace();
                    loadTestTransactionsAsFallback();
                }
            });
        }).exceptionally(e -> {
            javafx.application.Platform.runLater(() -> {
                System.err.println("❌ Исключение при загрузке: " + e.getMessage());
                e.printStackTrace();
                loadTestTransactionsAsFallback();
            });
            return null;
        });
    }

    private Transaction createTransactionFromJson(JsonObject json) {
        try {
            int id = json.get("id").getAsInt();

            // Парсим дату
            LocalDateTime time;
            if (json.has("time") && !json.get("time").getAsString().equals("Не указано")) {
                String timeStr = json.get("time").getAsString();
                try {
                    time = LocalDateTime.parse(timeStr,
                            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                } catch (DateTimeParseException e) {
                    time = LocalDateTime.now();
                }
            } else {
                time = LocalDateTime.now();
            }

            String fuelType = json.has("fuelType") ?
                    json.get("fuelType").getAsString() : "Не указано";
            double liters = json.has("liters") ?
                    json.get("liters").getAsDouble() : 0.0;
            double amount = json.has("amount") ?
                    json.get("amount").getAsDouble() : 0.0;
            String paymentMethod = json.has("paymentMethod") ?
                    json.get("paymentMethod").getAsString() : "Не указано";
            String status = json.has("status") ?
                    json.get("status").getAsString() : "Неизвестно";

            return new Transaction(id, time, fuelType, liters, amount, paymentMethod, status);
        } catch (Exception e) {
            System.err.println("❌ Ошибка создания транзакции из JSON: " + e.getMessage());
            return null;
        }
    }

    private void updateStatisticsFromTransactions() {
        double totalAmount = 0;
        double totalLiters = 0;
        int count = transactions.size();

        for (Transaction transaction : transactions) {
            totalAmount += transaction.getAmount();
            totalLiters += transaction.getLiters();
        }

        if (totalSalesLabel != null) {
            totalSalesLabel.setText(count + " транзакций");
        }

        if (totalRevenueLabel != null) {
            totalRevenueLabel.setText(String.format("%,.2f BYN", totalAmount));
        }

        if (totalLitersLabel != null) {
            totalLitersLabel.setText(String.format("%.1f л", totalLiters));
        }

        System.out.println("📊 Обновленная статистика из транзакций:");
        System.out.println("  Транзакций: " + count);
        System.out.println("  Сумма: " + totalAmount);
        System.out.println("  Литров: " + totalLiters);
    }

    private void loadTestTransactionsAsFallback() {
        System.out.println("⚠️ Загрузка тестовых транзакций как резервного варианта...");

        transactions.clear();

        // Тестовые данные
        LocalDateTime now = LocalDateTime.now();

        transactions.add(new Transaction(1, now.minusHours(2), "АИ-95", 45.5, 2850.75, "Карта", "Успешно"));
        transactions.add(new Transaction(2, now.minusHours(1), "ДТ", 32.0, 1920.00, "Наличные", "Успешно"));
        transactions.add(new Transaction(3, now.minusMinutes(30), "АИ-92", 20.0, 1100.00, "Карта", "Успешно"));
        transactions.add(new Transaction(4, now.minusMinutes(15), "АИ-95", 15.5, 970.25, "Наличные", "Успешно"));
        transactions.add(new Transaction(5, now.minusMinutes(5), "АИ-98", 25.0, 1750.00, "Карта", "Успешно"));

        System.out.println("✅ Загружено тестовых транзакций: " + transactions.size());
        updateStatisticsFromTransactions();
    }

    private void setupButtonActions() {
        System.out.println("🔄 Настройка действий кнопок...");

        // Безопасная настройка кнопок
        if (filterButton != null) {
            filterButton.setOnAction(e -> applyFilter());
        }
        if (resetFilterButton != null) {
            resetFilterButton.setOnAction(e -> resetFilter());
        }
        if (todayReportButton != null) {
            todayReportButton.setOnAction(e -> generateTodayReport());
        }
        if (weekReportButton != null) {
            weekReportButton.setOnAction(e -> generateWeekReport());
        }
        if (monthReportButton != null) {
            monthReportButton.setOnAction(e -> generateMonthReport());
        }
        if (newSaleButton != null) {
            newSaleButton.setOnAction(e -> createNewSale());
        }
        if (printSelectedButton != null) {
            printSelectedButton.setOnAction(e -> printReceipt());
        }
        if (generateReportButton != null) {
            generateReportButton.setOnAction(e -> refreshData());
        }

        System.out.println("✅ Действия кнопок настроены");
    }

    private void applyFilter() {
        System.out.println("🔍 Применить фильтр");
        // TODO: Фильтрация по датам с реальными данными
        showAlert("Внимание", "Фильтрация по датам будет реализована в следующем обновлении",
                Alert.AlertType.INFORMATION);
    }

    private void resetFilter() {
        System.out.println("🔄 Сбросить фильтр");
        loadRealTransactions();
        if (startDatePicker != null) startDatePicker.setValue(null);
        if (endDatePicker != null) endDatePicker.setValue(null);
    }

    private void generateTodayReport() {
        System.out.println("📅 Отчет за сегодня");
        // Можно обновить только сегодняшние транзакции
        refreshData();
    }

    private void generateWeekReport() {
        System.out.println("📆 Отчет за неделю");
        showAlert("Отчет", "Отчет за неделю будет сгенерирован", Alert.AlertType.INFORMATION);
    }

    private void generateMonthReport() {
        System.out.println("🗓️ Отчет за месяц");
        showAlert("Отчет", "Отчет за месяц будет сгенерирован", Alert.AlertType.INFORMATION);
    }

    private void createNewSale() {
        System.out.println("➕ Новая продажа");
        try {
            Stage currentStage = (Stage) newSaleButton.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/new_transaction.fxml"));
            Parent root = loader.load();

            Stage mainStage = new Stage();
            mainStage.setTitle("Новая транзакция");
            mainStage.setScene(new Scene(root, 1920, 1000));
            mainStage.show();

            //currentStage.close();

        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось открыть главное окно: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void printReceipt() {
        Transaction selected = salesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            System.out.println("🖨️ Печать чека для транзакции ID: " + selected.getId());
            showAlert("Печать чека",
                    "Чек для транзакции #" + selected.getId() + " будет распечатан",
                    Alert.AlertType.INFORMATION);
        } else {
            showAlert("Ошибка", "Выберите транзакцию для печати", Alert.AlertType.WARNING);
        }
    }

    private void refreshData() {
        System.out.println("🔄 Обновление данных...");
        loadRealTransactions();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Метод для принудительного обновления данных (можно вызвать извне)
    public void refresh() {
        loadRealTransactions();
    }
}