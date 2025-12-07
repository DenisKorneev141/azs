package com.azs.export;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class CsvExporter {

    public static void exportReport(JsonObject reportData, File file,
                                    LocalDate startDate, LocalDate endDate,
                                    String azsName) throws IOException {

        try (FileWriter writer = new FileWriter(file)) {
            // UTF-8 BOM для корректного отображения кириллицы в Excel
            writer.write("\uFEFF");

            // Заголовок отчета
            writer.write("ОТЧЕТ ПО ПРОДАЖАМ ТОПЛИВА\n\n");

            // Информация об АЗС и периоде
            writer.write("АЗС:;" + azsName + "\n");
            writer.write("Период:;" +
                    startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "\n");
            writer.write("Дата формирования:;" +
                    java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n");
            writer.write("Генерировал:;" + System.getProperty("user.name") + "\n\n");

            // Основная статистика
            writer.write("--- ОСНОВНАЯ СТАТИСТИКА ---\n");
            writer.write("Показатель;Значение\n");
            writer.write("Общая выручка;" +
                    String.format("%.2f BYN", reportData.get("total_revenue").getAsDouble()) + "\n");
            writer.write("Продано литров;" +
                    String.format("%.1f л", reportData.get("total_liters").getAsDouble()) + "\n");
            writer.write("Количество продаж;" +
                    reportData.get("total_transactions").getAsInt() + "\n\n");

            // Детализированная статистика
            writer.write("--- ДЕТАЛИЗИРОВАННАЯ СТАТИСТИКА ---\n");
            writer.write("Показатель;Значение\n");
            writer.write("Выручка наличными;" +
                    String.format("%.2f BYN", reportData.get("cash_revenue").getAsDouble()) + "\n");
            writer.write("Выручка по картам;" +
                    String.format("%.2f BYN", reportData.get("card_revenue").getAsDouble()) + "\n");
            writer.write("Средний чек;" +
                    String.format("%.2f BYN", reportData.get("average_sale").getAsDouble()) + "\n");
            writer.write("Доля наличных;" +
                    calculatePercentage(reportData.get("cash_revenue").getAsDouble(),
                            reportData.get("total_revenue").getAsDouble()) + "%\n");
            writer.write("Доля безналичных;" +
                    calculatePercentage(reportData.get("card_revenue").getAsDouble(),
                            reportData.get("total_revenue").getAsDouble()) + "%\n");
            writer.write("Самое популярное топливо;" +
                    reportData.get("most_popular_fuel").getAsString() + "\n\n");

            // Статистика по типам топлива (если есть)
            if (reportData.has("fuel_statistics")) {
                writer.write("--- СТАТИСТИКА ПО ТИПАМ ТОПЛИВА ---\n");
                writer.write("Тип топлива;Количество продаж;Литров;Выручка\n");

                JsonObject fuelStats = reportData.getAsJsonObject("fuel_statistics");

                if (fuelStats.has("ai92_count") && fuelStats.get("ai92_count").getAsInt() > 0) {
                    writer.write("АИ-92;" +
                            fuelStats.get("ai92_count").getAsInt() + ";" +
                            String.format("%.1f", fuelStats.get("ai92_liters").getAsDouble()) + ";" +
                            String.format("%.2f", fuelStats.get("ai92_revenue").getAsDouble()) + "\n");
                }

                if (fuelStats.has("ai95_count") && fuelStats.get("ai95_count").getAsInt() > 0) {
                    writer.write("АИ-95;" +
                            fuelStats.get("ai95_count").getAsInt() + ";" +
                            String.format("%.1f", fuelStats.get("ai95_liters").getAsDouble()) + ";" +
                            String.format("%.2f", fuelStats.get("ai95_revenue").getAsDouble()) + "\n");
                }

                if (fuelStats.has("ai98_count") && fuelStats.get("ai98_count").getAsInt() > 0) {
                    writer.write("АИ-98;" +
                            fuelStats.get("ai98_count").getAsInt() + ";" +
                            String.format("%.1f", fuelStats.get("ai98_liters").getAsDouble()) + ";" +
                            String.format("%.2f", fuelStats.get("ai98_revenue").getAsDouble()) + "\n");
                }

                if (fuelStats.has("ai100_count") && fuelStats.get("ai100_count").getAsInt() > 0) {
                    writer.write("АИ-100;" +
                            fuelStats.get("ai100_count").getAsInt() + ";" +
                            String.format("%.1f", fuelStats.get("ai100_liters").getAsDouble()) + ";" +
                            String.format("%.2f", fuelStats.get("ai100_revenue").getAsDouble()) + "\n");
                }

                if (fuelStats.has("dt_count") && fuelStats.get("dt_count").getAsInt() > 0) {
                    writer.write("Дизель;" +
                            fuelStats.get("dt_count").getAsInt() + ";" +
                            String.format("%.1f", fuelStats.get("dt_liters").getAsDouble()) + ";" +
                            String.format("%.2f", fuelStats.get("dt_revenue").getAsDouble()) + "\n");
                }
            }

            // Итоги
            writer.write("\n--- ИТОГИ ---\n");
            writer.write("Статус;Успешно\n");
            writer.write("Формат;CSV (разделитель - точка с запятой)\n");
            writer.write("Кодировка;UTF-8\n");
            writer.write("Примечание;Для открытия в Excel используйте кодировку UTF-8\n");
        }
    }

    private static String calculatePercentage(double part, double total) {
        if (total == 0) return "0.00";
        return String.format("%.2f", (part / total) * 100);
    }
}