package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import com.azs.ApiClient;
import com.azs.model.UserSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CompletableFuture;

public class ReportsController {
    @FXML private DatePicker reportStartDate;
    @FXML private DatePicker reportEndDate;

    @FXML private Button generateReportButton;
    @FXML private Button dailyReportButton;
    @FXML private Button weeklyReportButton;
    @FXML private Button monthlyReportButton;
    @FXML private Button yearlyReportButton;

    @FXML private Button exportExcelButton;
    @FXML private Button exportPdfButton;
    @FXML private Button printReportButton;

    @FXML private Label totalRevenueLabel;
    @FXML private Label totalLitersLabel;
    @FXML private Label totalSalesCountLabel;

    @FXML private Label cashRevenueLabel;
    @FXML private Label cardRevenueLabel;
    @FXML private Label averageSaleLabel;
    @FXML private Label mostPopularFuelLabel;

    private int azsId;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    private void initialize() {
        azsId = UserSession.getAzsId();
        System.out.println("ReportsController инициализирован для АЗС ID: " + azsId);

        // Устанавливаем даты по умолчанию (последние 30 дней)
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        reportEndDate.setValue(endDate);
        reportStartDate.setValue(startDate);

        setupButtonActions();

        // Автоматически загружаем отчет за последние 30 дней
        generateReport();
    }

    private void setupButtonActions() {
        generateReportButton.setOnAction(e -> generateReport());
        dailyReportButton.setOnAction(e -> generateDailyReport());
        weeklyReportButton.setOnAction(e -> generateWeeklyReport());
        monthlyReportButton.setOnAction(e -> generateMonthlyReport());
        yearlyReportButton.setOnAction(e -> generateYearlyReport());

        exportExcelButton.setOnAction(e -> exportToExcel());
        exportPdfButton.setOnAction(e -> exportToPdf());
        printReportButton.setOnAction(e -> printReport());
    }

    private void generateReport() {
        LocalDate startDate = reportStartDate.getValue();
        LocalDate endDate = reportEndDate.getValue();

        if (startDate == null || endDate == null) {
            showError("Ошибка", "Выберите начальную и конечную даты");
            return;
        }

        if (startDate.isAfter(endDate)) {
            showError("Ошибка", "Начальная дата не может быть позже конечной");
            return;
        }

        String startDateStr = startDate.format(DATE_FORMATTER);
        String endDateStr = endDate.format(DATE_FORMATTER);

        System.out.println("Формирование отчета с " + startDateStr + " по " + endDateStr);

        loadReportData(startDateStr, endDateStr);
    }

    private void generateDailyReport() {
        LocalDate today = LocalDate.now();
        reportStartDate.setValue(today);
        reportEndDate.setValue(today);
        generateReport();
    }

    private void generateWeeklyReport() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);
        reportStartDate.setValue(startDate);
        reportEndDate.setValue(endDate);
        generateReport();
    }

    private void generateMonthlyReport() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.withDayOfMonth(1);
        reportStartDate.setValue(startDate);
        reportEndDate.setValue(endDate);
        generateReport();
    }

    private void generateYearlyReport() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.withDayOfYear(1);
        reportStartDate.setValue(startDate);
        reportEndDate.setValue(endDate);
        generateReport();
    }

    private void loadReportData(String startDate, String endDate) {
        // Загружаем данные о транзакциях
        CompletableFuture<JsonObject> future = ApiClient.getReportData(azsId, startDate, endDate);

        future.thenAccept(response -> {
            javafx.application.Platform.runLater(() -> {
                if (response.has("success") && response.get("success").getAsBoolean()) {
                    updateReportUI(response);
                } else {
                    String errorMsg = response.has("message") ?
                            response.get("message").getAsString() : "Неизвестная ошибка";
                    showError("Ошибка загрузки отчета", errorMsg);
                    setDefaultValues();
                }
            });
        }).exceptionally(e -> {
            javafx.application.Platform.runLater(() -> {
                showError("Ошибка", "Не удалось загрузить данные: " + e.getMessage());
                setDefaultValues();
            });
            return null;
        });
    }

    private void updateReportUI(JsonObject reportData) {
        try {
            // Основная статистика
            double totalRevenue = reportData.get("total_revenue").getAsDouble();
            double totalLiters = reportData.get("total_liters").getAsDouble();
            int totalTransactions = reportData.get("total_transactions").getAsInt();

            totalRevenueLabel.setText(String.format("%.2f BYN", totalRevenue));
            totalLitersLabel.setText(String.format("%.1f л", totalLiters));
            totalSalesCountLabel.setText(String.valueOf(totalTransactions));

            // Детализированная статистика
            if (reportData.has("cash_revenue")) {
                double cashRevenue = reportData.get("cash_revenue").getAsDouble();
                cashRevenueLabel.setText(String.format("Наличные: %.2f BYN", cashRevenue));
            }

            if (reportData.has("card_revenue")) {
                double cardRevenue = reportData.get("card_revenue").getAsDouble();
                cardRevenueLabel.setText(String.format("Безнал: %.2f BYN", cardRevenue));
            }

            if (reportData.has("average_sale")) {
                double averageSale = reportData.get("average_sale").getAsDouble();
                averageSaleLabel.setText(String.format("Ср. чек: %.2f BYN", averageSale));
            }

            if (reportData.has("most_popular_fuel")) {
                String popularFuel = reportData.get("most_popular_fuel").getAsString();
                mostPopularFuelLabel.setText("Популярное: " + popularFuel);
            }

            System.out.println("Отчет обновлен:");
            System.out.println("  Выручка: " + totalRevenue + " BYN");
            System.out.println("  Литров: " + totalLiters + " л");
            System.out.println("  Транзакций: " + totalTransactions);

        } catch (Exception e) {
            System.err.println("Ошибка парсинга данных отчета: " + e.getMessage());
            setDefaultValues();
        }
    }

    private void setDefaultValues() {
        totalRevenueLabel.setText("0 BYN");
        totalLitersLabel.setText("0 л");
        totalSalesCountLabel.setText("0");

        if (cashRevenueLabel != null) cashRevenueLabel.setText("Наличные: 0 BYN");
        if (cardRevenueLabel != null) cardRevenueLabel.setText("Безнал: 0 BYN");
        if (averageSaleLabel != null) averageSaleLabel.setText("Ср. чек: 0 BYN");
        if (mostPopularFuelLabel != null) mostPopularFuelLabel.setText("Популярное: —");
    }

    private void exportToExcel() {
        showInfo("Экспорт в Excel", "Функция экспорта в Excel будет реализована в следующем обновлении");
        // TODO: Реализовать экспорт в Excel
    }

    private void exportToPdf() {
        showInfo("Экспорт в PDF", "Функция экспорта в PDF будет реализована в следующем обновлении");
        // TODO: Реализовать экспорт в PDF
    }

    private void printReport() {
        showInfo("Печать отчета", "Функция печати отчета будет реализована в следующем обновлении");
        // TODO: Реализовать печать отчета
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}