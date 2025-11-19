package com.azs;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class LoginOperator extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);

        primaryStage.setTitle("Система управления АЗС Phaeton");
        primaryStage.setFullScreen(true);
        //primaryStage.setFullScreenExitHint("");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);

        primaryStage.show();
    }

    public static void main(String[] args){
        launch(args);
    }
}
