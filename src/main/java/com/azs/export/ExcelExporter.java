package com.azs.export;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ExcelExporter {

    public static void exportReport(JsonObject reportData, File file,
                                    LocalDate startDate, LocalDate endDate,
                                    String azsName) throws IOException {

        try (FileWriter writer = new FileWriter(file)) {
            // Создаем XML Spreadsheet (Excel 2003 XML формат)
            writer.write("<?xml version=\"1.0\"?>\n");
            writer.write("<?mso-application progid=\"Excel.Sheet\"?>\n");
            writer.write("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
            writer.write(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n");
            writer.write(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n");
            writer.write(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
            writer.write(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">\n");

            writer.write(" <Styles>\n");
            writer.write("  <Style ss:ID=\"Default\" ss:Name=\"Normal\">\n");
            writer.write("   <Alignment ss:Vertical=\"Center\"/>\n");
            writer.write("   <Font ss:FontName=\"Arial\" x:CharSet=\"204\" x:Family=\"Swiss\" ss:Size=\"11\"/>\n");
            writer.write("  </Style>\n");
            writer.write("  <Style ss:ID=\"Title\">\n");
            writer.write("   <Font ss:FontName=\"Arial\" x:CharSet=\"204\" x:Family=\"Swiss\" ss:Size=\"16\" ss:Bold=\"1\"/>\n");
            writer.write("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n");
            writer.write("  </Style>\n");
            writer.write("  <Style ss:ID=\"Header\">\n");
            writer.write("   <Font ss:FontName=\"Arial\" x:CharSet=\"204\" x:Family=\"Swiss\" ss:Size=\"12\" ss:Bold=\"1\"/>\n");
            writer.write("   <Interior ss:Color=\"#C0C0C0\" ss:Pattern=\"Solid\"/>\n");
            writer.write("   <Borders>\n");
            writer.write("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\"/>\n");
            writer.write("   </Borders>\n");
            writer.write("  </Style>\n");
            writer.write("  <Style ss:ID=\"Number\">\n");
            writer.write("   <NumberFormat ss:Format=\"#,##0.00\"/>\n");
            writer.write("  </Style>\n");
            writer.write("  <Style ss:ID=\"Currency\">\n");
            writer.write("   <NumberFormat ss:Format=\"#,##0.00\"/>\n");
            writer.write("  </Style>\n");
            writer.write(" </Styles>\n");

            writer.write(" <Worksheet ss:Name=\"Отчет\">\n");
            writer.write("  <Table>\n");

            // Устанавливаем ширину колонок
            writer.write("   <Column ss:Width=\"200\"/>\n");
            writer.write("   <Column ss:Width=\"150\"/>\n");

            // Заголовок отчета
            writer.write("   <Row>\n");
            writer.write("    <Cell ss:StyleID=\"Title\" ss:MergeAcross=\"1\"><Data ss:Type=\"String\">ОТЧЕТ ПО ПРОДАЖАМ ТОПЛИВА</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell/><Cell/>\n");
            writer.write("   </Row>\n");

            // Информация об АЗС
            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">АЗС:</Data></Cell>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">" + escapeXml(azsName) + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            // Период отчета
            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Период:</Data></Cell>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">" +
                    startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " - " +
                    endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            // Дата формирования
            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Дата формирования:</Data></Cell>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">" +
                    java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell/><Cell/>\n");
            writer.write("   </Row>\n");

            // Заголовок основной статистики
            writer.write("   <Row>\n");
            writer.write("    <Cell ss:StyleID=\"Header\" ss:MergeAcross=\"1\"><Data ss:Type=\"String\">ОСНОВНАЯ СТАТИСТИКА</Data></Cell>\n");
            writer.write("   </Row>\n");

            // Основная статистика - данные
            double totalRevenue = reportData.has("total_revenue") ? reportData.get("total_revenue").getAsDouble() : 0.0;
            double totalLiters = reportData.has("total_liters") ? reportData.get("total_liters").getAsDouble() : 0.0;
            int totalTransactions = reportData.has("total_transactions") ? reportData.get("total_transactions").getAsInt() : 0;

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Общая выручка:</Data></Cell>\n");
            writer.write("    <Cell ss:StyleID=\"Currency\"><Data ss:Type=\"Number\">" + totalRevenue + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Продано литров:</Data></Cell>\n");
            writer.write("    <Cell ss:StyleID=\"Number\"><Data ss:Type=\"Number\">" + totalLiters + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Количество продаж:</Data></Cell>\n");
            writer.write("    <Cell><Data ss:Type=\"Number\">" + totalTransactions + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell/><Cell/>\n");
            writer.write("   </Row>\n");

            // Заголовок детализированной статистики
            writer.write("   <Row>\n");
            writer.write("    <Cell ss:StyleID=\"Header\" ss:MergeAcross=\"1\"><Data ss:Type=\"String\">ДЕТАЛИЗИРОВАННАЯ СТАТИСТИКА</Data></Cell>\n");
            writer.write("   </Row>\n");

            // Детализированная статистика - данные
            double cashRevenue = reportData.has("cash_revenue") ? reportData.get("cash_revenue").getAsDouble() : 0.0;
            double cardRevenue = reportData.has("card_revenue") ? reportData.get("card_revenue").getAsDouble() : 0.0;
            double averageSale = reportData.has("average_sale") ? reportData.get("average_sale").getAsDouble() : 0.0;
            String popularFuel = reportData.has("most_popular_fuel") ?
                    reportData.get("most_popular_fuel").getAsString() : "Нет данных";

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Выручка наличными:</Data></Cell>\n");
            writer.write("    <Cell ss:StyleID=\"Currency\"><Data ss:Type=\"Number\">" + cashRevenue + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Выручка по картам:</Data></Cell>\n");
            writer.write("    <Cell ss:StyleID=\"Currency\"><Data ss:Type=\"Number\">" + cardRevenue + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Средний чек:</Data></Cell>\n");
            writer.write("    <Cell ss:StyleID=\"Currency\"><Data ss:Type=\"Number\">" + averageSale + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            writer.write("   <Row>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">Самое популярное топливо:</Data></Cell>\n");
            writer.write("    <Cell><Data ss:Type=\"String\">" + escapeXml(popularFuel) + "</Data></Cell>\n");
            writer.write("   </Row>\n");

            // Проценты
            if (totalRevenue > 0) {
                double cashPercent = (cashRevenue / totalRevenue) * 100;
                double cardPercent = (cardRevenue / totalRevenue) * 100;

                writer.write("   <Row>\n");
                writer.write("    <Cell><Data ss:Type=\"String\">Доля наличных:</Data></Cell>\n");
                writer.write("    <Cell ss:StyleID=\"Number\"><Data ss:Type=\"Number\">" + cashPercent + "</Data></Cell>\n");
                writer.write("   </Row>\n");

                writer.write("   <Row>\n");
                writer.write("    <Cell><Data ss:Type=\"String\">Доля безналичных:</Data></Cell>\n");
                writer.write("    <Cell ss:StyleID=\"Number\"><Data ss:Type=\"Number\">" + cardPercent + "</Data></Cell>\n");
                writer.write("   </Row>\n");
            }

            writer.write("  </Table>\n");
            writer.write(" </Worksheet>\n");
            writer.write("</Workbook>\n");
        }
    }

    private static String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}