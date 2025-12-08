package com.azs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseUtil {
    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                String url = "jdbc:postgresql://localhost:5432/azs_database";
                String user = "postgres";
                String password = "123456";

                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection(url, user, password);
                System.out.println("✅ Подключение к БД установлено");
            } catch (ClassNotFoundException e) {
                throw new SQLException("PostgreSQL Driver not found", e);
            }
        }
        return connection;
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ Подключение к БД закрыто");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка закрытия подключения: " + e.getMessage());
        }
    }
}