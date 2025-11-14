// src/main/java/com/azs/MainServer.java
package com.azs;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class MainServer {
    public static void main(String[] args) {
        try {
            // Создаем HTTP сервер на порту 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            // Простой обработчик для теста
            server.createContext("/api/test", new TestHandler());

            server.setExecutor(null);
            server.start();

            System.out.println("🚀 Сервер запущен на http://localhost:8080");
            System.out.println("📞 Тестовый endpoint: http://localhost:8080/api/test");

        } catch (IOException e) {
            System.err.println("❌ Ошибка запуска сервера: " + e.getMessage());
        }
    }
}