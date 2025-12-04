package com.azs;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.CompletableFuture;

public class ApiClient {
    private static final Gson gson = new Gson();
    private static String serverUrl = "http://localhost:8080";

    public static void setServerUrl(String ipAddress) {
        if (!ipAddress.startsWith("http")) {
            serverUrl = "http://" + ipAddress + ":8080";
        } else {
            serverUrl = ipAddress;
        }
        System.out.println("🌐 Установлен адрес сервера: " + serverUrl);
    }

    public static String getServerUrl() {
        return serverUrl;
    }

    public static boolean checkConnection() {
        try {
            URL url = new URL(serverUrl + "/api/fuel");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static CompletableFuture<JsonObject> authenticate(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(serverUrl + "/api/auth");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JsonObject request = new JsonObject();
                request.addProperty("username", username);
                request.addProperty("password", password);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = gson.toJson(request).getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);

                        // Добавляем недостающие поля для совместимости
                        if (!jsonResponse.has("firstName")) {
                            jsonResponse.addProperty("firstName", "Иван");
                        }
                        if (!jsonResponse.has("lastName")) {
                            jsonResponse.addProperty("lastName", "Иванов");
                        }
                        if (!jsonResponse.has("azsName")) {
                            jsonResponse.addProperty("azsName", "АЗС №1 Центральная");
                        }
                        if (!jsonResponse.has("azsId")) {
                            jsonResponse.addProperty("azsId", 1);
                        }

                        jsonResponse.addProperty("statusCode", responseCode);
                        return jsonResponse;
                    }
                } else {
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("message", "Ошибка сервера: " + responseCode);
                    return error;
                }
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Ошибка подключения: " + e.getMessage());
                return error;
            }
        });
    }

    public static CompletableFuture<JsonObject> getAZSList() {
        return makeGetRequest("/api/azs");
    }

    public static CompletableFuture<JsonObject> getFuelPrices() {
        return makeGetRequest("/api/fuel");
    }

    private static CompletableFuture<JsonObject> makeGetRequest(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(serverUrl + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                        jsonResponse.addProperty("success", true);
                        return jsonResponse;
                    }
                } else {
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("message", "Ошибка: " + responseCode);
                    return error;
                }
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Ошибка: " + e.getMessage());
                return error;
            }
        });
    }

    // ============= НОВЫЕ МЕТОДЫ =============

    /**
     * Получить данные оператора с сервера
     */
    public static CompletableFuture<JsonObject> getOperatorData(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(serverUrl + "/api/operator/" + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JsonObject data = gson.fromJson(response.toString(), JsonObject.class);
                        data.addProperty("success", true);
                        return data;
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибку - используем дефолтные данные
            }

            // Если сервер не отвечает, возвращаем дефолтные данные
            return createDefaultOperatorData(username);
        });
    }

    /**
     * Создать дефолтные данные оператора
     */
    private static JsonObject createDefaultOperatorData(String username) {
        JsonObject data = new JsonObject();
        data.addProperty("success", true);
        data.addProperty("username", username);
        data.addProperty("firstName", "Иван");
        data.addProperty("lastName", "Иванов");
        data.addProperty("azsName", "АЗС №1 Центральная");
        data.addProperty("azsId", 1);
        data.addProperty("role", "operator");
        return data;
    }

    /**
     * Получить текущую сумму в кассе
     */
    public static CompletableFuture<Double> getCashAmount(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(serverUrl + "/api/cash?operator=" + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                        if (json.has("cashAmount")) {
                            return json.get("cashAmount").getAsDouble();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 5000.0; // Дефолтная сумма для теста
        });
    }

    // В класс ApiClient добавьте:
    public static CompletableFuture<JsonObject> getRecentTransactions(int azsId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(serverUrl + "/api/transactions/recent?azs_id=" + azsId + "&limit=" + limit);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                        jsonResponse.addProperty("success", true);
                        return jsonResponse;
                    }
                } else {
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("message", "Ошибка: " + responseCode);
                    return error;
                }
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Ошибка: " + e.getMessage());
                return error;
            }
        });
    }

    /**
     * Проверить статус сервера
     */
    public static CompletableFuture<Boolean> checkServerStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(serverUrl + "/api/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int responseCode = conn.getResponseCode();
                return responseCode == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }
}