package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import com.azs.model.UserSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML private StackPane contentPane;
    @FXML private TabPane mainTabPane;
    @FXML private Tab salesTab;
    @FXML private Tab nozzlesTab;
    @FXML private Tab pricesTab;
    @FXML private Tab reportsTab;

    // Элементы шапки
    @FXML private Label operatorNameLabel;
    @FXML private Label currentAzsLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label cashAmountLabel;
    @FXML private Label serverStatusLabel;
    @FXML private Label totalSalesLabel;
    @FXML private Label totalRevenueLabel;

    private Timeline timeline;
    @FXML
    private void initialize() {
        // Устанавливаем данные оператора в шапку
        updateHeaderInfo();

        // Запускаем обновление времени
        startClock();
        // Загружаем содержимое по умолчанию (первая вкладка)
        loadSalesContent();

        // Обработчики переключения вкладок
        mainTabPane.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> {
                    if (newTab == salesTab) {
                        loadSalesContent();
                    } else if (newTab == nozzlesTab) {
                        loadNozzlesContent();
                    } else if (newTab == pricesTab) {
                        loadPricesContent();
                    } else if (newTab == reportsTab) {
                        loadReportsContent();
                    }
                });
    }
    private void updateHeaderInfo() {
        // Устанавливаем имя оператора
        String fullName = UserSession.getFullName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            operatorNameLabel.setText(fullName);
        }

        // Устанавливаем название АЗС
        String azsName = UserSession.getAzsName();
        if (azsName != null && !azsName.trim().isEmpty()) {
            currentAzsLabel.setText(azsName);
        }

        Double cashAmount = UserSession.getTodaysTotal();
        if(azsName != null){
            cashAmountLabel.setText(String.format("%,.2f BYN", cashAmount));
        }

        // Обновляем время (сразу)
        updateCurrentTime();

        // TODO: Загрузить сумму в кассе с сервера
        //cashAmountLabel.setText(loadCashAmount() + " BYN");
    }

    private void startClock() {
        // Создаем таймер, который обновляет время каждую секунду
        timeline = new Timeline(new KeyFrame(
                Duration.seconds(1),
                event -> updateCurrentTime()
        ));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void updateCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");
        String formattedTime = now.format(formatter);
        currentTimeLabel.setText(formattedTime);
    }
    private void loadSalesContent() {
        try {
            VBox salesContent = FXMLLoader.load(getClass().getResource("/fxml/sales_content.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(salesContent);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить вкладку 'Продажи'");
        }
    }

    private void loadNozzlesContent() {
        try {
            VBox nozzlesContent = FXMLLoader.load(getClass().getResource("/fxml/nozzles_content.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(nozzlesContent);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить вкладку 'Колонки'");
        }
    }

    private void loadPricesContent() {
        try {
            VBox pricesContent = FXMLLoader.load(getClass().getResource("/fxml/prices_content.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(pricesContent);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить вкладку 'Цены'");
        }
    }

    private void loadReportsContent() {
        try {
            VBox reportsContent = FXMLLoader.load(getClass().getResource("/fxml/reports_content.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(reportsContent);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Не удалось загрузить вкладку 'Отчеты'");
        }
    }

    //private String loadCashAmount() {

        //<Double> future = ApiClient.getCashAmount();
        //return String.format("%.2f", amount);

        //return "5 000"; // Временное значение
   // }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleLogout() {
        // Реализация выхода
        com.azs.model.UserSession.clearSession();

        // Закрыть текущее окно
        Stage stage = (Stage) contentPane.getScene().getWindow();
        stage.close();

        // Показать окно входа
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage loginStage = new Stage();
            loginStage.setScene(new javafx.scene.Scene(root));
            loginStage.setTitle("Вход в систему - АЗС Phaeton");
            loginStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}