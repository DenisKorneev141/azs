package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import com.azs.ApiClient;
import com.azs.model.UserSession;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;
    @FXML private TextField serverAddressField;
    @FXML private Button connectButton;

    @FXML
    private void initialize() {
        serverAddressField.setText("localhost");
        statusLabel.setText("Не подключено");
        statusLabel.setStyle("-fx-text-fill: red;");
        loginButton.setDisable(true);

        serverAddressField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                connectButton.setDisable(false);
            }
        });

        serverAddressField.setOnAction(event -> handleConnect());
        passwordField.setOnAction(event -> handleLogin());
    }

    @FXML
    private void handleConnect() {
        String serverAddress = serverAddressField.getText().trim();

        if (serverAddress.isEmpty()) {
            showAlert("Ошибка", "Введите адрес сервера", AlertType.WARNING);
            return;
        }

        statusLabel.setText("Подключение...");
        statusLabel.setStyle("-fx-text-fill: orange;");
        connectButton.setDisable(true);

        ApiClient.setServerUrl(serverAddress);

        CompletableFuture.supplyAsync(() -> ApiClient.checkConnection())
                .thenAccept(isConnected -> {
                    javafx.application.Platform.runLater(() -> {
                        if (isConnected) {
                            statusLabel.setText("Подключено ✓");
                            statusLabel.setStyle("-fx-text-fill: green;");
                            loginButton.setDisable(false);
                            usernameField.requestFocus();
                            showAlert("Успех", "Соединение с сервером установлено", AlertType.INFORMATION);
                        } else {
                            statusLabel.setText("Ошибка подключения");
                            statusLabel.setStyle("-fx-text-fill: red;");
                            loginButton.setDisable(true);
                            connectButton.setDisable(false);
                            showAlert("Ошибка",
                                    "Не удалось подключиться к серверу:\n" +
                                            ApiClient.getServerUrl() + "\n\n" +
                                            "Проверьте:\n" +
                                            "1. Адрес сервера\n" +
                                            "2. Запущен ли сервер\n" +
                                            "3. Доступ к порту 8080",
                                    AlertType.ERROR);
                        }
                    });
                });
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Ошибка", "Заполните все поля", AlertType.WARNING);
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("Вход...");

        ApiClient.authenticate(username, password)
                .thenAccept(response -> {
                    javafx.application.Platform.runLater(() -> {
                        loginButton.setDisable(false);
                        loginButton.setText("Войти");

                        // ПРОВЕРЯЕМ, ЧТО ОТВЕТ ЕСТЬ И ЕСТЬ ПОЛЕ success
                        if (response == null) {
                            showAlert("Ошибка", "Пустой ответ от сервера", AlertType.ERROR);
                            return;
                        }

                        if (!response.has("success")) {
                            showAlert("Ошибка", "Некорректный ответ от сервера", AlertType.ERROR);
                            return;
                        }

                        if (response.get("success").getAsBoolean()) {
                            // Получаем основные поля с проверкой их наличия
                            String role = response.has("role") ?
                                    response.get("role").getAsString() : "operator";
                            String message = response.has("message") ?
                                    response.get("message").getAsString() : "Авторизация успешна";

                            // БЕЗОПАСНОЕ ПОЛУЧЕНИЕ ДАННЫХ ОПЕРАТОРА
                            String firstName = "Иван";
                            String lastName = "Иванов";
                            String azsName = "АЗС №1 Центральная";
                            int azsId = 1;
                            double todaysTotal = 0.0;
                            int todaysTransactions = 0;
                            double todaysLiters = 0.0;

                            // Проверяем наличие поля user
                            if (response.has("user") && response.get("user").isJsonObject()) {
                                JsonObject userData = response.get("user").getAsJsonObject();

                                // Получаем имя и фамилию
                                if (userData.has("firstName")) {
                                    firstName = userData.get("firstName").getAsString();
                                }
                                if (userData.has("lastName")) {
                                    lastName = userData.get("lastName").getAsString();
                                }

                                // Получаем данные АЗС
                                if (userData.has("azs") && userData.get("azs").isJsonObject()) {
                                    JsonObject azsData = userData.get("azs").getAsJsonObject();
                                    if (azsData.has("name")) {
                                        azsName = azsData.get("name").getAsString();
                                    }
                                    if (azsData.has("id")) {
                                        azsId = azsData.get("id").getAsInt();
                                    }
                                }
                            }

                            // Получаем ВСЮ статистику за сегодня
                            if (response.has("todaysTotal")) {
                                todaysTotal = response.get("todaysTotal").getAsDouble();
                            }
                            if (response.has("todaysTransactions")) {
                                todaysTransactions = response.get("todaysTransactions").getAsInt();
                            }
                            if (response.has("todaysLiters")) {
                                todaysLiters = response.get("todaysLiters").getAsDouble();
                            }

                            // Отладочный вывод
                            System.out.println("📊 Получены данные с сервера:");
                            System.out.println("  todaysTotal: " + todaysTotal);
                            System.out.println("  todaysTransactions: " + todaysTransactions);
                            System.out.println("  todaysLiters: " + todaysLiters);

                            // Сохраняем в UserSession ВСЕ данные (7 параметров!)
                            UserSession.initializeSession(username, role, firstName,
                                    lastName, azsName, azsId,
                                    todaysTotal, todaysTransactions, todaysLiters); // ← ВСЕ 7 параметров!

                            /*showAlert("Успех",
                                    message + "\n" +
                                            "Оператор: " + firstName + " " + lastName + "\n" +
                                            "АЗС: " + azsName + "\n" +
                                            "Статистика за сегодня:\n" +
                                            "• Сумма: " + String.format("%,.2f ₽", todaysTotal) + "\n" +
                                            "• Транзакций: " + todaysTransactions + "\n" +
                                            "• Литров: " + String.format("%.1f л", todaysLiters),
                                    AlertType.INFORMATION);*/

                            openMainWindow();

                        } else {
                            String errorMessage = response.has("message") ?
                                    response.get("message").getAsString() : "Неизвестная ошибка";
                            showAlert("Ошибка авторизации", errorMessage, AlertType.ERROR);
                            passwordField.clear();
                            passwordField.requestFocus();
                        }
                    });
                }).exceptionally(e -> {
                    javafx.application.Platform.runLater(() -> {
                        loginButton.setDisable(false);
                        loginButton.setText("Войти");
                        showAlert("Ошибка", "Ошибка подключения к серверу: " + e.getMessage(), AlertType.ERROR);
                    });
                    return null;
                });
    }

    private void openMainWindow() {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();

            Stage mainStage = new Stage();
            mainStage.setTitle("Рабочее место оператора - АЗС Phaeton");
            mainStage.setScene(new Scene(root, 1920, 1000));
            mainStage.show();

            currentStage.close();

        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось открыть главное окно: " + e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}