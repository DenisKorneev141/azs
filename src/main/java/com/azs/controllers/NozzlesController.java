package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import com.azs.ApiClient;
import com.azs.model.UserSession;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.util.concurrent.CompletableFuture;

public class NozzlesController {
    @FXML private Label nozzle1StatusLabel;
    @FXML private Label nozzle2StatusLabel;
    @FXML private Label nozzle3StatusLabel;
    @FXML private Label nozzle4StatusLabel;

    @FXML private VBox nozzle1Box;
    @FXML private VBox nozzle2Box;
    @FXML private VBox nozzle3Box;
    @FXML private VBox nozzle4Box;

    @FXML private Button nozzle1QrButton;
    @FXML private Button nozzle2QrButton;
    @FXML private Button nozzle3QrButton;
    @FXML private Button nozzle4QrButton;

    @FXML private Button nozzle1ToggleButton;
    @FXML private Button nozzle2ToggleButton;
    @FXML private Button nozzle3ToggleButton;
    @FXML private Button nozzle4ToggleButton;

    @FXML private Button addNozzleButton;

    private int azsId;
    private JsonObject nozzlesData;

    @FXML
    private void initialize() {
        System.out.println("NozzlesController инициализирован");

        // Получаем ID АЗС из сессии
        azsId = UserSession.getAzsId();
        System.out.println("Загружаем колонки для АЗС ID: " + azsId);
        System.out.println("АЗС: " + UserSession.getAzsName());

        // Инициализируем кнопки QR-кода
        setupQrButtons();

        // Загружаем данные колонок
        loadNozzlesData();
    }

    private void setupQrButtons() {
        nozzle1QrButton.setOnAction(e -> showQrCode(1));
        nozzle2QrButton.setOnAction(e -> showQrCode(2));
        nozzle3QrButton.setOnAction(e -> showQrCode(3));
        nozzle4QrButton.setOnAction(e -> showQrCode(4));

        addNozzleButton.setOnAction(e -> addNewNozzle());
    }

