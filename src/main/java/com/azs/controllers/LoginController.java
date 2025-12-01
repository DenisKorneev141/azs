package com.azs;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private void initialize() {
        // Инициализация (если нужна)
    }

    @FXML
    private void handleLogin() {
        // Ваша логика авторизации
        System.out.println("Логин: " + usernameField.getText());
        System.out.println("Пароль: " + passwordField.getText());

        // TODO: Добавьте вашу логику проверки
    }
}