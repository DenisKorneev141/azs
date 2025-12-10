package com.azs;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class QrCodeUtils {

    /**
     * Генерирует настоящий QR-код из текста
     */
    public static Image generateQrCodeImage(String text, int size) {
        try {
            // Настройки для QR-кода
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            // Создаем QR-код
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hints);

            // Конвертируем в BufferedImage
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Добавляем информацию в центр (опционально)
            bufferedImage = addCenterText(bufferedImage, "АЗС PHAETON");

            // Конвертируем в JavaFX Image
            WritableImage fxImage = new WritableImage(size, size);
            SwingFXUtils.toFXImage(bufferedImage, fxImage);

            return fxImage;

        } catch (WriterException e) {
            System.err.println("❌ Ошибка генерации QR-кода: " + e.getMessage());
            return createErrorImage(size);
        }
    }

    /**
     * Добавляет текст в центр QR-кода
     */
    private static BufferedImage addCenterText(BufferedImage qrImage, String text) {
        try {
            Graphics2D g2d = qrImage.createGraphics();

            // Настройки для текста
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));

            // Позиционируем текст в центре
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            int x = (qrImage.getWidth() - textWidth) / 2;
            int y = (qrImage.getHeight() + textHeight) / 2 - fm.getDescent();

            // Добавляем белую подложку
            g2d.setColor(Color.WHITE);
            g2d.fillRect(x - 2, y - textHeight + 4, textWidth + 4, textHeight - 2);

            // Рисуем текст
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, x, y);

            g2d.dispose();

        } catch (Exception e) {
            System.err.println("⚠️ Не удалось добавить текст в QR: " + e.getMessage());
        }

        return qrImage;
    }

    /**
     * Создает изображение с ошибкой
     */
    private static Image createErrorImage(int size) {
        BufferedImage errorImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = errorImage.createGraphics();

        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, size, size);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        String errorText = "QR ERROR";
        FontMetrics fm = g2d.getFontMetrics();
        int x = (size - fm.stringWidth(errorText)) / 2;
        int y = size / 2;
        g2d.drawString(errorText, x, y);

        g2d.dispose();

        WritableImage fxImage = new WritableImage(size, size);
        SwingFXUtils.toFXImage(errorImage, fxImage);
        return fxImage;
    }

    /**
     * Генерирует текст для QR-кода колонки АЗС
     */
    public static String generateQrText(int azsId, int nozzleNumber, String azsName) {
        return String.format(
                "AZS_QR_DATA\n" +
                        "===========\n" +
                        "Тип: Заправочная станция\n" +
                        "Название: %s\n" +
                        "ID АЗС: %d\n" +
                        "Колонка: %d\n" +
                        "Время: %s\n" +
                        "Данные: azs_id=%d&nozzle=%d\n" +
                        "===========\n" +
                        "Отсканируйте для начала заправки",
                azsName,
                azsId,
                nozzleNumber,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()),
                azsId,
                nozzleNumber
        );
    }

    /**
     * Сохраняет QR-код в PNG файл
     */
    public static void saveQrCodeToFile(String text, int size, String filename) {
        try {
            // Генерируем QR-код
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hints);

            // Сохраняем в файл
            File outputFile = new File(filename);
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", outputFile.toPath());

            System.out.println("✅ QR-код сохранен в файл: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("❌ Ошибка сохранения QR-кода: " + e.getMessage());
            throw new RuntimeException("Не удалось сохранить QR-код", e);
        }
    }

    /**
     * Генерирует QR-код и возвращает как byte array (для API)
     */
    public static byte[] generateQrCodeBytes(String text, int size) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ImageIO.write(image, "PNG", baos);

            return baos.toByteArray();

        } catch (Exception e) {
            System.err.println("❌ Ошибка генерации QR bytes: " + e.getMessage());
            return new byte[0];
        }
    }
}