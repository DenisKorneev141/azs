package com.azs;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField serverAddressField;

    @FXML
    private Button connectButton;

    private Stage stage;

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

        // Устанавливаем адрес сервера
        ApiClient.setServerUrl(serverAddress);

        // Проверяем соединение асинхронно
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

                        if (response.get("success").getAsBoolean()) {
                            String role = response.get("role").getAsString();
                            String welcomeMessage = response.get("message").getAsString();

                            showAlert("Успех",
                                    welcomeMessage + "\n" +
                                            "Пользователь: " + username + "\n" +
                                            "Роль: " + role,
                                    AlertType.INFORMATION);

                            UserSession.setCurrentUser(username, role);

                            openMainWindow(role);

                        } else {
                            String errorMessage = response.get("message").getAsString();
                            showAlert("Ошибка авторизации", errorMessage, AlertType.ERROR);
                            passwordField.clear();
                            passwordField.requestFocus();
                        }
                    });
                });
    }

    private void openMainWindow(String role) {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();

            if ("admin".equals(role)) {
                // Окно администратора - нужно будет реализовать
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin_dashboard.fxml"));
                Parent root = loader.load();
                Stage adminStage = new Stage();
                adminStage.setTitle("Панель администратора - АЗС Phaeton");
                adminStage.setScene(new Scene(root, 1200, 800));
                adminStage.show();
            } else {
                // Окно оператора
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/operator_dashboard.fxml"));
                Parent root = loader.load();
                Stage operatorStage = new Stage();
                operatorStage.setTitle("Рабочее место оператора - АЗС Phaeton");
                operatorStage.setScene(new Scene(root, 1000, 700));
                operatorStage.show();
            }

            currentStage.close();

        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось открыть главное окно: " + e.getMessage(), AlertType.ERROR);
        }
    }

    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}