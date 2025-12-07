package com.azs.export;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class HtmlExporter {

    public static void exportReport(JsonObject reportData, File file,
                                    LocalDate startDate, LocalDate endDate,
                                    String azsName) throws IOException {

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html lang='ru'>\n");
            writer.write("<head>\n");
            writer.write("    <meta charset='UTF-8'>\n");
            writer.write("    <title>Отчет по продажам топлива</title>\n");
            writer.write("    <style>\n");
            writer.write("        body { font-family: Arial, sans-serif; margin: 40px; }\n");
            writer.write("        .header { text-align: center; margin-bottom: 30px; }\n");
            writer.write("        .title { font-size: 24px; font-weight: bold; color: #2c3e50; }\n");
            writer.write("        .info { margin-bottom: 20px; color: #34495e; }\n");
            writer.write("        .section { margin: 25px 0; }\n");
            writer.write("        .section-title { font-size: 18px; font-weight: bold; color: #2c3e50; margin-bottom: 10px; border-bottom: 2px solid #3498db; padding-bottom: 5px; }\n");
            writer.write("        table { width: 100%; border-collapse: collapse; margin-bottom: 15px; }\n");
            writer.write("        th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }\n");
            writer.write("        th { background-color: #f8f9fa; font-weight: bold; }\n");
            writer.write("        .total { font-weight: bold; color: #27ae60; }\n");
            writer.write("        .currency { text-align: right; }\n");
            writer.write("        .footer { margin-top: 30px; text-align: right; font-size: 12px; color: #7f8c8d; }\n");
            writer.write("    </style>\n");
            writer.write("</head>\n");
            writer.write("<body>\n");

            // Заголовок
            writer.write("    <div class='header'>\n");
            writer.write("        <div class='title'>ОТЧЕТ ПО ПРОДАЖАМ ТОПЛИВА</div>\n");

            writer.write("    </div>\n");


            // Информация
            writer.write("    <div class='info'>\n");
            writer.write("        <div><strong>АЗС:</strong> " + azsName + "</div>\n");
            writer.write("        <div><strong>Период:</strong> " +
                    startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "</div>\n");
            writer.write("        <div><strong>Дата формирования:</strong> " +
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "</div>\n");
            writer.write("    </div>\n");

            // Основная статистика
            writer.write("    <div class='section'>\n");
            writer.write("        <div class='section-title'>Основная статистика</div>\n");
            writer.write("        <table>\n");
            writer.write("            <tr>\n");
            writer.write("                <th>Показатель</th>\n");
            writer.write("                <th>Значение</th>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Общая выручка</td>\n");
            writer.write("                <td class='currency total'>" +
                    String.format("%.2f BYN", reportData.get("total_revenue").getAsDouble()) + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Продано литров</td>\n");
            writer.write("                <td class='currency'>" +
                    String.format("%.1f л", reportData.get("total_liters").getAsDouble()) + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Количество продаж</td>\n");
            writer.write("                <td>" + reportData.get("total_transactions").getAsInt() + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("        </table>\n");
            writer.write("    </div>\n");

            // Детализированная статистика
            writer.write("    <div class='section'>\n");
            writer.write("        <div class='section-title'>Детализированная статистика</div>\n");
            writer.write("        <table>\n");
            writer.write("            <tr>\n");
            writer.write("                <th>Показатель</th>\n");
            writer.write("                <th>Значение</th>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Выручка наличными</td>\n");
            writer.write("                <td class='currency'>" +
                    String.format("%.2f BYN", reportData.get("cash_revenue").getAsDouble()) + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Выручка по картам</td>\n");
            writer.write("                <td class='currency'>" +
                    String.format("%.2f BYN", reportData.get("card_revenue").getAsDouble()) + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Средний чек</td>\n");
            writer.write("                <td class='currency'>" +
                    String.format("%.2f BYN", reportData.get("average_sale").getAsDouble()) + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Самое популярное топливо</td>\n");
            writer.write("                <td>" + reportData.get("most_popular_fuel").getAsString() + "</td>\n");
            writer.write("            </tr>\n");
            writer.write("        </table>\n");

            // Проценты
            double totalRevenue = reportData.get("total_revenue").getAsDouble();
            double cashPercent = totalRevenue > 0 ? (reportData.get("cash_revenue").getAsDouble() / totalRevenue) * 100 : 0;
            double cardPercent = totalRevenue > 0 ? (reportData.get("card_revenue").getAsDouble() / totalRevenue) * 100 : 0;

            writer.write("        <table>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Доля наличных</td>\n");
            writer.write("                <td class='currency'>" + String.format("%.1f", cashPercent) + "%</td>\n");
            writer.write("            </tr>\n");
            writer.write("            <tr>\n");
            writer.write("                <td>Доля безналичных</td>\n");
            writer.write("                <td class='currency'>" + String.format("%.1f", cardPercent) + "%</td>\n");
            writer.write("            </tr>\n");
            writer.write("        </table>\n");
            writer.write("    </div>\n");

            // Подпись
            writer.write("    <div class='footer'>\n");
            writer.write("        <button class='print-btn' onclick='window.print()'>🖨️ Печать</button>\n");
            writer.write("        <div>Сгенерировано автоматически</div>\n");
            writer.write("    </div>\n");

            writer.write("</body>\n");
            writer.write("</html>\n");
        }
    }
}