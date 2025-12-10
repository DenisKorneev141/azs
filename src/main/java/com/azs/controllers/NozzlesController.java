package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import com.azs.ApiClient;
import com.azs.model.UserSession;
import com.azs.QrCodeUtils;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

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

        if (statusLabel == null || qrButton == null || toggleButton == null || nozzleBox == null) {
            System.err.println("Не найдены элементы UI для колонки " + nozzleNumber);
            return;
        }

        // Устанавливаем стили и текст в зависимости от статуса
        switch (status) {
            case "active":
                // Колонка активна
                statusLabel.setText("Активна");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 24;");
                toggleButton.setText("Деактивировать");
                toggleButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 6;");
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
        System.out.println("🔗 Показать QR-код для колонки " + nozzleNumber);

        // Проверяем доступность колонки
        String columnName = "nozzle_" + nozzleNumber;
        if (nozzlesData == null || !nozzlesData.has(columnName) ||
                "not_available".equals(nozzlesData.get(columnName).getAsString())) {
            showError("QR-код недоступен", "Колонка №" + nozzleNumber + " не доступна");
            return;
        }

        // Получаем данные с сервера
        CompletableFuture<JsonObject> future = ApiClient.getQrCodeData(azsId, nozzleNumber);

        future.thenAccept(response -> {
            Platform.runLater(() -> {
                if (response.get("success").getAsBoolean()) {
                    // Получаем текст для QR-кода
                    String qrText = response.get("qr_text").getAsString();
                    String azsName = response.get("azs_name").getAsString();

                    // Генерируем и показываем QR-код
                    showQrCodeDialog(nozzleNumber, qrText, azsName);
                } else {
                    // Используем локальные данные
                    String azsName = UserSession.getAzsName();
                    String qrText = QrCodeUtils.generateQrText(azsId, nozzleNumber, azsName);
                    showQrCodeDialog(nozzleNumber, qrText, azsName);
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                // Используем локальные данные
                String azsName = UserSession.getAzsName();
                String qrText = QrCodeUtils.generateQrText(azsId, nozzleNumber, azsName);
                showQrCodeDialog(nozzleNumber, qrText, azsName);
            });
            return null;
        });
    }

    private void showQrCodeDialog(int nozzleNumber, String qrText, String azsName) {
        // Создаем простое диалоговое окно
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("QR-код для колонки №" + nozzleNumber);
        alert.setHeaderText("АЗС: " + azsName + " | Колонка: " + nozzleNumber);


        Image qrImage = QrCodeUtils.generateQrCodeImage(qrText, 250);
        ImageView qrImageView = new ImageView(qrImage);
        qrImageView.setFitWidth(250);
        qrImageView.setFitHeight(250);
        qrImageView.setPreserveRatio(true);

        // Простое текстовое описание
        String info = "✅ QR-код готов к сканированию\n\n" +
                "Данные для клиента:\n" +
                "• АЗС: " + azsName + "\n" +
                "• Колонка: №" + nozzleNumber + "\n" +
                "• Время: " +
                new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());

        // Создаем простой layout
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(qrImageView, new Label(info));

        // Добавляем кнопки
        ButtonType copyButton = new ButtonType("Копировать", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(copyButton, closeButton);

        // Устанавливаем контент
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefSize(300, 400);

        // Обработка кнопки "Копировать"
        alert.showAndWait().ifPresent(response -> {
            if (response == copyButton) {
                copyToClipboard(qrText);
                showInfo("Скопировано", "Данные QR-кода скопированы");
            }
        });
    }



    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            System.out.println("✅ Текст скопирован в буфер обмена");
        } catch (Exception e) {
            System.err.println("❌ Ошибка копирования в буфер обмена: " + e.getMessage());
            showError("Ошибка", "Не удалось скопировать текст в буфер обмена");
        }
    }

    private void saveQrCodeToFile(String qrText, int nozzleNumber, String azsName) {
        try {
            // Создаем имя файла
            String filename = String.format("qr_azs%d_nozzle%d_%s.png",
                    azsId, nozzleNumber,
                    new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()));

            // Генерируем и сохраняем НАСТОЯЩИЙ QR-код
            QrCodeUtils.saveQrCodeToFile(qrText, 400, filename);

            showSuccess("QR-код сохранен",
                    "QR-код сохранен в файл: " + filename +
                            "\nФайл находится в папке с программой.");

        } catch (Exception e) {
            System.err.println("❌ Ошибка сохранения QR-кода: " + e.getMessage());
            showError("Ошибка", "Не удалось сохранить QR-код в файл: " + e.getMessage());
        }
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