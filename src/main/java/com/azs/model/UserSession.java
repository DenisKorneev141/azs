package com.azs.model;

public class UserSession {
    private static String username;
    private static String role;
    private static String firstName;
    private static String lastName;
    private static String azsName;
    private static int azsId;
    private static double todaysTotal;
    private static int todaysTransactions;  // ← ДОБАВЛЕНО
    private static double todaysLiters;     // ← ДОБАВЛЕНО

    // Полная инициализация с новыми полями
    public static void initializeSession(String username, String role,
                                         String firstName, String lastName,
                                         String azsName, int azsId,
                                         double todaysTotal, int todaysTransactions, double todaysLiters) {
        UserSession.username = username;
        UserSession.role = role;
        UserSession.firstName = firstName;
        UserSession.lastName = lastName;
        UserSession.azsName = azsName;
        UserSession.azsId = azsId;
        UserSession.todaysTotal = todaysTotal;
        UserSession.todaysTransactions = todaysTransactions;
        UserSession.todaysLiters = todaysLiters;

        System.out.println("Сессия создана:");
        System.out.println("  Оператор: " + firstName + " " + lastName);
        System.out.println("  АЗС: " + azsName + " (ID: " + azsId + ")");
        System.out.println("  Статистика за сегодня:");
        System.out.println("  - Сумма: " + todaysTotal + " ₽");
        System.out.println("  - Транзакций: " + todaysTransactions);
        System.out.println("  - Литров: " + todaysLiters + " л");
    }

    // Перегруженный метод для обратной совместимости
    public static void initializeSession(String username, String role,
                                         String firstName, String lastName,
                                         String azsName, int azsId) {
        initializeSession(username, role, firstName, lastName,
                azsName, azsId, 0.0, 0, 0.0);
    }

    // Установить сумму за сегодня
    public static void setTodaysTotal(double total) {
        todaysTotal = total;
    }

    // Установить количество транзакций за сегодня
    public static void setTodaysTransactions(int transactions) {
        todaysTransactions = transactions;
    }

    // Установить количество литров за сегодня
    public static void setTodaysLiters(double liters) {
        todaysLiters = liters;
    }

    // Получить сумму за сегодня
    public static double getTodaysTotal() {
        return todaysTotal;
    }

    // Получить количество транзакций за сегодня
    public static int getTodaysTransactions() {
        return todaysTransactions;
    }

    // Получить количество литров за сегодня
    public static double getTodaysLiters() {
        return todaysLiters;
    }

    public static String getFormattedTodaysTotal() {
        return String.format("%,.2f ₽", todaysTotal);
    }

    public static String getFormattedTodaysLiters() {
        return String.format("%.1f л", todaysLiters);
    }

    // Геттеры (остаются без изменений)
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
        todaysTotal = 0;
        todaysTransactions = 0;
        todaysLiters = 0;
        System.out.println("Сессия очищена");
    }

    // Проверка авторизации
    public static boolean isLoggedIn() {
        return username != null && !username.isEmpty();
    }

    // Полная информация о сессии
    public static String getSessionInfo() {
        return String.format("Пользователь: %s (%s %s), АЗС: %s\n" +
                        "Статистика за сегодня: %.2f ₽, %d транзакций, %.1f л",
                getUsername(), getFirstName(), getLastName(), getAzsName(),
                todaysTotal, todaysTransactions, todaysLiters);
    }

    // Статистика в кратком виде
    public static String getTodayStats() {
        return String.format("%.1f л | %d транзакций | %,.2f ₽",
                todaysLiters, todaysTransactions, todaysTotal);
    }
}