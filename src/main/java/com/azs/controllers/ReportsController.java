package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import com.azs.ApiClient;
import com.azs.model.UserSession;
import com.azs.export.ExcelExporter;
import com.azs.export.HtmlExporter;
import com.google.gson.JsonObject;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    @FXML private Button exportHtmlButton;

    @FXML private Label totalRevenueLabel;
    @FXML private Label totalLitersLabel;
    @FXML private Label totalSalesCountLabel;

    @FXML private Label cashRevenueLabel;
    @FXML private Label cardRevenueLabel;
    @FXML private Label averageSaleLabel;
    @FXML private Label mostPopularFuelLabel;

    private int azsId;
    private JsonObject currentReportData;
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
        exportHtmlButton.setOnAction(e -> exportToHtml());
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
                System.out.println("Получен ответ от сервера: " + response);

                if (response.has("success") && response.get("success").getAsBoolean()) {
                    currentReportData = response;
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
                System.err.println("Исключение при загрузке отчета: " + e.getMessage());
                e.printStackTrace();
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
                cashRevenueLabel.setText(String.format("💵 Наличные: %.2f BYN", cashRevenue));
            }

            if (reportData.has("card_revenue")) {
                double cardRevenue = reportData.get("card_revenue").getAsDouble();
                cardRevenueLabel.setText(String.format("💳 Безналичные: %.2f BYN", cardRevenue));
            }

            if (reportData.has("average_sale")) {
                double averageSale = reportData.get("average_sale").getAsDouble();
                averageSaleLabel.setText(String.format("🧾 Средний чек: %.2f BYN", averageSale));
            }

            if (reportData.has("most_popular_fuel")) {
                String popularFuel = reportData.get("most_popular_fuel").getAsString();
                mostPopularFuelLabel.setText("🏆 Популярное: " + popularFuel);
            }

            System.out.println("✅ Отчет обновлен:");
            System.out.println("  Выручка: " + totalRevenue + " BYN");
            System.out.println("  Литров: " + totalLiters + " л");
            System.out.println("  Транзакций: " + totalTransactions);

        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга данных отчета: " + e.getMessage());
            e.printStackTrace();
            setDefaultValues();
        }
    }

    private void setDefaultValues() {
        totalRevenueLabel.setText("0.00 BYN");
        totalLitersLabel.setText("0.00 л");
        totalSalesCountLabel.setText("0");

        if (cashRevenueLabel != null) cashRevenueLabel.setText("💵 Наличные: 0.00 BYN");
        if (cardRevenueLabel != null) cardRevenueLabel.setText("💳 Безналичные: 0.00 BYN");
        if (averageSaleLabel != null) averageSaleLabel.setText("🧾 Средний чек: 0.00 BYN");
        if (mostPopularFuelLabel != null) mostPopularFuelLabel.setText("🏆 Популярное: —");
    }

    private void exportToExcel() {
        if (currentReportData == null) {
            showError("Ошибка", "Сначала сформируйте отчет");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить отчет в Excel");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Генерируем имя файла по умолчанию
        LocalDate startDate = reportStartDate.getValue();
        LocalDate endDate = reportEndDate.getValue();
        String defaultFileName = String.format("Отчет_АЗС_%d_%s_%s.xlsx",
                azsId,
                startDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                endDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        );
        fileChooser.setInitialFileName(defaultFileName);

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                ExcelExporter.exportReport(currentReportData, file, startDate, endDate, UserSession.getAzsName());
                showInfo("Успех", "Отчет успешно экспортирован в Excel!\n" +
                        "Файл: " + file.getAbsolutePath() + "\n\n" +
                        "Откройте файл в Microsoft Excel или другом табличном редакторе.");
            } catch (Exception e) {
                showError("Ошибка экспорта", "Не удалось экспортировать отчет: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void exportToHtml() {
        if (currentReportData == null) {
            showError("Ошибка", "Сначала сформируйте отчет");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить отчет как HTML");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("HTML Files", "*.html"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        LocalDate startDate = reportStartDate.getValue();
        LocalDate endDate = reportEndDate.getValue();
        String defaultFileName = String.format("Отчет_АЗС_%d_%s_%s.html",
                azsId,
                startDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                endDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        );
        fileChooser.setInitialFileName(defaultFileName);

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                HtmlExporter.exportReport(currentReportData, file, startDate, endDate, UserSession.getAzsName());
                showInfo("Успех", "Отчет успешно экспортирован в HTML.\n" +
                        "Файл: " + file.getAbsolutePath() + "\n\n" +
                        "Вы можете:\n" +
                        "1. Открыть файл в браузере (двойной клик)\n" +
                        "2. Нажать кнопку 'Печать' в правом верхнем углу\n" +
                        "3. Сохранить как PDF из диалога печати браузера");
            } catch (Exception e) {
                showError("Ошибка экспорта", "Не удалось экспортировать отчет: " + e.getMessage());
                e.printStackTrace();
            }
        }
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