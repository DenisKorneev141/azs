package com.azs;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
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
            server.createContext("/api/azs/", new NozzlesHandler());
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

            server.createContext("/api/operators", new OperatorsHandler());

            server.createContext("/api/users", new UsersHandler());

            server.createContext("/api/transactions/recent", new RecentTransactionsHandler());

            // ВАЖНО: Создать контекст для колонок
            server.createContext("/api/azs/", new NozzlesHandler());

            server.createContext("/api/health", new HealthHandler());

            server.setExecutor(null);
            server.start();
            isRunning.set(true);

            System.out.println("\n✅ Сервер запущен на порту: " + PORT);
            System.out.println("🌐 Доступ по: http://localhost:" + PORT);

            connectToDatabase();

        } catch (IOException e) {
            System.err.println("❌ Ошибка запуска сервера: " + e.getMessage());
            e.printStackTrace();
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