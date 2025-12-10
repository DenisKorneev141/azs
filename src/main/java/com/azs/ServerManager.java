package com.azs;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
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

            System.out.println("Создание контекстов API...");

            // API эндпоинты
            server.createContext("/api/auth", new AuthHandler());
            server.createContext("/api/azs", new AzsHandler());
            server.createContext("/api/reports", new ReportsHandler());
            server.createContext("/api/fuel", new FuelHandler());
            server.createContext("/", new CorsHandler());
            // ДОБАВЬТЕ ЭТО В ServerManager.java в метод startServer() после других контекстов:
            server.createContext("/api/users/register", new UserRegistrationHandler());
            server.createContext("/api/users/login", new UserLoginHandler());
            server.createContext("/api/users/profile", new UserProfileHandler());
            server.createContext("/api/users/transactions", new UserTransactionsHandler());
            server.createContext("/api/users/update", new UserUpdateHandler());

            server.createContext("/api/operators", new OperatorsHandler());
            server.createContext("/api/qr/", new QrCodeHandler());
            server.createContext("/api/users", new UsersHandler());
            server.createContext("/api/transactions/recent", new RecentTransactionsHandler());

            // НОВЫЕ ЭНДПОИНТЫ ДЛЯ ТРАНЗАКЦИЙ И ЧЕКОВ
            server.createContext("/api/transactions", new TransactionsHandler());
            server.createContext("/api/users/search", new UserSearchHandler());
            server.createContext("/api/users/", new UserBalanceHandler()); // Для обновления баланса

            // ВАЖНО: Добавьте обработчик чеков
            server.createContext("/api/receipts/generate", new ReceiptHandler());

            // ВАЖНО: Создать контекст для колонок
            server.createContext("/api/azs/", new NozzlesHandler());

            server.createContext("/api/health", new HealthHandler());

            server.setExecutor(null);
            server.start();
            isRunning.set(true);

            System.out.println("\n✅ Сервер запущен на порту: " + PORT);
            System.out.println("🌐 Доступ по: http://localhost:" + PORT);
            System.out.println("\nДоступные эндпоинты:");
            System.out.println("  POST /api/transactions - создать новую транзакцию");
            System.out.println("  POST /api/receipts/generate - сгенерировать чек");
            System.out.println("  GET  /api/users/search?phone=... - поиск пользователя по телефону");
            System.out.println("  POST /api/users/{id}/update-balance - обновить баланс пользователя");

            connectToDatabase();

        } catch (IOException e) {
            System.err.println("❌ Ошибка запуска сервера: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void logRequest(HttpExchange exchange) {
        System.out.println("📥 " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() +
                " | Headers: " + exchange.getRequestHeaders().entrySet());
    }

    // ========== ОБРАБОТЧИК CORS ==========
    static class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                // Обрабатываем preflight запросы
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Для остальных запросов просто добавляем CORS заголовки
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String path = exchange.getRequestURI().getPath();

            if (path.startsWith("/api/")) {
                // Перенаправляем на соответствующий обработчик
                String newPath = path.substring(4); // Убираем /api
                exchange.setAttribute("handlerPath", newPath);

                // Здесь можно добавить логику перенаправления
                // Или просто вернуть 404
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", "Endpoint not found: " + path);
                sendJsonResponse(exchange, 404, error);
            } else {
                // Для корневого пути возвращаем информацию о сервере
                JsonObject info = new JsonObject();
                info.addProperty("server", "AZS Server");
                info.addProperty("version", "1.0");
                info.addProperty("status", "running");
                info.addProperty("time", LocalDateTime.now().toString());
                sendJsonResponse(exchange, 200, info);
            }
        }
    }

    // ========== ОБРАБОТЧИК РЕГИСТРАЦИИ ПОЛЬЗОВАТЕЛЯ ==========
    // ========== ОБРАБОТЧИК РЕГИСТРАЦИИ ПОЛЬЗОВАТЕЛЯ ==========
    static class UserRegistrationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                logRequest(exchange);

                // ОБРАБОТКА OPTIONS ЗАПРОСОВ (CORS Preflight)
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                    exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                // Проверяем метод запроса
                if (!"POST".equals(exchange.getRequestMethod())) {
                    System.out.println("❌ Неверный метод: " + exchange.getRequestMethod());
                    sendError(exchange, 405, "Метод не поддерживается");
                    return;
                }

                // УСТАНОВИТЬ CORS ЗАГОЛОВКИ ДЛЯ ОСНОВНОГО ОТВЕТА
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

                String requestBody = readRequestBody(exchange);
                System.out.println("📝 Тело запроса регистрации: " + requestBody);

                JsonObject json;
                try {
                    json = gson.fromJson(requestBody, JsonObject.class);
                } catch (Exception e) {
                    System.err.println("❌ Ошибка парсинга JSON: " + e.getMessage());
                    sendError(exchange, 400, "Неверный формат JSON");
                    return;
                }

                // Проверяем обязательные поля
                if (!json.has("username") || !json.has("phone") || !json.has("name") || !json.has("password")) {
                    System.err.println("❌ Отсутствуют обязательные поля");
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("message", "Отсутствуют обязательные поля");
                    sendJsonResponse(exchange, 400, error);
                    return;
                }

                String username = json.get("username").getAsString();
                String phone = json.get("phone").getAsString();
                String name = json.get("name").getAsString();
                String password = json.get("password").getAsString();

                System.out.println("📝 Регистрация пользователя: " + name + ", тел: " + phone);

                // Проверяем, не зарегистрирован ли уже пользователь
                String checkSql = "SELECT id FROM users WHERE phone = ? OR username = ?";
                try (PreparedStatement checkStmt = getConnection().prepareStatement(checkSql)) {
                    checkStmt.setString(1, phone);
                    checkStmt.setString(2, username);
                    ResultSet rs = checkStmt.executeQuery();

                    if (rs.next()) {
                        System.out.println("❌ Пользователь уже существует: " + phone);
                        JsonObject error = new JsonObject();
                        error.addProperty("success", false);
                        error.addProperty("message", "Пользователь с таким телефоном или логином уже существует");
                        sendJsonResponse(exchange, 400, error);
                        return;
                    }
                } catch (SQLException e) {
                    System.err.println("❌ Ошибка проверки пользователя: " + e.getMessage());
                    sendError(exchange, 500, "Ошибка базы данных");
                    return;
                }

                // Хешируем пароль
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                // Регистрируем пользователя
                String insertSql = "INSERT INTO users (username, phone, password_hash, name, balance, total_spent, total_liters, is_active, created_at) " +
                        "VALUES (?, ?, ?, ?, 0.00, 0.00, 0.00, true, NOW())";

                try (PreparedStatement pstmt = getConnection().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, username);
                    pstmt.setString(2, phone);
                    pstmt.setString(3, hashedPassword);
                    pstmt.setString(4, name);

                    System.out.println("📝 Выполняем SQL: " + insertSql);

                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        // Получаем ID нового пользователя
                        ResultSet generatedKeys = pstmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            int userId = generatedKeys.getInt(1);

                            JsonObject response = new JsonObject();
                            response.addProperty("success", true);
                            response.addProperty("message", "Регистрация успешна");
                            response.addProperty("userId", userId);

                            // Создаем токен
                            String token = generateToken(userId, username);
                            response.addProperty("token", token);

                            // Данные пользователя
                            JsonObject userData = new JsonObject();
                            userData.addProperty("id", userId);
                            userData.addProperty("username", username);
                            userData.addProperty("phone", phone);
                            userData.addProperty("name", name);
                            userData.addProperty("balance", 0.0);
                            userData.addProperty("total_spent", 0.0);
                            userData.addProperty("total_liters", 0.0);
                            response.add("user", userData);

                            System.out.println("✅ Зарегистрирован новый пользователь: " + name + " (ID: " + userId + ")");
                            sendJsonResponse(exchange, 201, response);
                        }
                    } else {
                        throw new SQLException("Не удалось создать пользователя");
                    }
                } catch (SQLException e) {
                    System.err.println("❌ Ошибка регистрации в БД: " + e.getMessage());
                    e.printStackTrace();
                    sendError(exchange, 500, "Ошибка регистрации: " + e.getMessage());
                }

            } catch (Exception e) {
                System.err.println("❌ Неожиданная ошибка регистрации: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Внутренняя ошибка сервера");
            }
        }
    }

    // ========== ОБРАБОТЧИК ВХОДА ПОЛЬЗОВАТЕЛЯ ==========
    static class UserLoginHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            logRequest(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Метод не поддерживается");
                return;
            }

            try {
                String requestBody = readRequestBody(exchange);
                JsonObject json = gson.fromJson(requestBody, JsonObject.class);

                String phone = json.get("phone").getAsString();
                String password = json.get("password").getAsString();

                JsonObject response = new JsonObject();

                // Ищем пользователя по телефону
                String sql = "SELECT id, username, phone, name, password_hash, balance, " +
                        "total_spent, total_liters FROM users WHERE phone = ? AND is_active = true";

                try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                    pstmt.setString(1, phone);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");

                        // Проверяем пароль
                        if (BCrypt.checkpw(password, storedHash)) {
                            int userId = rs.getInt("id");
                            String username = rs.getString("username");
                            String name = rs.getString("name");

                            // Создаем ответ с данными пользователя
                            response.addProperty("success", true);
                            response.addProperty("message", "Авторизация успешна");

                            // Данные пользователя
                            JsonObject userData = new JsonObject();
                            userData.addProperty("id", userId);
                            userData.addProperty("username", username);
                            userData.addProperty("phone", phone);
                            userData.addProperty("name", name);
                            userData.addProperty("balance", rs.getDouble("balance"));
                            userData.addProperty("total_spent", rs.getDouble("total_spent"));
                            userData.addProperty("total_liters", rs.getDouble("total_liters"));

                            response.add("user", userData);

                            // Создаем токен
                            String token = generateToken(userId, username);
                            response.addProperty("token", token);

                            System.out.println("Успешный вход пользователя: " + name + " (ID: " + userId + ")");

                        } else {
                            response.addProperty("success", false);
                            response.addProperty("message", "Неверный пароль");
                            System.out.println("Неверный пароль для телефона: " + phone);
                        }
                    } else {
                        response.addProperty("success", false);
                        response.addProperty("message", "Пользователь не найден");
                        System.out.println("Пользователь не найден: " + phone);
                    }
                }

                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                System.err.println("Ошибка входа: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка входа: " + e.getMessage());
            }
        }
    }

    // ========== ОБРАБОТЧИК ПРОФИЛЯ ПОЛЬЗОВАТЕЛЯ ==========
    static class UserProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            logRequest(exchange);
            try {
                // Проверяем авторизацию
                String token = getTokenFromRequest(exchange);
                if (token == null || !validateToken(token)) {
                    sendError(exchange, 401, "Требуется авторизация");
                    return;
                }

                // Получаем ID пользователя из токена
                int userId = getUserIdFromToken(token);

                if ("GET".equals(exchange.getRequestMethod())) {
                    // Получаем данные пользователя
                    String sql = "SELECT id, username, phone, name, balance, " +
                            "total_spent, total_liters, created_at FROM users WHERE id = ?";

                    try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                        pstmt.setInt(1, userId);
                        ResultSet rs = pstmt.executeQuery();

                        JsonObject response = new JsonObject();

                        if (rs.next()) {
                            JsonObject userData = new JsonObject();
                            userData.addProperty("id", rs.getInt("id"));
                            userData.addProperty("username", rs.getString("username"));
                            userData.addProperty("phone", rs.getString("phone"));
                            userData.addProperty("name", rs.getString("name"));
                            userData.addProperty("balance", rs.getDouble("balance"));
                            userData.addProperty("total_spent", rs.getDouble("total_spent"));
                            userData.addProperty("total_liters", rs.getDouble("total_liters"));

                            // Преобразуем дату
                            java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
                            if (timestamp != null) {
                                userData.addProperty("created_at", timestamp.toLocalDateTime().toString());
                            }

                            response.addProperty("success", true);
                            response.add("user", userData);
                        } else {
                            response.addProperty("success", false);
                            response.addProperty("message", "Пользователь не найден");
                        }

                        sendJsonResponse(exchange, 200, response);
                    }

                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }

            } catch (Exception e) {
                System.err.println("Ошибка получения профиля: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка сервера");
            }
        }
    }

    // ========== ОБРАБОТЧИК ИСТОРИИ ТРАНЗАКЦИЙ ==========
    static class UserTransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            logRequest(exchange);
            try {
                // Проверяем авторизацию
                String token = getTokenFromRequest(exchange);
                if (token == null || !validateToken(token)) {
                    sendError(exchange, 401, "Требуется авторизация");
                    return;
                }

                // Получаем ID пользователя из токена
                int userId = getUserIdFromToken(token);

                // Получаем параметры запроса
                String query = exchange.getRequestURI().getQuery();
                int limit = 50;

                if (query != null && query.contains("limit=")) {
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("limit=")) {
                            limit = Integer.parseInt(param.substring(6));
                            break;
                        }
                    }
                }

                // Получаем историю транзакций
                String sql = "SELECT t.id, t.fuel_type, t.liters, t.price_per_liter, t.total_amount, " +
                        "t.payment_method, t.bonus_spent, t.status, t.created_at, " +
                        "a.name as azs_name " +
                        "FROM transactions t " +
                        "LEFT JOIN azs a ON t.azs_id = a.id " +
                        "WHERE t.user_id = ? " +
                        "ORDER BY t.created_at DESC " +
                        "LIMIT ?";

                try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                    pstmt.setInt(1, userId);
                    pstmt.setInt(2, limit);

                    ResultSet rs = pstmt.executeQuery();

                    JsonArray transactions = new JsonArray();

                    while (rs.next()) {
                        JsonObject trans = new JsonObject();
                        trans.addProperty("id", rs.getInt("id"));
                        trans.addProperty("fuel_type", rs.getString("fuel_type"));
                        trans.addProperty("liters", rs.getDouble("liters"));
                        trans.addProperty("price_per_liter", rs.getDouble("price_per_liter"));
                        trans.addProperty("total_amount", rs.getDouble("total_amount"));
                        trans.addProperty("payment_method", rs.getString("payment_method"));
                        trans.addProperty("bonus_spent", rs.getDouble("bonus_spent"));
                        trans.addProperty("status", rs.getString("status"));
                        trans.addProperty("azs_name", rs.getString("azs_name"));

                        // Форматируем дату
                        java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
                        if (timestamp != null) {
                            String formattedDate = timestamp.toLocalDateTime().format(
                                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                            );
                            trans.addProperty("created_at", formattedDate);
                            trans.addProperty("timestamp", timestamp.getTime());
                        }

                        transactions.add(trans);
                    }

                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.add("transactions", transactions);
                    response.addProperty("count", transactions.size());

                    sendJsonResponse(exchange, 200, response);

                    System.out.println(" Загружено " + transactions.size() + " транзакций для пользователя ID: " + userId);
                }

            } catch (Exception e) {
                System.err.println("❌ Ошибка получения истории транзакций: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка сервера");
            }
        }
    }

    // ========== ОБРАБОТЧИК ОБНОВЛЕНИЯ ПОЛЬЗОВАТЕЛЯ ==========
    static class UserUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            logRequest(exchange);
            try {
                // Проверяем авторизацию
                String token = getTokenFromRequest(exchange);
                if (token == null || !validateToken(token)) {
                    sendError(exchange, 401, "Требуется авторизация");
                    return;
                }

                // Получаем ID пользователя из токена
                int userId = getUserIdFromToken(token);

                if ("PUT".equals(exchange.getRequestMethod())) {
                    String requestBody = readRequestBody(exchange);
                    JsonObject json = gson.fromJson(requestBody, JsonObject.class);

                    // Собираем поля для обновления
                    StringBuilder sqlBuilder = new StringBuilder("UPDATE users SET ");
                    List<Object> params = new ArrayList<>();

                    if (json.has("name")) {
                        sqlBuilder.append("name = ?, ");
                        params.add(json.get("name").getAsString());
                    }

                    if (json.has("phone")) {
                        // Проверяем, не занят ли телефон
                        String checkSql = "SELECT id FROM users WHERE phone = ? AND id != ?";
                        try (PreparedStatement checkStmt = getConnection().prepareStatement(checkSql)) {
                            checkStmt.setString(1, json.get("phone").getAsString());
                            checkStmt.setInt(2, userId);
                            ResultSet rs = checkStmt.executeQuery();

                            if (rs.next()) {
                                JsonObject error = new JsonObject();
                                error.addProperty("success", false);
                                error.addProperty("message", "Телефон уже используется другим пользователем");
                                sendJsonResponse(exchange, 400, error);
                                return;
                            }
                        }

                        sqlBuilder.append("phone = ?, ");
                        params.add(json.get("phone").getAsString());
                    }

                    if (json.has("password")) {
                        String newPassword = json.get("password").getAsString();
                        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
                        sqlBuilder.append("password_hash = ?, ");
                        params.add(hashedPassword);
                    }

                    // Удаляем последнюю запятую
                    sqlBuilder.setLength(sqlBuilder.length() - 2);
                    sqlBuilder.append(" WHERE id = ?");
                    params.add(userId);

                    try (PreparedStatement pstmt = getConnection().prepareStatement(sqlBuilder.toString())) {
                        for (int i = 0; i < params.size(); i++) {
                            pstmt.setObject(i + 1, params.get(i));
                        }

                        int rowsAffected = pstmt.executeUpdate();

                        JsonObject response = new JsonObject();
                        if (rowsAffected > 0) {
                            response.addProperty("success", true);
                            response.addProperty("message", "Профиль обновлен");
                        } else {
                            response.addProperty("success", false);
                            response.addProperty("message", "Не удалось обновить профиль");
                        }

                        sendJsonResponse(exchange, 200, response);
                    }

                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }

            } catch (Exception e) {
                System.err.println("❌ Ошибка обновления пользователя: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка сервера");
            }
        }
    }


    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ АВТОРИЗАЦИИ ==========
    private static String generateToken(int userId, String username) {
        // Простая реализация токена (в продакшн используйте JWT)
        String tokenData = userId + ":" + username + ":" + System.currentTimeMillis();
        return Base64.getEncoder().encodeToString(tokenData.getBytes());
    }

    private static String getTokenFromRequest(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private static boolean validateToken(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            if (parts.length >= 3) {
                // Проверяем, не истек ли токен (24 часа)
                long tokenTime = Long.parseLong(parts[2]);
                long currentTime = System.currentTimeMillis();
                return (currentTime - tokenTime) < 24 * 60 * 60 * 1000;
            }
        } catch (Exception e) {
            // Невалидный токен
        }
        return false;
    }

    private static int getUserIdFromToken(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            if (parts.length >= 1) {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            // Ошибка парсинга
        }
        return 0;
    }

    // ========== ОБРАБОТЧИК АВТОРИЗАЦИИ ==========
    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
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
                        "WHERE o.username = ? AND o.password_hash = ? AND o.is_active = true";

                try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
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

                                    // 3. Получаем статистику за сегодня
                                    JsonObject todayStats = getTodaysStats(operatorId);
                                    double todaysTotal = todayStats.get("total_amount").getAsDouble();
                                    int todaysTransactions = todayStats.get("transaction_count").getAsInt();
                                    double todaysLiters = todayStats.get("total_liters").getAsDouble();

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

                                    // Статистика за сегодня
                                    response.addProperty("todaysTotal", todaysTotal);
                                    response.addProperty("todaysTransactions", todaysTransactions);
                                    response.addProperty("todaysLiters", todaysLiters);

                                    response.addProperty("formattedTotal", String.format("%.2f ₽", todaysTotal));
                                    response.addProperty("formattedLiters", String.format("%.1f л", todaysLiters));

                                    System.out.println("✅ Успешный вход: " + username);
                                    System.out.println("   Оператор: " + operatorName);
                                    System.out.println("   АЗС: " + azsName);
                                    System.out.println("   Статистика за сегодня:");
                                    System.out.println("   - Сумма: " + todaysTotal + " ₽");
                                    System.out.println("   - Транзакций: " + todaysTransactions);
                                    System.out.println("   - Литров: " + todaysLiters + " л");

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

        // Метод для получения статистики за сегодня
        private JsonObject getTodaysStats(int operatorId) {
            JsonObject stats = new JsonObject();

            try {
                // 1. Сначала получаем azs_id оператора
                String getAzsSql = "SELECT place as azs_id FROM operators WHERE id = ?";

                try (PreparedStatement pstmt = getConnection().prepareStatement(getAzsSql)) {
                    pstmt.setInt(1, operatorId);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        int azsId = rs.getInt("azs_id");

                        // 2. Теперь получаем статистику для этой АЗС за сегодня
                        String statsSql = "SELECT " +
                                "  COALESCE(SUM(total_amount), 0) as todays_total, " +
                                "  COUNT(*) as transaction_count, " +
                                "  COALESCE(SUM(liters), 0) as total_liters " +  // Предполагаю, что есть поле liters
                                "FROM transactions " +
                                "WHERE azs_id = ? " +
                                "AND DATE(created_at) = CURRENT_DATE";

                        try (PreparedStatement statsStmt = getConnection().prepareStatement(statsSql)) {
                            statsStmt.setInt(1, azsId);
                            ResultSet statsRs = statsStmt.executeQuery();

                            if (statsRs.next()) {
                                double totalAmount = statsRs.getDouble("todays_total");
                                int transactionCount = statsRs.getInt("transaction_count");
                                double totalLiters = statsRs.getDouble("total_liters");

                                stats.addProperty("total_amount", totalAmount);
                                stats.addProperty("transaction_count", transactionCount);
                                stats.addProperty("total_liters", totalLiters);
                                stats.addProperty("success", true);

                                System.out.println("📊 Статистика за сегодня для АЗС " + azsId + ":");
                                System.out.println("   Сумма: " + totalAmount + " ₽");
                                System.out.println("   Транзакций: " + transactionCount);
                                System.out.println("   Литров: " + totalLiters);
                            } else {
                                // Нет транзакций за сегодня
                                stats.addProperty("total_amount", 0.0);
                                stats.addProperty("transaction_count", 0);
                                stats.addProperty("total_liters", 0.0);
                                stats.addProperty("success", true);
                                System.out.println("📊 Нет транзакций за сегодня для АЗС " + azsId);
                            }
                        }
                    } else {
                        // Оператор не найден
                        stats.addProperty("total_amount", 0.0);
                        stats.addProperty("transaction_count", 0);
                        stats.addProperty("total_liters", 0.0);
                        stats.addProperty("success", false);
                        stats.addProperty("error", "Оператор не найден");
                    }
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка получения статистики: " + e.getMessage());
                e.printStackTrace();
                stats.addProperty("success", false);
                stats.addProperty("error", e.getMessage());
                // Устанавливаем значения по умолчанию
                stats.addProperty("total_amount", 0.0);
                stats.addProperty("transaction_count", 0);
                stats.addProperty("total_liters", 0.0);
            }

            return stats;
        }
    }

    static class RecentTransactionsHandler implements HttpHandler {

        private String getParameter(String query, String paramName) {
            return getParameter(query, paramName, "");
        }

        private String getParameter(String query, String paramName, String defaultValue) {
            if (query == null || query.isEmpty()) return defaultValue;

            String[] params = query.split("&");
            for (String param : params) {
                String[] pair = param.split("=");
                if (pair.length >= 2 && pair[0].equals(paramName)) {
                    return pair[1];
                }
            }
            return defaultValue;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String query = exchange.getRequestURI().getQuery();

                // Проверяем наличие обязательного параметра
                if (query == null || !query.contains("azs_id")) {
                    sendError(exchange, 400, "Параметр azs_id обязателен");
                    return;
                }

                // Получаем параметры
                String azsIdStr = getParameter(query, "azs_id");
                String limitStr = getParameter(query, "limit", "50");

                int azsId = Integer.parseInt(azsIdStr);
                int limit = Integer.parseInt(limitStr);

                System.out.println("📥 Запрос транзакций для АЗС: " + azsId + ", лимит: " + limit);

                JsonArray transactions = getRecentTransactions(azsId, limit);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("data", transactions);
                response.addProperty("count", transactions.size());

                sendJsonResponse(exchange, 200, response);

            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Некорректный числовой параметр: " + e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 500, "Ошибка сервера: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private JsonArray getRecentTransactions(int azsId, int limit) {
            JsonArray result = new JsonArray();

            String sql = "SELECT id, created_at, fuel_type, liters, total_amount, " +
                    "payment_method, status " +
                    "FROM transactions " +
                    "WHERE azs_id = ? " +
                    "ORDER BY created_at DESC " +
                    "LIMIT ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, azsId);
                pstmt.setInt(2, limit);

                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    JsonObject trans = new JsonObject();
                    trans.addProperty("id", rs.getInt("id"));

                    // Форматируем дату
                    java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
                    if (timestamp != null) {
                        java.time.LocalDateTime dateTime = timestamp.toLocalDateTime();
                        String formattedDate = dateTime.format(
                                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        );
                        trans.addProperty("time", formattedDate);
                    } else {
                        trans.addProperty("time", "Не указано");
                    }

                    trans.addProperty("fuelType", rs.getString("fuel_type"));
                    trans.addProperty("liters", rs.getDouble("liters"));
                    trans.addProperty("amount", rs.getDouble("total_amount"));
                    trans.addProperty("paymentMethod", rs.getString("payment_method"));
                    trans.addProperty("status", rs.getString("status"));

                    result.add(trans);
                }

                System.out.println("✅ Загружено " + result.size() + " транзакций для АЗС " + azsId);

            } catch (SQLException e) {
                System.err.println("❌ Ошибка SQL при загрузке транзакций: " + e.getMessage());
                System.err.println("SQL запрос: " + sql);
                e.printStackTrace();
            }

            return result;
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("timestamp", System.currentTimeMillis());
            sendJsonResponse(exchange, 200, response);
        }
    }

    // ========== ОБРАБОТЧИК QR-КОДОВ (УПРОЩЕННЫЙ) ==========
    static class QrCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                System.out.println("🔗 Запрос QR-кода: " + path);

                // Пример: /api/qr/1/2 (АЗС ID=1, Колонка=2)
                String[] parts = path.split("/");

                if (parts.length != 5) {
                    sendError(exchange, 400, "Неверный URL формат. Нужно: /api/qr/{azs_id}/{nozzle}");
                    return;
                }

                int azsId = Integer.parseInt(parts[3]);
                int nozzleNumber = Integer.parseInt(parts[4]);

                System.out.println("🔗 Генерация QR для АЗС " + azsId + ", колонка " + nozzleNumber);

                // Простая проверка существования АЗС
                JsonObject azsInfo = getAzsInfo(azsId);

                if (!azsInfo.get("success").getAsBoolean()) {
                    sendError(exchange, 404, "АЗС не найдена");
                    return;
                }

                // Генерируем простую текстовую информацию для QR
                String qrText = generateQrText(azsId, nozzleNumber, azsInfo);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("qr_text", qrText);
                response.addProperty("azs_id", azsId);
                response.addProperty("nozzle_number", nozzleNumber);
                response.addProperty("azs_name", azsInfo.get("name").getAsString());
                response.addProperty("address", azsInfo.get("address").getAsString());
                response.addProperty("timestamp", System.currentTimeMillis());

                sendJsonResponse(exchange, 200, response);

            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Некорректный числовой параметр");
            } catch (Exception e) {
                System.err.println("❌ Ошибка в QrCodeHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }

        private JsonObject getAzsInfo(int azsId) throws SQLException {
            JsonObject result = new JsonObject();

            String sql = "SELECT name, address FROM azs WHERE id = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, azsId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    result.addProperty("success", true);
                    result.addProperty("name", rs.getString("name"));
                    result.addProperty("address", rs.getString("address"));
                } else {
                    result.addProperty("success", false);
                    result.addProperty("error", "АЗС не найдена");
                }
            }

            return result;
        }

        private String generateQrText(int azsId, int nozzleNumber, JsonObject azsInfo) {
            // Формируем текст для QR-кода
            // Это просто текст, который потом можно будет использовать на сайте
            return String.format(
                    "АЗС: %s\n" +
                            "Адрес: %s\n" +
                            "Колонка: %d\n" +
                            "ID АЗС: %d\n" +
                            "ID Колонки: %d\n" +
                            "Время: %s\n" +
                            "Тип: QR для заправки\n" +
                            "Данные для сайта: azs_id=%d&nozzle=%d",
                    azsInfo.get("name").getAsString(),
                    azsInfo.get("address").getAsString(),
                    nozzleNumber,
                    azsId,
                    nozzleNumber,
                    new java.util.Date().toString(),
                    azsId,
                    nozzleNumber
            );
        }
    }



    // ========== ОБРАБОТЧИК АЗС ==========
    static class AzsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                logRequest(exchange);

                // Добавьте обработку OPTIONS запросов
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
                    exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if ("GET".equals(exchange.getRequestMethod())) {
                    System.out.println("📋 Запрос списка АЗС");

                    JsonArray azsList = new JsonArray();

                    // Проверяем подключение к БД
                    Connection conn = getConnection();
                    if (conn == null || conn.isClosed()) {
                        System.err.println("❌ Нет подключения к БД");
                        sendError(exchange, 500, "Нет подключения к базе данных");
                        return;
                    }

                    String sql = "SELECT id, name, address, nozzle_count FROM azs WHERE is_active = true ORDER BY id";

                    System.out.println("📋 SQL запрос: " + sql);

                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {

                        int count = 0;
                        while (rs.next()) {
                            count++;
                            JsonObject azs = new JsonObject();
                            azs.addProperty("id", rs.getInt("id"));
                            azs.addProperty("name", rs.getString("name"));
                            azs.addProperty("address", rs.getString("address"));
                            azs.addProperty("nozzle_count", rs.getInt("nozzle_count"));
                            azsList.add(azs);

                            System.out.println("📋 АЗС " + count + ": " + rs.getString("name") +
                                    " (" + rs.getString("address") + ")");
                        }

                        System.out.println("📋 Всего АЗС: " + count);
                    } catch (SQLException e) {
                        System.err.println("❌ Ошибка SQL: " + e.getMessage());
                        e.printStackTrace();
                        sendError(exchange, 500, "Ошибка базы данных: " + e.getMessage());
                        return;
                    }

                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.add("data", azsList);
                    response.addProperty("count", azsList.size());
                    response.addProperty("status", 200);

                    sendJsonResponse(exchange, 200, response);

                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }
            } catch (Exception e) {
                System.err.println("❌ Неожиданная ошибка в AzsHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Внутренняя ошибка сервера: " + e.getMessage());
            }
        }
    }



    // Добавьте класс обработчика отчетов:
    static class ReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                System.out.println("Запрос отчета: " + query);

                if (query == null) {
                    sendError(exchange, 400, "Не указаны параметры запроса");
                    return;
                }

                // Парсим параметры
                Map<String, String> params = parseQuery(query);

                if (!params.containsKey("azs_id") || !params.containsKey("start_date") || !params.containsKey("end_date")) {
                    sendError(exchange, 400, "Необходимы параметры: azs_id, start_date, end_date");
                    return;
                }

                int azsId = Integer.parseInt(params.get("azs_id"));
                String startDate = params.get("start_date");
                String endDate = params.get("end_date");

                System.out.println("Формирование отчета для АЗС " + azsId +
                        " с " + startDate + " по " + endDate);

                JsonObject reportData = generateReport(azsId, startDate, endDate);
                sendJsonResponse(exchange, 200, reportData);

            } catch (Exception e) {
                System.err.println("Ошибка формирования отчета: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка сервера: " + e.getMessage());
            }
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null) return params;

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
            return params;
        }

        private JsonObject generateReport(int azsId, String startDate, String endDate) throws SQLException {
            JsonObject report = new JsonObject();

            System.out.println("📊 Генерация отчета для АЗС ID: " + azsId);
            System.out.println("📊 Период: " + startDate + " - " + endDate);

            // Исправленный SQL запрос с правильными значениями полей
            String sql = "SELECT " +
                    "COUNT(*) as total_transactions, " +
                    "COALESCE(SUM(total_amount), 0) as total_revenue, " +
                    "COALESCE(SUM(liters), 0) as total_liters, " +
                    "COALESCE(SUM(CASE WHEN payment_method = 'Наличные' THEN total_amount ELSE 0 END), 0) as cash_revenue, " +
                    "COALESCE(SUM(CASE WHEN payment_method = 'Банковская карта' THEN total_amount ELSE 0 END), 0) as card_revenue, " +
                    "COALESCE(AVG(total_amount), 0) as average_sale " +
                    "FROM transactions " +
                    "WHERE azs_id = ? " +
                    "AND created_at::date >= ?::date " +
                    "AND created_at::date <= ?::date " +
                    "AND status = 'Успешно'";  // Только успешные транзакции

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, azsId);
                pstmt.setString(2, startDate);
                pstmt.setString(3, endDate);

                System.out.println("📊 SQL запрос: " + sql);
                System.out.println("📊 Параметры: azs_id=" + azsId + ", start_date=" + startDate + ", end_date=" + endDate);

                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    int totalTransactions = rs.getInt("total_transactions");
                    double totalRevenue = rs.getDouble("total_revenue");
                    double totalLiters = rs.getDouble("total_liters");
                    double cashRevenue = rs.getDouble("cash_revenue");
                    double cardRevenue = rs.getDouble("card_revenue");
                    double averageSale = rs.getDouble("average_sale");

                    report.addProperty("total_transactions", totalTransactions);
                    report.addProperty("total_revenue", totalRevenue);
                    report.addProperty("total_liters", totalLiters);
                    report.addProperty("cash_revenue", cashRevenue);
                    report.addProperty("card_revenue", cardRevenue);
                    report.addProperty("average_sale", averageSale);
                    report.addProperty("success", true);

                    System.out.println("📊 Отчет сформирован:");
                    System.out.println("  Транзакций: " + totalTransactions);
                    System.out.println("  Выручка: " + totalRevenue + " BYN");
                    System.out.println("  Литров: " + totalLiters + " л");
                    System.out.println("  Наличные: " + cashRevenue + " BYN");
                    System.out.println("  Карта: " + cardRevenue + " BYN");
                    System.out.println("  Средний чек: " + averageSale + " BYN");
                } else {
                    // Если нет данных
                    report.addProperty("total_transactions", 0);
                    report.addProperty("total_revenue", 0.0);
                    report.addProperty("total_liters", 0.0);
                    report.addProperty("cash_revenue", 0.0);
                    report.addProperty("card_revenue", 0.0);
                    report.addProperty("average_sale", 0.0);
                    report.addProperty("success", true);
                    System.out.println("📊 Нет данных за указанный период");
                }
            }

            // Самый популярный тип топлива
            String popularFuelSql = "SELECT fuel_type, COUNT(*) as count " +
                    "FROM transactions " +
                    "WHERE azs_id = ? " +
                    "AND created_at::date >= ?::date " +
                    "AND created_at::date <= ?::date " +
                    "AND status = 'Успешно' " +
                    "GROUP BY fuel_type " +
                    "ORDER BY count DESC " +
                    "LIMIT 1";

            try (PreparedStatement pstmt = getConnection().prepareStatement(popularFuelSql)) {
                pstmt.setInt(1, azsId);
                pstmt.setString(2, startDate);
                pstmt.setString(3, endDate);

                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String popularFuel = rs.getString("fuel_type");
                    report.addProperty("most_popular_fuel", popularFuel);
                    System.out.println("📊 Популярное топливо: " + popularFuel);
                } else {
                    report.addProperty("most_popular_fuel", "Нет данных");
                    System.out.println("📊 Популярное топливо: нет данных");
                }
            }

            // Статистика по типам топлива
            String fuelStatsSql = "SELECT " +
                    "COUNT(CASE WHEN fuel_type LIKE '%92%' THEN 1 END) as ai92_count, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%92%' THEN liters ELSE 0 END), 0) as ai92_liters, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%92%' THEN total_amount ELSE 0 END), 0) as ai92_revenue, " +
                    "COUNT(CASE WHEN fuel_type LIKE '%95%' THEN 1 END) as ai95_count, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%95%' THEN liters ELSE 0 END), 0) as ai95_liters, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%95%' THEN total_amount ELSE 0 END), 0) as ai95_revenue, " +
                    "COUNT(CASE WHEN fuel_type LIKE '%98%' THEN 1 END) as ai98_count, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%98%' THEN liters ELSE 0 END), 0) as ai98_liters, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%98%' THEN total_amount ELSE 0 END), 0) as ai98_revenue, " +
                    "COUNT(CASE WHEN fuel_type LIKE '%100%' THEN 1 END) as ai100_count, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%100%' THEN liters ELSE 0 END), 0) as ai100_liters, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%100%' THEN total_amount ELSE 0 END), 0) as ai100_revenue, " +
                    "COUNT(CASE WHEN fuel_type LIKE '%ДТ%' OR fuel_type LIKE '%Дизель%' THEN 1 END) as dt_count, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%ДТ%' OR fuel_type LIKE '%Дизель%' THEN liters ELSE 0 END), 0) as dt_liters, " +
                    "COALESCE(SUM(CASE WHEN fuel_type LIKE '%ДТ%' OR fuel_type LIKE '%Дизель%' THEN total_amount ELSE 0 END), 0) as dt_revenue " +
                    "FROM transactions " +
                    "WHERE azs_id = ? " +
                    "AND created_at::date >= ?::date " +
                    "AND created_at::date <= ?::date " +
                    "AND status = 'Успешно'";

            try (PreparedStatement pstmt = getConnection().prepareStatement(fuelStatsSql)) {
                pstmt.setInt(1, azsId);
                pstmt.setString(2, startDate);
                pstmt.setString(3, endDate);

                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    JsonObject fuelStats = new JsonObject();

                    // AI-92
                    fuelStats.addProperty("ai92_count", rs.getInt("ai92_count"));
                    fuelStats.addProperty("ai92_liters", rs.getDouble("ai92_liters"));
                    fuelStats.addProperty("ai92_revenue", rs.getDouble("ai92_revenue"));

                    // AI-95
                    fuelStats.addProperty("ai95_count", rs.getInt("ai95_count"));
                    fuelStats.addProperty("ai95_liters", rs.getDouble("ai95_liters"));
                    fuelStats.addProperty("ai95_revenue", rs.getDouble("ai95_revenue"));

                    // AI-98
                    fuelStats.addProperty("ai98_count", rs.getInt("ai98_count"));
                    fuelStats.addProperty("ai98_liters", rs.getDouble("ai98_liters"));
                    fuelStats.addProperty("ai98_revenue", rs.getDouble("ai98_revenue"));

                    // AI-100
                    fuelStats.addProperty("ai100_count", rs.getInt("ai100_count"));
                    fuelStats.addProperty("ai100_liters", rs.getDouble("ai100_liters"));
                    fuelStats.addProperty("ai100_revenue", rs.getDouble("ai100_revenue"));

                    // Дизель
                    fuelStats.addProperty("dt_count", rs.getInt("dt_count"));
                    fuelStats.addProperty("dt_liters", rs.getDouble("dt_liters"));
                    fuelStats.addProperty("dt_revenue", rs.getDouble("dt_revenue"));

                    report.add("fuel_statistics", fuelStats);
                }
            }

            return report;
        }
    }

    // ========== ОБРАБОТЧИК ТРАНЗАКЦИЙ ==========
    static class TransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String requestBody = readRequestBody(exchange);
                    System.out.println("📥 Получена транзакция: " + requestBody);

                    JsonObject transaction = gson.fromJson(requestBody, JsonObject.class);

                    // Сохраняем транзакцию в БД
                    boolean success = saveTransaction(transaction);

                    JsonObject response = new JsonObject();
                    if (success) {
                        response.addProperty("success", true);
                        response.addProperty("message", "Транзакция успешно сохранена");
                        System.out.println("✅ Транзакция сохранена успешно");
                        sendJsonResponse(exchange, 201, response);
                    } else {
                        response.addProperty("success", false);
                        response.addProperty("message", "Ошибка сохранения транзакции");
                        System.out.println("❌ Ошибка сохранения транзакции");
                        sendJsonResponse(exchange, 500, response);
                    }
                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка в TransactionsHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }

        private boolean saveTransaction(JsonObject transaction) {
            try {
                // Упрощенный SQL запрос без fuel_id
                String sql = "INSERT INTO transactions (" +
                        "user_id, azs_id, nozzle, fuel_type, " +
                        "liters, price_per_liter, total_amount, cash_in, " +
                        "change, bonus_amount, bonus_spent, payment_method, status, created_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setInt(1, transaction.get("user_id").getAsInt());
                    pstmt.setInt(2, transaction.get("azs_id").getAsInt());
                    pstmt.setInt(3, transaction.get("nozzle").getAsInt());
                    pstmt.setString(4, transaction.get("fuel_type").getAsString());
                    pstmt.setDouble(5, transaction.get("liters").getAsDouble());
                    pstmt.setDouble(6, transaction.get("price_per_liter").getAsDouble());
                    pstmt.setDouble(7, transaction.get("total_amount").getAsDouble());
                    pstmt.setDouble(8, transaction.get("cash_in").getAsDouble());
                    pstmt.setDouble(9, transaction.get("change").getAsDouble());
                    pstmt.setDouble(10, transaction.get("bonus_spent").getAsDouble()); // Для bonus_amount
                    pstmt.setDouble(11, transaction.get("bonus_spent").getAsDouble()); // Для bonus_spent
                    pstmt.setString(12, transaction.get("payment_method").getAsString());
                    pstmt.setString(13, transaction.get("status").getAsString());

                    // Используем текущее время сервера
                    pstmt.setTimestamp(14, new java.sql.Timestamp(System.currentTimeMillis()));

                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        // Получаем ID созданной транзакции
                        ResultSet generatedKeys = pstmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            int transactionId = generatedKeys.getInt(1);
                            transaction.addProperty("id", transactionId);
                            System.out.println("✅ Транзакция сохранена с ID: " + transactionId);
                        }

                        // Обновляем статистику пользователя если это не гость
                        int userId = transaction.get("user_id").getAsInt();
                        if (userId > 0) {
                            updateUserStats(userId, transaction);
                        }

                        System.out.println("✅ Транзакция сохранена в БД: " +
                                transaction.get("fuel_type").getAsString() + " - " +
                                transaction.get("liters").getAsDouble() + " л - " +
                                transaction.get("total_amount").getAsDouble() + " BYN");
                        return true;
                    }
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка SQL при сохранении транзакции: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("❌ Общая ошибка при сохранении транзакции: " + e.getMessage());
                e.printStackTrace();
            }

            return false;
        }

        // Добавить этот метод для получения ID топлива по названию
        private int getFuelIdByName(String fuelName) {
            String sql = "SELECT id FROM fuels WHERE name LIKE ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                // Преобразуем название топлива для поиска
                String searchName = "%" + fuelName + "%";
                pstmt.setString(1, searchName);

                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                } else {
                    // Если не найдено, возвращаем значение по умолчанию
                    System.out.println("⚠️ Топливо не найдено в БД: " + fuelName + ", используем ID=1");
                    return 1; // Значение по умолчанию
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка получения ID топлива: " + e.getMessage());
                return 1; // Значение по умолчанию в случае ошибки
            }
        }



        private void updateUserStats(int userId, JsonObject transaction) {
            String updateSql = "UPDATE users SET " +
                    "balance = balance - ? + ?, " + // Списание бонусов и начисление новых
                    "total_spent = total_spent + ?, " +
                    "total_liters = total_liters + ? " +
                    "WHERE id = ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(updateSql)) {
                double bonusSpent = transaction.get("bonus_spent").getAsDouble();
                double totalAmount = transaction.get("total_amount").getAsDouble();
                double bonusEarned = totalAmount * 0.01; // 1% от суммы

                pstmt.setDouble(1, bonusSpent);
                pstmt.setDouble(2, bonusEarned);
                pstmt.setDouble(3, totalAmount);
                pstmt.setDouble(4, transaction.get("liters").getAsDouble());
                pstmt.setInt(5, userId);

                pstmt.executeUpdate();
                System.out.println("✅ Статистика пользователя ID " + userId + " обновлена");
            } catch (SQLException e) {
                System.err.println("❌ Ошибка обновления статистики пользователя: " + e.getMessage());
                e.printStackTrace();
            }
        }

    }

    // ========== ОБРАБОТЧИК ПОИСКА ПОЛЬЗОВАТЕЛЯ ПО ТЕЛЕФОНУ ==========
    static class UserSearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String query = exchange.getRequestURI().getQuery();
                System.out.println("🔍 Поиск пользователя по запросу: " + query);

                if (query == null || !query.contains("phone=")) {
                    sendError(exchange, 400, "Не указан номер телефона");
                    return;
                }

                // Извлекаем номер телефона
                String phone = "";
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("phone=")) {
                        phone = param.substring(6);
                        break;
                    }
                }

                if (phone.isEmpty()) {
                    sendError(exchange, 400, "Не указан номер телефона");
                    return;
                }

                // Ищем пользователя в БД
                JsonObject user = findUserByPhone(phone);

                JsonObject response = new JsonObject();
                if (user != null) {
                    response.addProperty("success", true);
                    response.add("user", user);
                    System.out.println("✅ Пользователь найден: " + user.get("name").getAsString());
                } else {
                    response.addProperty("success", false);
                    response.addProperty("message", "Пользователь не найден");
                    System.out.println("❌ Пользователь не найден по телефону: " + phone);
                }

                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                System.err.println("❌ Ошибка в UserSearchHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }

        private JsonObject findUserByPhone(String phone) {
            String sql = "SELECT id, username, phone, name, balance, " +
                    "total_spent, total_liters " +
                    "FROM users WHERE phone = ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, phone);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("id", rs.getInt("id"));
                    user.addProperty("username", rs.getString("username"));
                    user.addProperty("phone", rs.getString("phone"));
                    user.addProperty("name", rs.getString("name"));
                    user.addProperty("balance", rs.getDouble("balance"));
                    user.addProperty("total_spent", rs.getDouble("total_spent"));
                    user.addProperty("total_liters", rs.getDouble("total_liters"));
                    return user;
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка поиска пользователя: " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }
    }

    // ========== ОБРАБОТЧИК ОБНОВЛЕНИЯ БАЛАНСА ПОЛЬЗОВАТЕЛЯ ==========
    static class UserBalanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                // Извлекаем ID пользователя из URL
                String path = exchange.getRequestURI().getPath();
                System.out.println("🔄 Обновление баланса по пути: " + path);

                String[] parts = path.split("/");
                if (parts.length < 5) {
                    sendError(exchange, 400, "Неверный URL");
                    return;
                }

                int userId = Integer.parseInt(parts[3]); // /api/users/{id}/update-balance

                if ("POST".equals(exchange.getRequestMethod())) {
                    String requestBody = readRequestBody(exchange);
                    JsonObject updateData = gson.fromJson(requestBody, JsonObject.class);

                    // Обновляем баланс пользователя
                    boolean success = updateUserBalance(userId, updateData);

                    JsonObject response = new JsonObject();
                    if (success) {
                        response.addProperty("success", true);
                        response.addProperty("message", "Баланс обновлен");
                        System.out.println("✅ Баланс пользователя ID " + userId + " обновлен");
                    } else {
                        response.addProperty("success", false);
                        response.addProperty("message", "Ошибка обновления баланса");
                        System.out.println("❌ Ошибка обновления баланса пользователя ID " + userId);
                    }

                    sendJsonResponse(exchange, 200, response);
                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Неверный ID пользователя");
            } catch (Exception e) {
                System.err.println("❌ Ошибка в UserBalanceHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }

        private boolean updateUserBalance(int userId, JsonObject updateData) {
            String sql = "UPDATE users SET " +
                    "balance = balance - ? + ?, " + // bonus_spent + bonus_earned
                    "total_spent = total_spent + ?, " +
                    "total_liters = total_liters + ? " +
                    "WHERE id = ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setDouble(1, updateData.get("bonus_spent").getAsDouble());
                pstmt.setDouble(2, updateData.get("bonus_earned").getAsDouble());
                pstmt.setDouble(3, updateData.get("total_spent_increment").getAsDouble());
                pstmt.setDouble(4, updateData.get("total_liters_increment").getAsDouble());
                pstmt.setInt(5, userId);

                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    System.out.println("✅ Баланс пользователя ID " + userId + " обновлен:");
                    System.out.println("   Списано бонусов: " + updateData.get("bonus_spent").getAsDouble());
                    System.out.println("   Начислено бонусов: " + updateData.get("bonus_earned").getAsDouble());
                    System.out.println("   Добавлено к потраченному: " + updateData.get("total_spent_increment").getAsDouble());
                    System.out.println("   Добавлено литров: " + updateData.get("total_liters_increment").getAsDouble());
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка обновления баланса пользователя: " + e.getMessage());
                e.printStackTrace();
            }

            return false;
        }
    }

    // ========== ОБРАБОТЧИК ЧЕКОВ ==========
    static class ReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // В начале метода handle каждого обработчика
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String requestBody = readRequestBody(exchange);
                    System.out.println("🧾 Получен запрос на генерацию чека: " + requestBody);

                    JsonObject transactionData = gson.fromJson(requestBody, JsonObject.class);

                    // Генерируем чек
                    JsonObject receipt = generateReceipt(transactionData);

                    // Сохраняем чек в базу данных
                    saveReceipt(receipt);

                    // Отправляем чек в ответе
                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.add("receipt", receipt);

                    sendJsonResponse(exchange, 200, response);
                    System.out.println("✅ Чек сгенерирован и сохранен");

                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка в ReceiptHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }

        private JsonObject generateReceipt(JsonObject transactionData) {
            JsonObject receipt = new JsonObject();

            // Генерируем уникальный номер чека
            String receiptNumber = generateReceiptNumber();

            // Добавляем информацию о транзакции
            receipt.addProperty("receipt_number", receiptNumber);

            // Добавляем transaction_id с безопасной проверкой
            if (transactionData.has("id") && !transactionData.get("id").isJsonNull()) {
                receipt.addProperty("transaction_id", transactionData.get("id").getAsInt());
            } else {
                receipt.addProperty("transaction_id", 0); // Значение по умолчанию
            }

            // Дата с безопасной проверкой
            if (transactionData.has("created_at") && !transactionData.get("created_at").isJsonNull()) {
                receipt.addProperty("date", transactionData.get("created_at").getAsString());
                receipt.addProperty("created_at", transactionData.get("created_at").getAsString());
            } else {
                String currentDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                receipt.addProperty("date", currentDate);
                receipt.addProperty("created_at", currentDate);
            }

            // Информация о топливе с безопасными проверками
            receipt.addProperty("fuel_type", getStringValue(transactionData, "fuel_type", "Не указано"));
            receipt.addProperty("liters", getDoubleValue(transactionData, "liters", 0.0));
            receipt.addProperty("price_per_liter", getDoubleValue(transactionData, "price_per_liter", 0.0));
            receipt.addProperty("total_amount", getDoubleValue(transactionData, "total_amount", 0.0));

            // Информация об оплате
            receipt.addProperty("payment_method", getStringValue(transactionData, "payment_method", "Не указано"));
            receipt.addProperty("cash_in", getDoubleValue(transactionData, "cash_in", 0.0));
            receipt.addProperty("change", getDoubleValue(transactionData, "change", 0.0));

            // Информация о пользователе
            int userId = getIntValue(transactionData, "user_id", 0);
            String userName = "Гость";
            if (userId > 0) {
                userName = getUserName(userId);
            }
            receipt.addProperty("user_name", userName);
            receipt.addProperty("user_id", userId);

            // Информация об АЗС
            int azsId = getIntValue(transactionData, "azs_id", 0);
            receipt.addProperty("azs_id", azsId);
            receipt.addProperty("azs_name", getAZSName(azsId));
            receipt.addProperty("nozzle", getIntValue(transactionData, "nozzle", 0));

            // Бонусная система
            receipt.addProperty("bonus_spent", getDoubleValue(transactionData, "bonus_spent", 0.0));
            receipt.addProperty("bonus_earned", calculateBonusEarned(getDoubleValue(transactionData, "total_amount", 0.0)));

            // QR код для проверки чека
            int transactionId = getIntValue(transactionData, "id", 0);
            receipt.addProperty("qr_code_data", generateQRCodeData(receiptNumber, transactionId));

            // Форматированный текст чека для печати
            String formattedReceipt = formatReceiptText(receipt);
            receipt.addProperty("formatted_text", formattedReceipt);

            // Статус
            receipt.addProperty("status", "Успешно");

            return receipt;
        }

        // Вспомогательные методы для безопасного получения значений
        private String getStringValue(JsonObject json, String key, String defaultValue) {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsString();
            }
            return defaultValue;
        }

        private int getIntValue(JsonObject json, String key, int defaultValue) {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsInt();
            }
            return defaultValue;
        }

        private double getDoubleValue(JsonObject json, String key, double defaultValue) {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsDouble();
            }
            return defaultValue;
        }

        private String generateReceiptNumber() {
            // Формат: R-20251207-0001 (R-YYYYMMDD-последовательность)
            String date = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());

            // Получаем последний номер чека за сегодня
            String sql = "SELECT receipt_number FROM receipts WHERE receipt_number LIKE ? ORDER BY id DESC LIMIT 1";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, "R-" + date + "-%");
                ResultSet rs = pstmt.executeQuery();

                int sequence = 1;
                if (rs.next()) {
                    String lastNumber = rs.getString("receipt_number");
                    String[] parts = lastNumber.split("-");
                    if (parts.length == 3) {
                        sequence = Integer.parseInt(parts[2]) + 1;
                    }
                }

                return String.format("R-%s-%04d", date, sequence);

            } catch (Exception e) {
                // Если ошибка, генерируем случайный номер
                return String.format("R-%s-%04d", date, (int)(Math.random() * 1000) + 1);
            }
        }

        private String getUserName(int userId) {
            String sql = "SELECT name FROM users WHERE id = ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    return rs.getString("name");
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка получения имени пользователя: " + e.getMessage());
            }

            return "Клиент";
        }

        private String getAZSName(int azsId) {
            String sql = "SELECT name FROM azs WHERE id = ?";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, azsId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    return rs.getString("name");
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка получения названия АЗС: " + e.getMessage());
            }

            return "АЗС №" + azsId;
        }

        private double calculateBonusEarned(double totalAmount) {
            // Начисляем 1% от суммы покупки в качестве бонусов
            return Math.round((totalAmount * 0.01) * 100.0) / 100.0;
        }

        private String generateQRCodeData(String receiptNumber, int transactionId) {
            // Генерируем данные для QR кода
            return String.format("AZS-RECEIPT:%s:%d:%d",
                    receiptNumber,
                    transactionId,
                    System.currentTimeMillis());
        }

        private String formatReceiptText(JsonObject receipt) {
            StringBuilder sb = new StringBuilder();

            sb.append("================================\n");
            sb.append("           ЧЕК ОПЛАТЫ           \n");
            sb.append("================================\n");
            sb.append("Номер чека: ").append(receipt.get("receipt_number").getAsString()).append("\n");
            sb.append("Дата: ").append(formatDateTime(receipt.get("date").getAsString())).append("\n");
            sb.append("--------------------------------\n");
            sb.append("АЗС: ").append(receipt.get("azs_name").getAsString()).append("\n");
            sb.append("Колонка: ").append(receipt.get("nozzle").getAsInt()).append("\n");
            sb.append("--------------------------------\n");
            sb.append("Топливо: ").append(receipt.get("fuel_type").getAsString()).append("\n");
            sb.append("Литров: ").append(String.format("%.2f", receipt.get("liters").getAsDouble())).append("\n");
            sb.append("Цена за литр: ").append(String.format("%.2f", receipt.get("price_per_liter").getAsDouble())).append(" руб.\n");
            sb.append("--------------------------------\n");
            sb.append("Сумма: ").append(String.format("%.2f", receipt.get("total_amount").getAsDouble())).append(" руб.\n");
            sb.append("Оплата: ").append(receipt.get("payment_method").getAsString()).append("\n");

            if (receipt.get("payment_method").getAsString().equals("Наличные")) {
                sb.append("Внесено: ").append(String.format("%.2f", receipt.get("cash_in").getAsDouble())).append(" руб.\n");
                sb.append("Сдача: ").append(String.format("%.2f", receipt.get("change").getAsDouble())).append(" руб.\n");
            }

            sb.append("--------------------------------\n");
            sb.append("Клиент: ").append(receipt.get("user_name").getAsString()).append("\n");

            if (receipt.get("bonus_spent").getAsDouble() > 0) {
                sb.append("Списано бонусов: ").append(String.format("%.2f", receipt.get("bonus_spent").getAsDouble())).append("\n");
            }

            sb.append("Начислено бонусов: ").append(String.format("%.2f", receipt.get("bonus_earned").getAsDouble())).append("\n");
            sb.append("================================\n");
            sb.append(" Спасибо за покупку!\n");
            sb.append(" QR код для проверки:\n");
            sb.append(" ").append(receipt.get("qr_code_data").getAsString()).append("\n");
            sb.append("================================\n");

            return sb.toString();
        }

        private String formatDateTime(String dateTimeStr) {
            try {
                java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(dateTimeStr);
                return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            } catch (Exception e) {
                return dateTimeStr;
            }
        }

        private void saveReceipt(JsonObject receipt) {
            String sql = "INSERT INTO receipts (" +
                    "receipt_number, transaction_id, azs_id, user_id, " +
                    "fuel_type, liters, price_per_liter, total_amount, " +
                    "payment_method, cash_in, change, bonus_spent, " +
                    "bonus_earned, receipt_text, qr_code_data, status, " +
                    "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, receipt.get("receipt_number").getAsString());
                pstmt.setInt(2, receipt.get("transaction_id").getAsInt());
                pstmt.setInt(3, receipt.get("azs_id").getAsInt());
                pstmt.setInt(4, receipt.get("user_id").getAsInt());
                pstmt.setString(5, receipt.get("fuel_type").getAsString());
                pstmt.setDouble(6, receipt.get("liters").getAsDouble());
                pstmt.setDouble(7, receipt.get("price_per_liter").getAsDouble());
                pstmt.setDouble(8, receipt.get("total_amount").getAsDouble());
                pstmt.setString(9, receipt.get("payment_method").getAsString());
                pstmt.setDouble(10, receipt.get("cash_in").getAsDouble());
                pstmt.setDouble(11, receipt.get("change").getAsDouble());
                pstmt.setDouble(12, receipt.get("bonus_spent").getAsDouble());
                pstmt.setDouble(13, receipt.get("bonus_earned").getAsDouble());
                pstmt.setString(14, receipt.get("formatted_text").getAsString());
                pstmt.setString(15, receipt.get("qr_code_data").getAsString());
                pstmt.setString(16, receipt.get("status").getAsString());
                pstmt.setTimestamp(17, new java.sql.Timestamp(System.currentTimeMillis()));

                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    System.out.println("✅ Чек сохранен в БД: " + receipt.get("receipt_number").getAsString());
                } else {
                    System.err.println("❌ Не удалось сохранить чек в БД");
                }
            } catch (SQLException e) {
                System.err.println("❌ Ошибка сохранения чека: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ========== ОБРАБОТЧИК ТОПЛИВА ==========
    static class FuelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JsonObject response = new JsonObject();

                String sql = "SELECT name, price FROM fuels ORDER BY id";

                try (Statement stmt = getConnection().createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    JsonObject fuelData = new JsonObject();

                    while (rs.next()) {
                        String name = rs.getString("name");
                        double price = rs.getDouble("price");

                        // Преобразуем имя топлива в нужный формат
                        String key = convertFuelNameToKey(name);
                        if (key != null) {
                            fuelData.addProperty(key, String.format("%.2f", price));
                            fuelData.addProperty(key + "_raw", price);
                        }
                    }

                    response.addProperty("success", true);
                    response.add("data", fuelData);
                    System.out.println("✅ Цены на топливо загружены из БД");

                } catch (SQLException e) {
                    response.addProperty("success", false);
                    response.addProperty("message", "Ошибка БД: " + e.getMessage());
                    System.err.println("❌ Ошибка загрузки цен на топливо: " + e.getMessage());
                }

                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Ошибка: " + e.getMessage());
                sendJsonResponse(exchange, 500, error);
                e.printStackTrace();
            }
        }

        private String convertFuelNameToKey(String name) {
            if (name == null) return null;

            name = name.toLowerCase().trim();

            // Определяем тип топлива по названию
            if (name.contains("92") || name.contains("аи-92") || name.contains("аи92")) {
                return "ai92";
            } else if (name.contains("95") || name.contains("аи-95") || name.contains("аи95")) {
                return "ai95";
            } else if (name.contains("98") || name.contains("аи-98") || name.contains("аи98")) {
                return "ai98";
            } else if (name.contains("100") || name.contains("аи-100") || name.contains("аи100")) {
                return "ai100";
            } else if (name.contains("дт") || name.contains("дизель") || name.contains("diesel")) {
                return "dt";
            } else if (name.contains("дтк-5") || name.contains("дтк5")) {
                return "dtk5";
            }

            return null;
        }
    }

    static class NozzlesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                System.out.println("🔍 Получен запрос: " + path + " | Метод: " + exchange.getRequestMethod()); // DEBUG

                // Убираем /api/azs/ из начала пути
                String relativePath = path.replace("/api/azs/", "");
                System.out.println("🔍 Относительный путь: " + relativePath); // DEBUG

                String[] parts = relativePath.split("/");

                if (parts.length == 1) {
                    // Это /api/azs/{id}
                    int azsId = Integer.parseInt(parts[0]);
                    if (exchange.getRequestMethod().equals("GET")) {
                        handleGetAzsDetails(exchange, azsId);
                    } else {
                        sendError(exchange, 405, "Метод не поддерживается");
                    }
                } else if (parts.length >= 2 && parts[1].equals("nozzles")) {
                    int azsId = Integer.parseInt(parts[0]);

                    if (parts.length == 2) {
                        // /api/azs/{id}/nozzles
                        if (exchange.getRequestMethod().equals("GET")) {
                            handleGetNozzles(exchange, azsId);
                        } else {
                            sendError(exchange, 405, "Метод не поддерживается");
                        }
                    } else if (parts.length == 3) {
                        // /api/azs/{id}/nozzles/{nozzleNumber}
                        int nozzleNumber = Integer.parseInt(parts[2]);
                        if (exchange.getRequestMethod().equals("PUT")) {
                            handleUpdateNozzle(exchange, azsId, nozzleNumber);
                        } else {
                            sendError(exchange, 405, "Метод не поддерживается");
                        }
                    } else {
                        sendError(exchange, 404, "Неверный URL");
                    }
                } else {
                    sendError(exchange, 404, "Неверный URL");
                }

            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Некорректный числовой параметр: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("❌ Ошибка в NozzlesHandler: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Ошибка: " + e.getMessage());
            }
        }

        private void handleGetAzsDetails(HttpExchange exchange, int azsId) throws IOException, SQLException {
            System.out.println("Получение деталей АЗС ID: " + azsId);

            String sql = "SELECT id, name, address, nozzle_count FROM azs WHERE id = ?";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setInt(1, azsId);

                try (ResultSet rs = stmt.executeQuery()) {
                    JsonObject response = new JsonObject();
                    if (rs.next()) {
                        JsonObject azs = new JsonObject();
                        azs.addProperty("id", rs.getInt("id"));
                        azs.addProperty("name", rs.getString("name"));
                        azs.addProperty("address", rs.getString("address"));
                        azs.addProperty("nozzle_count", rs.getInt("nozzle_count"));

                        response.addProperty("success", true);
                        response.add("azs", azs);
                    } else {
                        response.addProperty("success", false);
                        response.addProperty("message", "АЗС не найдена");
                    }

                    sendJsonResponse(exchange, 200, response);
                }
            }
        }

        private void handleGetNozzles(HttpExchange exchange, int azsId) throws IOException, SQLException {
            System.out.println("Получение статуса колонок для АЗС ID: " + azsId);

            JsonObject response = new JsonObject();

            String sql = "SELECT nozzle_1, nozzle_2, nozzle_3, nozzle_4, nozzle_count " +
                    "FROM azs WHERE id = ?";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setInt(1, azsId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        JsonObject nozzles = new JsonObject();
                        nozzles.addProperty("nozzle_1", rs.getString("nozzle_1"));
                        nozzles.addProperty("nozzle_2", rs.getString("nozzle_2"));
                        nozzles.addProperty("nozzle_3", rs.getString("nozzle_3"));
                        nozzles.addProperty("nozzle_4", rs.getString("nozzle_4"));
                        nozzles.addProperty("nozzle_count", rs.getInt("nozzle_count"));

                        response.addProperty("success", true);
                        response.add("nozzles", nozzles);

                        System.out.println("✅ Отправлены данные колонок для АЗС " + azsId + ":");
                        System.out.println("   Колонка 1: " + rs.getString("nozzle_1"));
                        System.out.println("   Колонка 2: " + rs.getString("nozzle_2"));
                        System.out.println("   Колонка 3: " + rs.getString("nozzle_3"));
                        System.out.println("   Колонка 4: " + rs.getString("nozzle_4"));
                        System.out.println("   Всего колонок: " + rs.getInt("nozzle_count"));
                    } else {
                        response.addProperty("success", false);
                        response.addProperty("message", "АЗС не найдена");
                        System.out.println("❌ АЗС с ID " + azsId + " не найдена");
                    }
                }
            }

            sendJsonResponse(exchange, 200, response);
        }

        private void handleUpdateNozzle(HttpExchange exchange, int azsId, int nozzleNumber)
                throws IOException, SQLException {

            System.out.println("Обновление колонки " + nozzleNumber + " для АЗС " + azsId);

            // Читаем тело запроса
            String body = readRequestBody(exchange);
            JsonObject request = gson.fromJson(body, JsonObject.class);
            String newStatus = request.get("status").getAsString();

            // Проверяем допустимые статусы
            if (!isValidNozzleStatus(newStatus)) {
                sendError(exchange, 400, "Неверный статус колонки. Допустимые: active, not_active, not_available");
                return;
            }

            // Проверяем существует ли колонка
            String checkSql = "SELECT nozzle_count FROM azs WHERE id = ?";
            try (PreparedStatement checkStmt = getConnection().prepareStatement(checkSql)) {
                checkStmt.setInt(1, azsId);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    int nozzleCount = rs.getInt("nozzle_count");
                    if (nozzleNumber > nozzleCount) {
                        sendError(exchange, 400, "У АЗС только " + nozzleCount + " колонок");
                        return;
                    }
                } else {
                    sendError(exchange, 404, "АЗС не найдена");
                    return;
                }
            }

            // Определяем какое поле обновлять
            String columnName;
            switch (nozzleNumber) {
                case 1: columnName = "nozzle_1"; break;
                case 2: columnName = "nozzle_2"; break;
                case 3: columnName = "nozzle_3"; break;
                case 4: columnName = "nozzle_4"; break;
                default:
                    sendError(exchange, 400, "Неверный номер колонки. Допустимые: 1-4");
                    return;
            }

            String sql = "UPDATE azs SET " + columnName + " = ? WHERE id = ?";

            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, newStatus);
                stmt.setInt(2, azsId);

                int rowsAffected = stmt.executeUpdate();

                JsonObject response = new JsonObject();
                if (rowsAffected > 0) {
                    response.addProperty("success", true);
                    response.addProperty("message", "Статус колонки обновлен");
                    System.out.println("✅ Статус колонки " + nozzleNumber + " обновлен на " + newStatus);
                } else {
                    response.addProperty("success", false);
                    response.addProperty("message", "Не удалось обновить статус");
                    System.out.println("❌ Не удалось обновить статус колонки");
                }

                sendJsonResponse(exchange, 200, response);
            }
        }

        private boolean isValidNozzleStatus(String status) {
            return status.equals("active") ||
                    status.equals("not_active") ||
                    status.equals("not_available");
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
        try {
            String responseJson = gson.toJson(response);

            // ВСЕГДА добавляем CORS заголовки
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

            // Отправляем заголовки
            exchange.sendResponseHeaders(statusCode, responseJson.getBytes().length);

            // Отправляем тело ответа
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseJson.getBytes());
            }

            System.out.println("✅ Ответ отправлен: " + statusCode + " - " + responseJson);

        } catch (Exception e) {
            System.err.println("❌ Ошибка отправки ответа: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        try {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", message);
            error.addProperty("status", statusCode);
            sendJsonResponse(exchange, statusCode, error);
        } catch (Exception e) {
            System.err.println("❌ Ошибка отправки ошибки: " + e.getMessage());
        }
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