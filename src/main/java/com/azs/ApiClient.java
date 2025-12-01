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

                        return gson.fromJson(response.toString(), JsonObject.class);
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

                        return gson.fromJson(response.toString(), JsonObject.class);
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
}