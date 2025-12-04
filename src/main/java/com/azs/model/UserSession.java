package com.azs.model;

public class UserSession {
    private static String username;
    private static String role;
    private static String firstName;
    private static String lastName;
    private static String azsName;
    private static int azsId;
    private static double todaysTotal; // ДОБАВИТЬ ЭТО

    // Полная инициализация
    public static void initializeSession(String username, String role,
                                         String firstName, String lastName,
                                         String azsName, int azsId) {
        UserSession.username = username;
        UserSession.role = role;
        UserSession.firstName = firstName;
        UserSession.lastName = lastName;
        UserSession.azsName = azsName;
        UserSession.azsId = azsId;

        System.out.println("Сессия создана:");
        System.out.println("  Оператор: " + firstName + " " + lastName);
        System.out.println("  АЗС: " + azsName + " (ID: " + azsId + ")");
    }

    // Установить сумму за сегодня
    public static void setTodaysTotal(double total) {
        todaysTotal = total;
    }

    // Получить сумму за сегодня
    public static double getTodaysTotal() {
        return todaysTotal;
    }

    public static String getFormattedTodaysTotal() {
        return String.format("%,.2f ₽", todaysTotal);
    }

    // Геттеры
    public static String getUsername() {
        return username != null ? username : "Гость";
    }

    public static String getRole() {
        return role != null ? role : "operator";
    }

    public static String getFirstName() {
        return firstName != null ? firstName : "Иван";
    }

    public static String getLastName() {
        return lastName != null ? lastName : "Иванов";
    }

    public static String getFullName() {
        return getFirstName() + " " + getLastName();
    }

    public static String getAzsName() {
        return azsName != null ? azsName : "АЗС №1 Центральная";
    }

    public static int getAzsId() {
        return azsId;
    }

    public static boolean isOperator() {
        return "operator".equals(role);
    }

    // Очистка сессии при выходе
    public static void clearSession() {
        username = null;
        role = null;
        firstName = null;
        lastName = null;
        azsName = null;
        azsId = 0;
        System.out.println("Сессия очищена");
    }

    // Проверка авторизации
    public static boolean isLoggedIn() {
        return username != null && !username.isEmpty();
    }

    // Информация о сессии (для отладки)
    public static String getSessionInfo() {
        return String.format("Пользователь: %s (%s %s), АЗС: %s",
                getUsername(), getFirstName(), getLastName(), getAzsName());
    }
}