    private void loadNozzlesData() {
        System.out.println("Запрос статусов колонок для АЗС ID: " + azsId);

        CompletableFuture<JsonObject> future = ApiClient.getNozzlesStatus(azsId);

        future.thenAccept(response -> {
            Platform.runLater(() -> {
                System.out.println("Ответ от сервера получен");

                if (response.get("success").getAsBoolean()) {
                    System.out.println("Данные колонок успешно загружены");
                    nozzlesData = response.get("nozzles").getAsJsonObject();
                    updateUI();
                } else {
                    String errorMsg = response.get("message").getAsString();
                    System.err.println("Ошибка загрузки колонок: " + errorMsg);
                    showError("Ошибка загрузки данных колонок", errorMsg);
                    // Показываем дефолтные значения
                    loadDefaultNozzles();
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                System.err.println("Ошибка подключения к серверу: " + e.getMessage());
                showError("Ошибка подключения",
                        "Не удалось подключиться к серверу: " + e.getMessage());
                loadDefaultNozzles();
            });
            return null;
        });
    }

    private void updateUI() {
        if (nozzlesData == null) {
            System.err.println("Нет данных колонок для обновления UI");
            return;
        }

        // Получаем количество колонок на АЗС
        int nozzleCount = nozzlesData.get("nozzle_count").getAsInt();
        System.out.println("Количество колонок на АЗС: " + nozzleCount);

        // Обновляем каждую колонку
        for (int i = 1; i <= 4; i++) {
            updateNozzleUI(i, nozzleCount);
        }
    }

    private void updateNozzleUI(int nozzleNumber, int totalNozzles) {
        String status;
        String columnName = "nozzle_" + nozzleNumber;

        System.out.println("Обновление колонки " + nozzleNumber + ", всего колонок: " + totalNozzles);

        // Проверяем существует ли колонка
        if (nozzleNumber > totalNozzles || !nozzlesData.has(columnName)) {
            status = "not_available";
            System.out.println("Колонка " + nozzleNumber + " недоступна");
        } else {
            status = nozzlesData.get(columnName).getAsString();
            System.out.println("Статус колонки " + nozzleNumber + ": " + status);
        }

        // Находим соответствующие элементы UI
        Label statusLabel = getStatusLabel(nozzleNumber);
        Button qrButton = getQrButton(nozzleNumber);
        Button toggleButton = getToggleButton(nozzleNumber);
        VBox nozzleBox = getNozzleBox(nozzleNumber);

        if (statusLabel == null) {
            System.err.println("Не найден statusLabel для колонки " + nozzleNumber);
            return;
        }
        if (qrButton == null) {
            System.err.println("Не найден qrButton для колонки " + nozzleNumber);
            return;
        }
        if (toggleButton == null) {
            System.err.println("Не найден toggleButton для колонки " + nozzleNumber);
            return;
        }
        if (nozzleBox == null) {
            System.err.println("Не найден nozzleBox для колонки " + nozzleNumber);
            return;
        }

        // Устанавливаем стили и текст в зависимости от статуса
        switch (status) {
            case "active":
                // Колонка активна
                statusLabel.setText("Активна");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 24;");
                toggleButton.setText("Деактивировать");
                toggleButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 6; ");
                toggleButton.setOnAction(e -> toggleNozzleStatus(nozzleNumber, "not_active"));
                break;

            case "not_active":
                // Колонка неактивна
                statusLabel.setText("Неактивна");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 24;");
                toggleButton.setText("Активировать");
                toggleButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 6;");
                toggleButton.setOnAction(e -> toggleNozzleStatus(nozzleNumber, "active"));
                break;

            case "not_available":
            default:
                // Колонка недоступна или не существует
                statusLabel.setText("Не доступна");
                statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
                qrButton.setVisible(false);
                qrButton.setManaged(false);
                toggleButton.setVisible(false);
                toggleButton.setManaged(false);
                return;
        }

        // Показываем элементы для доступных колонок
        qrButton.setVisible(true);
        qrButton.setManaged(true);
        toggleButton.setVisible(true);
        toggleButton.setManaged(true);
        nozzleBox.setVisible(true);
        nozzleBox.setManaged(true);
    }

    private void toggleNozzleStatus(int nozzleNumber, String newStatus) {
        System.out.println("Изменение статуса колонки " + nozzleNumber + " на " + newStatus);

        CompletableFuture<JsonObject> future = ApiClient.updateNozzleStatus(
                azsId, nozzleNumber, newStatus
        );

        future.thenAccept(response -> {
            Platform.runLater(() -> {
                if (response.get("success").getAsBoolean()) {
                    // Обновляем локальные данные и UI
                    nozzlesData.addProperty("nozzle_" + nozzleNumber, newStatus);
                    int totalNozzles = nozzlesData.get("nozzle_count").getAsInt();
                    updateNozzleUI(nozzleNumber, totalNozzles);

                    showSuccess("Статус колонки изменен",
                            "Колонка №" + nozzleNumber + " теперь " +
                                    (newStatus.equals("active") ? "активна" : "неактивна"));
                } else {
                    showError("Ошибка",
                            "Не удалось изменить статус колонки: " +
                                    response.get("message").getAsString());
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                showError("Ошибка подключения",
                        "Не удалось подключиться к серверу: " + e.getMessage());
            });
            return null;
        });
    }

    private void showQrCode(int nozzleNumber) {
        System.out.println("Показать QR-код для колонки " + nozzleNumber);
        // TODO: Реализовать показ QR-кода
        showInfo("QR-код",
                "QR-код для колонки №" + nozzleNumber +
                        "\nАЗС: " + UserSession.getAzsName());
    }

    private void addNewNozzle() {
        System.out.println("Добавить новую колонку");
        // TODO: Реализовать добавление новой колонки
        showInfo("Добавление колонки",
                "Функция добавления новой колонки будет доступна в следующем обновлении");
    }

    // Методы для получения элементов UI
    private Label getStatusLabel(int nozzleNumber) {
        switch (nozzleNumber) {
            case 1: return nozzle1StatusLabel;
            case 2: return nozzle2StatusLabel;
            case 3: return nozzle3StatusLabel;
            case 4: return nozzle4StatusLabel;
            default:
                System.err.println("Неизвестный номер колонки: " + nozzleNumber);
                return null;
        }
    }

    private Button getQrButton(int nozzleNumber) {
        switch (nozzleNumber) {
            case 1: return nozzle1QrButton;
            case 2: return nozzle2QrButton;
            case 3: return nozzle3QrButton;
            case 4: return nozzle4QrButton;
            default:
                System.err.println("Неизвестный номер колонки для QR: " + nozzleNumber);
                return null;
        }
    }

    private Button getToggleButton(int nozzleNumber) {
        switch (nozzleNumber) {
            case 1: return nozzle1ToggleButton;
            case 2: return nozzle2ToggleButton;
            case 3: return nozzle3ToggleButton;
            case 4: return nozzle4ToggleButton;
            default:
                System.err.println("Неизвестный номер колонки для toggle: " + nozzleNumber);
                return null;
        }
    }

    private VBox getNozzleBox(int nozzleNumber) {
        switch (nozzleNumber) {
            case 1: return nozzle1Box;
            case 2: return nozzle2Box;
            case 3: return nozzle3Box;
            case 4: return nozzle4Box;
            default:
                System.err.println("Неизвестный номер колонки для box: " + nozzleNumber);
                return null;
        }
    }

    // Дефолтные данные для отладки
    private void loadDefaultNozzles() {
        System.out.println("Загрузка дефолтных данных колонок");

        JsonObject defaultData = new JsonObject();
        defaultData.addProperty("nozzle_1", "active");
        defaultData.addProperty("nozzle_2", "not_active");
        defaultData.addProperty("nozzle_3", "active");
        defaultData.addProperty("nozzle_4", "not_available");
        defaultData.addProperty("nozzle_count", 3);

        nozzlesData = defaultData;
        updateUI();
    }

    // Вспомогательные методы для показа сообщений
    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
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