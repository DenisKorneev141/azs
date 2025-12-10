package com.azs;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReceiptHandler implements HttpHandler {
    private static final Gson gson = new Gson();

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
                // Читаем тело запроса
                String requestBody = readRequestBody(exchange);
                System.out.println("🧾 Получен запрос на генерацию чека: " + requestBody);

                JsonObject transactionData = gson.fromJson(requestBody, JsonObject.class);

                // Проверяем наличие обязательных полей
                if (!validateTransactionData(transactionData)) {
                    sendError(exchange, 400, "Отсутствуют обязательные поля в данных транзакции");
                    return;
                }

                // Генерируем чек
                JsonObject receipt = generateReceipt(transactionData);

                // Сохраняем чек в базу данных
                saveReceiptToDatabase(receipt);

                // Отправляем ответ
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("receipt", receipt);

                // Отправляем JSON ответ
                sendJsonResponse(exchange, 200, response);

                System.out.println("✅ Чек сгенерирован и сохранен: " + receipt.get("receipt_number").getAsString());

            } else {
                // Метод не поддерживается
                sendError(exchange, 405, "Метод не поддерживается");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка в ReceiptHandler: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Ошибка: " + e.getMessage());
        }
    }

    private boolean validateTransactionData(JsonObject transactionData) {
        // Проверяем наличие обязательных полей
        String[] requiredFields = {
                "fuel_type", "azs_id", "user_id", "nozzle",
                "liters", "price_per_liter", "total_amount",
                "payment_method", "created_at"
        };

        for (String field : requiredFields) {
            if (!transactionData.has(field) || transactionData.get(field).isJsonNull()) {
                System.err.println("❌ Отсутствует обязательное поле: " + field);
                return false;
            }
        }
        return true;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        return requestBody.toString();
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject response) throws IOException {
        String responseJson = gson.toJson(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, responseJson.getBytes().length);

        OutputStream os = exchange.getResponseBody();
        os.write(responseJson.getBytes());
        os.close();
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("message", message);
        sendJsonResponse(exchange, statusCode, error);
    }

    private JsonObject generateReceipt(JsonObject transactionData) {
        JsonObject receipt = new JsonObject();

        // Используйте безопасные методы получения значений
        String receiptNumber = generateReceiptNumber();
        receipt.addProperty("receipt_number", receiptNumber);

        // ВАЖНО: Используйте getIntValue вместо direct getAsInt()
        receipt.addProperty("transaction_id", getIntValue(transactionData, "id", 0));

        // Копируем основные данные из транзакции
        receipt.addProperty("fuel_type", transactionData.get("fuel_type").getAsString());
        receipt.addProperty("azs_id", transactionData.get("azs_id").getAsInt());
        receipt.addProperty("user_id", transactionData.get("user_id").getAsInt());
        receipt.addProperty("nozzle", transactionData.get("nozzle").getAsInt());
        receipt.addProperty("liters", transactionData.get("liters").getAsDouble());
        receipt.addProperty("price_per_liter", transactionData.get("price_per_liter").getAsDouble());
        receipt.addProperty("total_amount", transactionData.get("total_amount").getAsDouble());
        receipt.addProperty("payment_method", transactionData.get("payment_method").getAsString());

        // Добавляем дополнительные поля с проверками
        if (transactionData.has("cash_in") && !transactionData.get("cash_in").isJsonNull()) {
            receipt.addProperty("cash_in", transactionData.get("cash_in").getAsDouble());
        } else {
            receipt.addProperty("cash_in", 0.0);
        }

        if (transactionData.has("change") && !transactionData.get("change").isJsonNull()) {
            receipt.addProperty("change", transactionData.get("change").getAsDouble());
        } else {
            receipt.addProperty("change", 0.0);
        }

        if (transactionData.has("bonus_spent") && !transactionData.get("bonus_spent").isJsonNull()) {
            receipt.addProperty("bonus_spent", transactionData.get("bonus_spent").getAsDouble());
        } else {
            receipt.addProperty("bonus_spent", 0.0);
        }

        // Добавляем transaction_id с проверкой
        if (transactionData.has("id") && !transactionData.get("id").isJsonNull()) {
            receipt.addProperty("transaction_id", transactionData.get("id").getAsInt());
        } else {
            receipt.addProperty("transaction_id", 0); // Значение по умолчанию
        }

        // Получаем имя АЗС из базы данных
        int azsId = getIntValue(transactionData, "azs_id", 0);
        String azsName = getAZSName(azsId);
        receipt.addProperty("azs_name", azsName);

        // Получаем имя пользователя
        int userId = getIntValue(transactionData, "user_id", 0);
        String userName = "Гость";
        if (userId > 0) {
            userName = getUserName(userId);
        }
        receipt.addProperty("user_name", userName);

        // Рассчитываем начисленные бонусы (1% от суммы)
        double totalAmount = getDoubleValue(transactionData, "total_amount", 0.0);
        double bonusEarned = Math.round((totalAmount * 0.01) * 100.0) / 100.0;
        receipt.addProperty("bonus_earned", bonusEarned);

        // Генерируем QR код
        int transactionId = transactionData.has("id") && !transactionData.get("id").isJsonNull() ?
                transactionData.get("id").getAsInt() : 0;
        String qrCodeData = generateQRCodeData(receiptNumber, transactionId);
        receipt.addProperty("qr_code_data", qrCodeData);

        // Создаем форматированный текст чека
        String formattedReceipt = formatReceiptText(receipt);
        receipt.addProperty("formatted_text", formattedReceipt);

        // Статус
        receipt.addProperty("status", "Успешно");

        return receipt;
    }

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
        // Формат: R-YYYYMMDD-XXXX
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Получаем последний номер чека за сегодня
            String sql = "SELECT receipt_number FROM receipts WHERE receipt_number LIKE ? ORDER BY id DESC LIMIT 1";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "R-" + date + "-%");
                ResultSet rs = pstmt.executeQuery();

                int sequence = 1;
                if (rs.next()) {
                    String lastNumber = rs.getString("receipt_number");
                    String[] parts = lastNumber.split("-");
                    if (parts.length == 3) {
                        try {
                            sequence = Integer.parseInt(parts[2]) + 1;
                        } catch (NumberFormatException e) {
                            sequence = 1;
                        }
                    }
                }

                return String.format("R-%s-%04d", date, sequence);
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка генерации номера чека, используем временный: " + e.getMessage());
            long timestamp = System.currentTimeMillis() % 10000;
            return String.format("R-%s-%04d", date, timestamp);
        }
    }

    private String getAZSName(int azsId) {
        if (azsId <= 0) return "АЗС не указана";

        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "SELECT name FROM azs WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, azsId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения названия АЗС: " + e.getMessage());
        }

        return "АЗС №" + azsId;
    }

    private String getUserName(int userId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "SELECT name FROM users WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения имени пользователя: " + e.getMessage());
        }

        return "Клиент";
    }



    private void saveReceiptToDatabase(JsonObject receipt) {
        String sql = "INSERT INTO receipts (" +
                "receipt_number, transaction_id, azs_id, user_id, " +
                "fuel_type, liters, price_per_liter, total_amount, " +
                "payment_method, cash_in, change, bonus_spent, " +
                "bonus_earned, receipt_text, qr_code_data, status, " +
                "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, receipt.get("receipt_number").getAsString());

            // transaction_id может быть null
            if (receipt.has("transaction_id") && !receipt.get("transaction_id").isJsonNull()) {
                pstmt.setInt(2, receipt.get("transaction_id").getAsInt());
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }

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

            // Преобразуем строку даты в Timestamp
            String createdAtStr = receipt.get("created_at").getAsString();
            try {
                java.time.LocalDateTime localDateTime = java.time.LocalDateTime.parse(createdAtStr);
                pstmt.setTimestamp(17, java.sql.Timestamp.valueOf(localDateTime));
            } catch (Exception e) {
                // Если не парсится, используем текущее время
                pstmt.setTimestamp(17, new java.sql.Timestamp(System.currentTimeMillis()));
            }

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

    private String generateQRCodeData(String receiptNumber, int transactionId) {
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
        sb.append("Дата: ").append(receipt.get("date").getAsString()).append("\n");
        sb.append("--------------------------------\n");
        sb.append("АЗС: ").append(receipt.get("azs_name").getAsString()).append("\n");

        if (receipt.has("nozzle")) {
            sb.append("Колонка: ").append(receipt.get("nozzle").getAsInt()).append("\n");
        }

        sb.append("--------------------------------\n");

        sb.append("Топливо: ").append(receipt.get("fuel_type").getAsString()).append("\n");
        sb.append("Литров: ").append(String.format("%.2f", receipt.get("liters").getAsDouble())).append("\n");
        sb.append("Цена за литр: ").append(String.format("%.2f", receipt.get("price_per_liter").getAsDouble())).append(" BYN\n");

        sb.append("--------------------------------\n");

        sb.append("Сумма: ").append(String.format("%.2f", receipt.get("total_amount").getAsDouble())).append(" BYN\n");
        sb.append("Оплата: ").append(receipt.get("payment_method").getAsString()).append("\n");

        if (receipt.get("payment_method").getAsString().equals("Наличные")) {
            sb.append("Внесено: ").append(String.format("%.2f", receipt.get("cash_in").getAsDouble())).append(" BYN\n");
            sb.append("Сдача: ").append(String.format("%.2f", receipt.get("change").getAsDouble())).append(" BYN\n");
        }

        sb.append("--------------------------------\n");
        sb.append("Клиент: ").append(receipt.get("user_name").getAsString()).append("\n");

        if (receipt.get("bonus_spent").getAsDouble() > 0) {
            sb.append("Списано бонусов: ").append(String.format("%.2f", receipt.get("bonus_spent").getAsDouble())).append(" BYN\n");
        }

        sb.append("Начислено бонусов: ").append(String.format("%.2f", receipt.get("bonus_earned").getAsDouble())).append(" BYN\n");
        sb.append("================================\n");
        sb.append(" Спасибо за покупку!\n");
        sb.append(" QR код для проверки:\n");
        sb.append(" ").append(receipt.get("qr_code_data").getAsString()).append("\n");
        sb.append("================================\n");

        return sb.toString();
    }


}