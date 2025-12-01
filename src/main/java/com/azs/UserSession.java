package com.azs;

public class UserSession {
    private static String username;
    private static String role;

    public static void setCurrentUser(String username, String role) {
        UserSession.username = username;
        UserSession.role = role;
    }

    public static String getUsername() {
        return username;
    }

    public static String getRole() {
        return role;
    }

    public static boolean isAdmin() {
        return "admin".equals(role);
    }

    public static boolean isOperator() {
        return "operator".equals(role);
    }

    public static void clearSession() {
        username = null;
        role = null;
    }
}