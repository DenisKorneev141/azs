package com.azs;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerManager{
    private static HttpServer server;
    private static Connection connection;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final int PORT = 8080;

    public static void startServer() {
        if(isRunning.get()){
            System.out.println("Ошибка: сервер уже запущен!");
            return;
        }

        try{
            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            //обработчики

            server.setExecutor(null);
            server.start();
            isRunning.set(true);

            System.out.println("Сервер успешно запущен на порту: " + PORT);
            connectToDatabase();
        } catch (IOException e){
            System.err.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }

    public static void stopServer(){
        if(!isRunning.get()){
            System.out.println("Ошибка: сервер не запущен!");
            return;
        }


        server.stop(0);
        isRunning.set(false);

        System.out.println("Сервер остановлен");


    }

    public static void showStatus(){
        String status = isRunning.get() ? "АКТИВЕН" : "НЕАКТИВЕН";
        System.out.println("Статус сервера: " + status);
        if(isRunning.get()){
            System.out.printf("URL: http://localhost:" + PORT);
        }
    }

    private static void connectToDatabase(){
        try {
            Class.forName("org.postgresql.Driver");

            String url = "jdbc:postgresql://localhost:5432/azs_database";
            String user = "postgres";
            String password = "123456";

            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Подключение к БД установлено");
        } catch (Exception e) {
            System.err.println("Ошибка подключения к БД: " + e.getMessage());
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static String getFuelPrices(){
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name, price FROM fuels ORDER BY id");

            StringBuilder result = new StringBuilder();
            result.append("Актуальные цены на топливо:\n");

            while (rs.next()){
                int id = rs.getInt("id");
                String name = rs.getString("name");
                double price = rs.getDouble("price");
                result.append("ID: ").append(id).append(" | ")
                        .append(name).append(": ").append(price).append(" руб.\n");
            }

            rs.close();
            stmt.close();

            return result.toString();

        } catch (SQLException e) {
            return "Ошибка при получении списка: " + e.getMessage();
        }
    }

    public static String updateFuelPrice(int fuelId, double newPrice){
        try{
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            ResultSet checkRs = stmt.executeQuery("SELECT name FROM fuels WHERE id = " + fuelId);
            if(!checkRs.next()){
                System.out.println("Ошибка: топлива с указанным ID не существует! (Id: " + fuelId + ")");
            }

            String fuelName = checkRs.getString("name");
            checkRs.close();

            String sql = "UPDATE fuels SET price = " + newPrice + " WHERE id = " + fuelId;
            int rowsAffected = stmt.executeUpdate(sql);
            stmt.close();

            if(rowsAffected > 0){
                return "Цена на " + fuelName + " изменена на " + newPrice + " руб.";
            } else{
                return "Ошибка изменения цены!";
            }
        } catch(SQLException e){
            return "Ошибка: " + e.getMessage();
        }
    }

    public static String showAZS(){
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name, address, nozzle_count FROM azs ORDER BY id");

            StringBuilder result = new StringBuilder();
            result.append("Список всех АЗС:\n");

            while (rs.next()){
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String address = rs.getString("address");
                int nozzle_count = rs.getInt("nozzle_count");
                result.append("ID: ").append(id).append(", кол-во колонок: ").append(nozzle_count).append(" | ")
                        .append(name).append(": ").append(address).append("\n");
            }

            rs.close();
            stmt.close();

            return result.toString();

        } catch (SQLException e) {
            return "Ошибка при получении списка: " + e.getMessage();
        }
    }

    public static String newAZS(String name, String address, int nozzle ){
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            String sql = "INSERT INTO azs (name, address, nozzle_count) " +
                    "VALUES ('" + name + "', '" + address + "', " + nozzle + ")";

            int rowsAffected = stmt.executeUpdate(sql);
            stmt.close();

            if (rowsAffected > 0) {
                return name + " по адресу " + address + " успешно добавлена!";
            } else {
                return "Ошибка: не удалось добавить  АЗС!";
            }

        } catch (SQLException e){
            return "Ошибка при добавлении АЗС: " + e.getMessage();
        }
    }

    public static String deleteAZS(int delete_id){
        try{
            Connection conn = getConnection();

            String checkSql = "SELECT name FROM azs WHERE id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, delete_id);
            ResultSet rs = checkStmt.executeQuery();

            if(!rs.next()){
                return "Ошибка: АЗС с ID: " + delete_id + " не найдена";
            }
            String azsName = rs.getString("name");

            String deleteSql = "DELETE FROM azs WHERE id = ?";
            PreparedStatement delStmt = conn.prepareStatement(deleteSql);
            delStmt.setInt(1, delete_id);

            int rowsAffected = delStmt.executeUpdate();
            checkStmt.close();
            delStmt.close();

            if (rowsAffected > 0){
                return azsName + " с ID: " + delete_id + " удалена!";
            } else {
                return "Ошибка при удалении АЗС!";
            }
        } catch (SQLException e){
            return "Ошибка: " + e.getMessage();
        }
    }

    public static String showOperators(){
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT o.id, o.username, o.name, o.role, " + "a.name as azs_name, a.address as azs_address " + "FROM operators o " + "LEFT JOIN azs a ON o.place = a.id " + "ORDER BY o.id");

            StringBuilder result = new StringBuilder();
            result.append("Список всех операторов:\n\n");

            while (rs.next()){
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String username = rs.getString("username");
                String role = rs.getString("role");
                String azs_name = rs.getString("azs_name");
                String azsAddress = rs.getString("azs_address");

                result.append("\t\t[ID: ").append(id).append("]\nФИО: ")
                        .append(name).append("\nЛогин: ").append(username).append("\nРаботает: ").append(azs_name).append(" по адресу ").append(azsAddress).append("\nРоль: ").append(role).append("\n\n\n");
            }

            rs.close();
            stmt.close();

            return result.toString();

        } catch (SQLException e) {
            return "Ошибка при получении списка операторов: " + e.getMessage();
        }

    }
}