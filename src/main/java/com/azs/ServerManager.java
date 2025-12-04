package com.azs;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mindrot.jbcrypt.BCrypt;

public class ServerManager {
    private static HttpServer server;
    private static Connection connection;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final int PORT = 8080;
    private static final Gson gson = new Gson();

    public static void startServer() {
        if (isRunning.get()) {
            System.out.println("Ошибка: сервер уже запущен!");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);

            // API эндпоинты
            server.createContext("/api/auth", new AuthHandler());
            server.createContext("/api/azs", new AzsHandler());
            server.createContext("/api/fuel", new FuelHandler());
            server.createContext("/api/operators", new OperatorsHandler());
            server.createContext("/api/users", new UsersHandler());

            server.setExecutor(null);
            server.start();
            isRunning.set(true);

            System.out.println("✅ Сервер запущен на порту: " + PORT);
            System.out.println("🌐 Доступ по: http://localhost:" + PORT);
            connectToDatabase();
        } catch (IOException e) {
            System.err.println("❌ Ошибка запуска сервера: " + e.getMessage());
        }
    }

    // ========== ОБРАБОТЧИК АВТОРИЗАЦИИ ==========
    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Метод не поддерживается");
                return;
            }

            try {
                String requestBody = readRequestBody(exchange);
                JsonObject json = gson.fromJson(requestBody, JsonObject.class);

                String username = json.get("username").getAsString();
                String password = json.get("password").getAsString();

                JsonObject response = new JsonObject();

                // ЗАПРОС К БД ДЛЯ ПОЛУЧЕНИЯ ВСЕХ ДАННЫХ ОПЕРАТОРА
                String sql = "SELECT " +
                        "o.id, o.username, o.name as operator_name, o.role, " +
                        "o.place as azs_id, a.name as azs_name, a.address as azs_address " +
                        "FROM operators o " +
                        "LEFT JOIN azs a ON o.place = a.id " +
                        "WHERE o.username = ? AND o.password_hash = ? AND o.is_active = true"; // ИСПРАВЛЕНО: status → is_active

                try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                    // ВАЖНО: нужно получать хеш из БД, а не создавать новый!
                    // Сначала нужно получить хеш пароля из БД для этого пользователя

                    // 1. Сначала получаем хеш пароля из БД
                    String getHashSql = "SELECT password_hash FROM operators WHERE username = ?";
                    try (PreparedStatement hashStmt = getConnection().prepareStatement(getHashSql)) {
                        hashStmt.setString(1, username);
                        ResultSet hashRs = hashStmt.executeQuery();

                        if (hashRs.next()) {
                            String storedHash = hashRs.getString("password_hash");

                            // 2. Проверяем пароль
                            if (BCrypt.checkpw(password, storedHash)) {
                                // 3. Если пароль верный, получаем остальные данные
                                pstmt.setString(1, username);
                                pstmt.setString(2, storedHash); // Используем реальный хеш из БД

                                ResultSet rs = pstmt.executeQuery();

                                if (rs.next()) {
                                    // 1. Получаем основные данные оператора
                                    int operatorId = rs.getInt("id");
                                    String role = rs.getString("role");
                                    String operatorName = rs.getString("operator_name");

                                    // Разделяем ФИО на имя и фамилию
                                    String firstName = "Иван";
                                    String lastName = "Иванов";

                                    if (operatorName != null && !operatorName.trim().isEmpty()) {
                                        String[] nameParts = operatorName.split(" ");
                                        if (nameParts.length >= 2) {
                                            firstName = nameParts[0];
                                            lastName = nameParts[1];
                                        } else if (nameParts.length == 1) {
                                            firstName = nameParts[0];
                                            lastName = "";
                                        }
                                    }

                                    // 2. Получаем данные АЗС
                                    int azsId = rs.getInt("azs_id");
                                    String azsName = rs.getString("azs_name");
                                    String azsAddress = rs.getString("azs_address");

                                    // 3. Получаем сумму транзакций за сегодня
                                    double todaysTotal = getTodaysTransactionsTotal(operatorId);

                                    // 4. Формируем ответ
                                    response.addProperty("success", true);
                                    response.addProperty("message", "Авторизация успешна");
                                    response.addProperty("username", username);
                                    response.addProperty("role", role);

                                    // Данные оператора
                                    JsonObject userData = new JsonObject();
                                    userData.addProperty("id", operatorId);
                                    userData.addProperty("username", username);
                                    userData.addProperty("firstName", firstName);
                                    userData.addProperty("lastName", lastName);
                                    userData.addProperty("fullName", operatorName);

                                    // Данные АЗС
                                    JsonObject azsData = new JsonObject();
                                    azsData.addProperty("id", azsId);
                                    azsData.addProperty("name", azsName != null ? azsName : "Не указана");
                                    azsData.addProperty("address", azsAddress != null ? azsAddress : "Не указан");

                                    userData.add("azs", azsData);
                                    response.add("user", userData);

                                    // Сумма транзакций
                                    response.addProperty("todaysTotal", todaysTotal);
                                    response.addProperty("formattedTotal", String.format("%.2f ₽", todaysTotal));

                                    System.out.println("✅ Успешный вход: " + username);
                                    System.out.println("   Оператор: " + operatorName);
                                    System.out.println("   АЗС: " + azsName);
                                    System.out.println("   Сумма за сегодня: " + todaysTotal + " ₽");

                                } else {
                                    // Неверные учетные данные
                                    response.addProperty("success", false);
                                    response.addProperty("message", "Неверный логин или пароль");
                                    System.out.println("❌ Неудачная попытка входа: " + username);
                                }
                            } else {
                                // Неверный пароль
                                response.addProperty("success", false);
                                response.addProperty("message", "Неверный логин или пароль");
                                System.out.println("❌ Неверный пароль для пользователя: " + username);
                            }
                        } else {
                            // Пользователь не найден
                            response.addProperty("success", false);
                            response.addProperty("message", "Неверный логин или пароль");
                            System.out.println("❌ Пользователь не найден: " + username);
                        }
                    }
                } catch (SQLException e) {
                    response.addProperty("success", false);
                    response.addProperty("message", "Ошибка базы данных: " + e.getMessage());
                    System.err.println("❌ Ошибка БД при авторизации: " + e.getMessage());
                    e.printStackTrace();
                }

                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Ошибка сервера: " + e.getMessage());
                sendJsonResponse(exchange, 500, error);
                e.printStackTrace();
            }
        }

        // Метод для получения суммы транзакций за сегодня
        private double getTodaysTransactionsTotal(int operatorId) {
            // Сначала получаем azs_id оператора
            String getAzsSql = "SELECT place as azs_id FROM operators WHERE id = ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(getAzsSql)) {
                pstmt.setInt(1, operatorId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    int azsId = rs.getInt("azs_id");

                    // Теперь получаем сумму транзакций для этой АЗС за сегодня
                    String sumSql = "SELECT COALESCE(SUM(total_amount), 0) as todays_total " +
                            "FROM transactions " +
                            "WHERE azs_id = ? " +
                            "AND DATE(created_at) = CURRENT_DATE";

                    try (PreparedStatement sumStmt = getConnection().prepareStatement(sumSql)) {
                        sumStmt.setInt(1, azsId);
                        ResultSet sumRs = sumStmt.executeQuery();

                        if (sumRs.next()) {
                            return sumRs.getDouble("todays_total");
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка получения суммы транзакций: " + e.getMessage());
                e.printStackTrace();
            }

            return 0.0;
        }
    }

    // ========== ОБРАБОТЧИК АЗС ==========
    static class AzsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    JsonArray azsList = new JsonArray();
                    String sql = "SELECT id, name, address, nozzle_count FROM azs ORDER BY id";

                    try (Statement stmt = getConnection().createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {

                        while (rs.next()) {
                            JsonObject azs = new JsonObject();
                            azs.addProperty("id", rs.getInt("id"));
                            azs.addProperty("name", rs.getString("name"));
                            azs.addProperty("address", rs.getString("address"));
                            azs.addProperty("nozzle_count", rs.getInt("nozzle_count"));
                            azsList.add(azs);
                        }
                    }

                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.add("data", azsList);
                    sendJsonResponse(exchange, 200, response);

                } else if ("POST".equals(exchange.getRequestMethod())) {
                    String requestBody = readRequestBody(exchange);
                    JsonObject json = gson.fromJson(requestBody, JsonObject.class);

                    String name = json.get("name").getAsString();
                    String address = json.get("address").getAsString();
                    int nozzle = json.get("nozzle_count").getAsInt();

                    String result = newAZS(name, address, nozzle);
                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.addProperty("message", result);

                    sendJsonResponse(exchange, 201, response);
                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }
            } catch (Exception e) {
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }
    }

    // ========== ОБРАБОТЧИК ТОПЛИВА ==========
    static class FuelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JsonArray fuelList = new JsonArray();
                String sql = "SELECT id, name, price FROM fuels ORDER BY id";

                try (Statement stmt = getConnection().createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        JsonObject fuel = new JsonObject();
                        fuel.addProperty("id", rs.getInt("id"));
                        fuel.addProperty("name", rs.getString("name"));
                        fuel.addProperty("price", rs.getDouble("price"));
                        fuelList.add(fuel);
                    }
                }

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("data", fuelList);
                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }
    }

    // ========== ОБРАБОТЧИК ОПЕРАТОРОВ ==========
    static class OperatorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JsonArray operatorsList = new JsonArray();
                String sql = "SELECT o.id, o.username, o.name, o.role, " +
                        "a.name as azs_name, a.address as azs_address " +
                        "FROM operators o LEFT JOIN azs a ON o.place = a.id " +
                        "ORDER BY o.id";

                try (Statement stmt = getConnection().createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        JsonObject operator = new JsonObject();
                        operator.addProperty("id", rs.getInt("id"));
                        operator.addProperty("username", rs.getString("username"));
                        operator.addProperty("name", rs.getString("name"));
                        operator.addProperty("role", rs.getString("role"));
                        operator.addProperty("azs_name", rs.getString("azs_name"));
                        operator.addProperty("azs_address", rs.getString("azs_address"));
                        operatorsList.add(operator);
                    }
                }

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("data", operatorsList);
                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }
    }

    // ========== ОБРАБОТЧИК ПОЛЬЗОВАТЕЛЕЙ ==========
    static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JsonArray usersList = new JsonArray();
                String sql = "SELECT id, username, phone, name, balance, " +
                        "total_spent, total_liters FROM users ORDER BY id";

                try (Statement stmt = getConnection().createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        JsonObject user = new JsonObject();
                        user.addProperty("id", rs.getInt("id"));
                        user.addProperty("username", rs.getString("username"));
                        user.addProperty("name", rs.getString("name"));
                        user.addProperty("phone", rs.getString("phone"));
                        user.addProperty("balance", rs.getDouble("balance"));
                        user.addProperty("total_spent", rs.getDouble("total_spent"));
                        user.addProperty("total_liters", rs.getDouble("total_liters"));
                        usersList.add(user);
                    }
                }

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("data", usersList);
                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        return requestBody.toString();
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject response) throws IOException {
        String responseJson = gson.toJson(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, responseJson.getBytes().length);

        OutputStream os = exchange.getResponseBody();
        os.write(responseJson.getBytes());
        os.close();
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        sendJsonResponse(exchange, statusCode, error);
    }

    // ========== МЕТОДЫ ДЛЯ СТАРОГО КОНСОЛЬНОГО ИНТЕРФЕЙСА ==========
    public static void stopServer() {
        if (!isRunning.get()) {
            System.out.println("Ошибка: сервер не запущен!");
            return;
        }

        server.stop(0);
        isRunning.set(false);
        System.out.println("Сервер остановлен");
    }

    public static void showStatus() {
        String status = isRunning.get() ? "АКТИВЕН" : "НЕАКТИВЕН";
        System.out.println("Статус сервера: " + status);
        if (isRunning.get()) {
            System.out.println("URL: http://localhost:" + PORT);
        }
    }

    // ========== ПОДКЛЮЧЕНИЕ К БАЗЕ ДАННЫХ ==========
    private static void connectToDatabase() {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://localhost:5432/azs_database";
            String user = "postgres";
            String password = "123456";

            connection = DriverManager.getConnection(url, user, password);
            System.out.println("✅ Подключение к БД установлено");
        } catch (Exception e) {
            System.err.println("❌ Ошибка подключения к БД: " + e.getMessage());
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    // ========== МЕТОДЫ АВТОРИЗАЦИИ ==========
    private static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password_hash FROM operators WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("password_hash");
                return BCrypt.checkpw(password, hashedPassword);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка аутентификации: " + e.getMessage());
        }
        return false;
    }

    private static String getUserRole(String username) {
        String sql = "SELECT role FROM operators WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("role");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения роли: " + e.getMessage());
        }
        return "unknown";
    }

    // ========== СТАРЫЕ МЕТОДЫ (для обратной совместимости) ==========
    public static String getFuelPrices() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name, price FROM fuels ORDER BY id");

            StringBuilder result = new StringBuilder();
            result.append("Актуальные цены на топливо:\n");

            while (rs.next()) {
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

    public static String updateFuelPrice(int fuelId, double newPrice) {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            ResultSet checkRs = stmt.executeQuery("SELECT name FROM fuels WHERE id = " + fuelId);
            if (!checkRs.next()) {
                return "Ошибка: топлива с указанным ID не существует!";
            }

            String fuelName = checkRs.getString("name");
            checkRs.close();

            String sql = "UPDATE fuels SET price = " + newPrice + " WHERE id = " + fuelId;
            int rowsAffected = stmt.executeUpdate(sql);
            stmt.close();

            if (rowsAffected > 0) {
                return "Цена на " + fuelName + " изменена на " + newPrice + " руб.";
            } else {
                return "Ошибка изменения цены!";
            }
        } catch (SQLException e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    public static String showAZS() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name, address, nozzle_count FROM azs ORDER BY id");

            StringBuilder result = new StringBuilder();
            result.append("Список всех АЗС:\n");

            while (rs.next()) {
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

    public static String newAZS(String name, String address, int nozzle) {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            String sql = "";

            if (nozzle == 1) {
                sql = "INSERT INTO azs (name, address, nozzle_count, nozzle_1) " +
                        "VALUES ('" + name + "', '" + address + "', " + nozzle + ", 'active')";
            } else if (nozzle == 2) {
                sql = "INSERT INTO azs (name, address, nozzle_count, nozzle_1, nozzle_2) " +
                        "VALUES ('" + name + "', '" + address + "', " + nozzle + ", 'active', 'active')";
            } else if (nozzle == 3) {
                sql = "INSERT INTO azs (name, address, nozzle_count, nozzle_1, nozzle_2, nozzle_3) " +
                        "VALUES ('" + name + "', '" + address + "', " + nozzle + ", 'active', 'active', 'active')";
            } else if (nozzle == 4) {
                sql = "INSERT INTO azs (name, address, nozzle_count, nozzle_1, nozzle_2, nozzle_3, nozzle_4) " +
                        "VALUES ('" + name + "', '" + address + "', " + nozzle + ", 'active', 'active', 'active', 'active')";
            } else {
                return "Ошибка при добавлении АЗС! Количество колонок должно быть от 1 до 4.";
            }

            int rowsAffected = stmt.executeUpdate(sql);
            stmt.close();

            if (rowsAffected > 0) {
                return name + " по адресу " + address + " успешно добавлена!";
            } else {
                return "Ошибка: не удалось добавить АЗС!";
            }

        } catch (SQLException e) {
            return "Ошибка при добавлении АЗС: " + e.getMessage();
        }
    }

    public static String deleteAZS(int delete_id) {
        try {
            Connection conn = getConnection();

            String checkSql = "SELECT name FROM azs WHERE id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, delete_id);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                return "Ошибка: АЗС с ID: " + delete_id + " не найдена";
            }
            String azsName = rs.getString("name");

            String deleteSql = "DELETE FROM azs WHERE id = ?";
            PreparedStatement delStmt = conn.prepareStatement(deleteSql);
            delStmt.setInt(1, delete_id);

            int rowsAffected = delStmt.executeUpdate();
            checkStmt.close();
            delStmt.close();

            if (rowsAffected > 0) {
                return azsName + " с ID: " + delete_id + " удалена!";
            } else {
                return "Ошибка при удалении АЗС!";
            }
        } catch (SQLException e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    public static String showOperators() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT o.id, o.username, o.name, o.role, " +
                    "a.name as azs_name, a.address as azs_address " +
                    "FROM operators o " +
                    "LEFT JOIN azs a ON o.place = a.id " +
                    "ORDER BY o.id");

            StringBuilder result = new StringBuilder();
            result.append("Список всех операторов:\n\n");

            while (rs.next()) {
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

    public static String createOperator(String operator_username, String operator_password, String operator_name, int operatorAZSId) {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            String newPassword = hashPassword(operator_password);
            String sql = "INSERT INTO operators (username, password_hash, name, place) " +
                    "VALUES ('" + operator_username + "', '" + newPassword + "', '" + operator_name + "', '" + operatorAZSId + "')";
            int rowsAffected = stmt.executeUpdate(sql);
            stmt.close();

            if (rowsAffected > 0) {
                return "Оператор " + operator_name + " успешно добавлен!";
            } else {
                return "Ошибка: не удалось добавить оператора АЗС!";
            }

        } catch (SQLException e) {
            return "Ошибка при добавлении оператора АЗС: " + e.getMessage();
        }
    }

    public static String deleteOperator(int deleteOperatorId) {
        try {
            Connection conn = getConnection();
            String checkSql = "SELECT id FROM operators WHERE id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, deleteOperatorId);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                return "Ошибка: оператор с ID: " + deleteOperatorId + " не найден!";
            }

            String deleteSql = "DELETE FROM operators WHERE id = ?";
            PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, deleteOperatorId);
            int rowsAffected = deleteStmt.executeUpdate();

            checkStmt.close();
            deleteStmt.close();

            if (rowsAffected > 0) {
                return "Оператор с ID: " + deleteOperatorId + " удален!";
            } else {
                return "Ошибка при удалении оператора!";
            }

        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    public static String showUsers() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, username, phone, name, balance, total_spent, total_liters FROM users ORDER BY id");

            StringBuilder result = new StringBuilder();
            result.append("Список всех пользователей:\n\n");

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String username = rs.getString("username");
                String phone = rs.getString("phone");
                Double balance = rs.getDouble("balance");
                Double total_spent = rs.getDouble("total_spent");
                Double total_liters = rs.getDouble("total_liters");

                result.append("\t\t[ID: ").append(id).append("]\nФИО: ")
                        .append(name).append("\nЮзернейм: ").append(username).append("\nНомер телефона: ").append(phone).append("\nБаланс бонусов: ").append(balance).append(" руб.").append("\nВсего потрачено: ").append(total_spent).append(" руб").append("\nВсего заправлено: ").append(total_liters).append(" л.").append("\n\n");
            }

            rs.close();
            stmt.close();

            return result.toString();

        } catch (SQLException e) {
            return "Ошибка при получении списка пользователей: " + e.getMessage();
        }
    }

    public static String deleteUser(int choice) {
        try {
            Connection conn = getConnection();
            String checkSql = "SELECT id FROM users WHERE id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, choice);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                return "Ошибка: пользователь с ID: " + choice + " не найден!";
            }

            String deleteSql = "DELETE FROM users WHERE id = ?";
            PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, choice);
            int rowsAffected = deleteStmt.executeUpdate();

            checkStmt.close();
            deleteStmt.close();

            if (rowsAffected > 0) {
                return "Пользователь с ID: " + choice + " удален!";
            } else {
                return "Ошибка при удалении пользователя!";
            }

        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    public static String executeCommand(String command) {
        if (command.isEmpty()) return "";

        switch (command) {
            case "start":
                startServer();
                break;
            case "stop":
                stopServer();
                break;
            case "operators":
                System.out.println(showOperators());
                break;
            case "new operator":
                System.out.println(createOperator("testUsername", "test_password", "Иванов Иван Иванович", 1));
                break;
            case "users":
                System.out.println(showUsers());
                break;
            case "azs":
                System.out.println(showAZS());
                break;
            case "new azs":
                System.out.println(newAZS("Тестовая заправка", "г. Минск, ул. Минская, д.1", 4));
                break;
            case "price":
                System.out.println(getFuelPrices());
                break;
            case "restart":
                stopServer();
                startServer();
                break;
            case "status":
                showStatus();
                break;
            case "help":
                System.out.println("Команды:\n\t1. start / stop / restart / status - команды управления сервером\n\t2. operator / new operator / users - управление пользователями\n\t3. azs / new azs / price - управление АЗС");
                break;
        }

        return " ";
    }

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    public static String generateRandomString() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        int length = 10;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * characters.length());
            result.append(characters.charAt(index));
        }

        return result.toString();
    }
}