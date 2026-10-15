package com.lutfy.ticketfy.infra.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

// Mesmo QR do site (qrcode.react com level "M"): o conteúdo é o código do
// ingresso, sem prefixo.
@Component
public class QrCodePng {

    private static final int SIZE = 360;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    public byte[] render(String content) {
        try {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 4,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
                }
            }
            var out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException ex) {
            throw new IllegalStateException("QR code could not be encoded", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("QR code could not be written", ex);
        }
    }
}
