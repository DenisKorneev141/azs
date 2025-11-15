package com.azs;

import java.util.Scanner;

public class MainServer {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        showMainMenu();
    }

    public static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void showMainMenu(){
        while (true){
            System.out.println("Панель администратора сети АЗС");
            System.out.println("==============================");
            System.out.println("\t1. Управление сервером");
            System.out.println("\t2. Управление операторами");
            System.out.println("\t3. Управление пользователями");
            System.out.println("\t4. Управление АЗС");
            System.out.print("-> ");

            String choice = scanner.next();

            switch (choice){
                case "1":
                    showServerMenu();
                    break;
                case "2":
                    showOperatorsMenu();
                    break;
                case "3":
                   showUsersMenu();
                    break;
                case "4":
                   showAZSMenu();
                    break;
                default:
                    System.out.println("Ошибка: неверный выбор!");
            }
        }
    }

    public static void showServerMenu(){
        while (true){
            clearConsole();
            System.out.println("Управление сервером");
            System.out.println("===================");
            System.out.println("\t1. Включить сервер");
            System.out.println("\t2. Выключить сервер");
            System.out.println("\t3. Статус сервера");
            System.out.println("\t4. Перезагрузка");
            System.out.println("\t5. Назад");
            System.out.print("-> ");

            String choice = scanner.next();

            switch (choice){
                case "1":
                    ServerManager.startServer();
                    break;
                case "2":
                    ServerManager.stopServer();
                    break;
                case "3":
                    ServerManager.showStatus();
                    break;
                case "4":
                    ServerManager.stopServer();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ServerManager.startServer();
                    break;
                case "5":
                    showMainMenu();
                    break;
            }

        }
    }

    public static void showOperatorsMenu(){
        while (true){
            clearConsole();
            System.out.println("Управление операторами");
            System.out.println("======================");
            System.out.println("\t1. Просмотр всех операторов");
            System.out.println("\t2. Добавить нового оператора");
            System.out.println("\t3. Удалить / заблокировать оператора");
            System.out.println("\t4. Назад");
            System.out.print("-> ");

            String choice = scanner.next();

            switch (choice){
                case "1":
                    String operators = ServerManager.showOperators();
                    System.out.println(operators);
                    break;
                case "2":
                    //newOperator();
                    break;
                case "3":
                    //deleteOperator();
                    break;
                case "4":
                    showMainMenu();
                    break;
            }
        }
    }

    public static void showUsersMenu(){
        while (true){
            clearConsole();
            System.out.println("Управление пользователями");
            System.out.println("======================");
            System.out.println("\t1. Просмотр всех пользователей");
            System.out.println("\t2. Удалить / заблокировть пользователя");
            System.out.println("\t3. Назад");
            System.out.print("-> ");

            String choice = scanner.next();

            switch (choice){
                case "1":
                    //showUsers();
                    break;
                case "2":
                    //deleteUser();
                    break;
                case "3":
                    showMainMenu();
                    break;

            }
        }
    }

    public static void showAZSMenu(){
        while (true){
            clearConsole();
            System.out.println("Управление АЗС");
            System.out.println("===============");
            System.out.println("\t1. Просмотр цен");
            System.out.println("\t2. Изменение цен");
            System.out.println("\t3. Просмотр всех заправок");
            System.out.println("\t4. Добавить заправку");
            System.out.println("\t5. Удалить заправку");
            System.out.println("\t6. Назад");
            System.out.print("-> ");

            String choice = scanner.next();

            switch (choice){
                case "1":
                    String prices = ServerManager.getFuelPrices();
                    System.out.println(prices);
                    break;
                case "2":
                    System.out.println(ServerManager.getFuelPrices());

                    System.out.print("Введите ID топлива: ");
                    int fuelId = scanner.nextInt();
                    System.out.print("Введите новую цену: ");
                    double newPrice = scanner.nextDouble();

                    String result = ServerManager.updateFuelPrice(fuelId, newPrice);
                    System.out.println(result);
                    scanner.nextLine();
                    break;
                case "3":
                    String azsList = ServerManager.showAZS();
                    System.out.println(azsList);
                    break;
                case "4":
                    scanner.nextLine();
                    System.out.print("Введите название новой АЗС: ");
                    String name = scanner.nextLine();

                    System.out.print("Введите адрес: ");
                    String address = scanner.nextLine();

                    System.out.print("Введите кол-во колонок: ");
                    int nozzle = scanner.nextInt();
                    scanner.nextLine();

                    String new_azs_result = ServerManager.newAZS(name, address, nozzle);
                    System.out.println(new_azs_result);
                    break;
                case "5":
                    System.out.println(ServerManager.showAZS());
                    System.out.println("\nВыберите ID заправки которую хотите удалить: ");
                    int azs_id = scanner.nextInt();
                    String delete_azs_result = ServerManager.deleteAZS(azs_id);
                    System.out.println(delete_azs_result);
                    break;
                case "6":
                    showMainMenu();
                    break;
            }
        }
    }
}
