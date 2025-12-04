package com.azs.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class NozzlesController {
    @FXML private Label nozzle1Status;
    @FXML private Label nozzle2Status;
    @FXML private Label nozzle3Status;
    @FXML private Label nozzle4Status;

    @FXML private Button nozzle1QrButton;
    @FXML private Button nozzle2QrButton;
    @FXML private Button nozzle3QrButton;
    @FXML private Button nozzle4QrButton;

    @FXML private Button nozzle1DeactivateButton;
    @FXML private Button nozzle2DeactivateButton;
    @FXML private Button nozzle3ActivateButton;
    @FXML private Button nozzle4DeactivateButton;

    @FXML private Button addNozzleButton;

    @FXML
    private void initialize() {
        setupButtonActions();
    }

    private void setupButtonActions() {
        nozzle1QrButton.setOnAction(e -> showQrCode(1));
        nozzle2QrButton.setOnAction(e -> showQrCode(2));
        nozzle3QrButton.setOnAction(e -> showQrCode(3));
        nozzle4QrButton.setOnAction(e -> showQrCode(4));

        nozzle1DeactivateButton.setOnAction(e -> toggleNozzleStatus(1));
        nozzle2DeactivateButton.setOnAction(e -> toggleNozzleStatus(2));
        nozzle3ActivateButton.setOnAction(e -> toggleNozzleStatus(3));
        nozzle4DeactivateButton.setOnAction(e -> toggleNozzleStatus(4));

        addNozzleButton.setOnAction(e -> addNewNozzle());
    }

    private void showQrCode(int nozzleNumber) {
        System.out.println("Показать QR-код для колонки " + nozzleNumber);
        // Здесь будет логика показа QR-кода
    }

    private void toggleNozzleStatus(int nozzleNumber) {
        System.out.println("Изменить статус колонки " + nozzleNumber);
        // Здесь будет логика активации/деактивации колонки
    }

    private void addNewNozzle() {
        System.out.println("Добавить новую колонку");
        // Здесь будет логика добавления новой колонки
    }
}