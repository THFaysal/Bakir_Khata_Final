package com.example.bakir_khata.receipts;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * One-page EventFlow-inspired navy/gold branded PDFs rendered without browser access.
 * Avoids saving temporary user PDFs or posting private financial data to Cloudinary.
 * Uses PDF standard fonts and ASCII 'BDT' for reliable font embedding on Render.
 */
@Component
public class ReceiptPdfRenderer {
    private static final PDFont NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color NAVY = new Color(12, 38, 72);
    private static final Color GOLD = new Color(192, 135, 26);
    private static final Color LIGHT_GOLD = new Color(252, 247, 229);
    private static final Color INK = new Color(32, 43, 56);
    private static final Color MUTED = new Color(115, 125, 137);
    private static final Color LINE = new Color(219, 225, 233);
    private static final Color GREEN = new Color(15, 111, 70);

    public byte[] render(ReceiptDocument receipt) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                drawCard(cs);
                drawHeader(document, cs, receipt);
                drawAmountBand(cs, receipt);
                drawDetails(cs, receipt.details());
                drawBreakdown(cs, receipt.breakdown(), receipt.details().size());
                drawFooter(document, cs, receipt);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException | com.google.zxing.WriterException ex) {
            throw new IllegalStateException("Could not generate receipt PDF", ex);
        }
    }

    private void drawCard(PDPageContentStream cs) throws IOException {
        cs.setNonStrokingColor(Color.WHITE);
        cs.addRect(26, 25, 543, 792);
        cs.fill();
        cs.setStrokingColor(LINE);
        cs.setLineWidth(.7f);
        cs.addRect(26, 25, 543, 792);
        cs.stroke();
    }

    private void drawHeader(PDDocument doc, PDPageContentStream cs, ReceiptDocument receipt) throws IOException {
        var resource = new ClassPathResource("static/branding/bakir-khata-mark.png");
        try (var stream = resource.getInputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, stream.readAllBytes(), "bakir-khata-brand");
            cs.drawImage(logo, 48, 723, 91, 78);
        }
        float firstWidth = draw(cs, BOLD, 25, NAVY, 146, 753, "Bakir");
        draw(cs, BOLD, 25, GOLD, 146 + firstWidth + 6, 753, "Khata");
        draw(cs, NORMAL, 9, MUTED, 148, 731, "LOANS  /  COMMUNITY  /  TRUST");
        draw(cs, BOLD, 8, MUTED, 436, 791, "OFFICIAL DEMO PDF");
        horizontal(cs, 47, 708, 548, LINE, .8f);
        draw(cs, BOLD, 17.3f, NAVY, 49, 675, receipt.title());
        draw(cs, NORMAL, 9, MUTED, 50, 652, "Reference: " + receipt.reference());
        draw(cs, NORMAL, 8, MUTED, 456, 652, "ISSUED ON DEMAND");
    }

    private void drawAmountBand(PDPageContentStream cs, ReceiptDocument r) throws IOException {
        cs.setNonStrokingColor(LIGHT_GOLD);
        cs.addRect(48, 521, 499, 107);
        cs.fill();
        draw(cs, BOLD, 9, GOLD, 66, 597, r.amountLabel());
        draw(cs, BOLD, 26, NAVY, 64, 552, r.amount());
        cs.setNonStrokingColor(new Color(232, 245, 237));
        cs.addRect(381, 565, 146, 26);
        cs.fill();
        drawFit(cs, BOLD, 8, GREEN, 387, 574, r.status(), 136);
        drawFit(cs, NORMAL, 9, MUTED, 50, 500, r.description(), 485);
    }

    private void drawDetails(PDPageContentStream cs, List<ReceiptDocument.Field> values) throws IOException {
        draw(cs, BOLD, 10, GOLD, 50, 469, "PARTICIPANT AND LOAN DETAILS");
        horizontal(cs, 49, 459, 545, LINE, .5f);
        int split = (values.size() + 1) / 2;
        for (int i = 0; i < values.size(); i++) {
            int col = i / split;
            int row = i % split;
            float x = col == 0 ? 52 : 308;
            float y = 441 - row * 38;
            drawFit(cs, NORMAL, 8, MUTED, x, y, values.get(i).label(), 236);
            drawFit(cs, BOLD, 10, INK, x, y - 15, values.get(i).value(), 234);
        }
    }

    private void drawBreakdown(PDPageContentStream cs, List<ReceiptDocument.Field> fields,
                               int detailCount) throws IOException {
        int detailRows = (detailCount + 1) / 2;
        float headerY = 441 - detailRows * 38 - 4;
        draw(cs, BOLD, 10, GOLD, 50, headerY, "FINANCIAL SUMMARY");
        horizontal(cs, 49, headerY - 10, 545, LINE, .5f);
        int split = (fields.size() + 1) / 2;
        for (int i = 0; i < fields.size(); i++) {
            int col = i / split;
            int row = i % split;
            float x = col == 0 ? 52 : 308;
            float y = headerY - 27 - row * 35;
            drawFit(cs, NORMAL, 8, MUTED, x, y, fields.get(i).label(), 234);
            drawFit(cs, BOLD, 10.5f, NAVY, x, y - 14, fields.get(i).value(), 234);
        }
    }

    private void drawFooter(PDDocument doc, PDPageContentStream cs, ReceiptDocument r)
            throws IOException, com.google.zxing.WriterException {
        horizontal(cs, 49, 116, 545, LINE, .7f);
        draw(cs, BOLD, 8, NAVY, 50, 101, "BAKIR KHATA / DEMONSTRATION DOCUMENT");
        draw(cs, NORMAL, 7.5f, MUTED, 50, 85,
                "Mobile and sandbox test checkouts do not transfer actual money.");
        draw(cs, NORMAL, 7.5f, MUTED, 50, 74,
                "Cash confirmation is entered by users; verification is not a bank guarantee.");
        if (r.verifyUrl() != null) {
            byte[] qr = makeQr(r.verifyUrl());
            PDImageXObject img = PDImageXObject.createFromByteArray(doc, qr, "signed-receipt-qr");
            cs.drawImage(img, 475, 35, 73, 73);
            draw(cs, NORMAL, 6.5f, MUTED, 477, 31, "SCAN TO VERIFY");
        } else {
            draw(cs, NORMAL, 7, MUTED, 50, 46,
                    "Signed QR verification requires the APP_RECEIPT_SECRET environment variable.");
        }
        draw(cs, NORMAL, 7, MUTED, 50, 52, "For the full record, sign in to your Bakir Khata account.");
    }

    private byte[] makeQr(String link) throws IOException, com.google.zxing.WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(link, BarcodeFormat.QR_CODE, 240, 240,
                Map.of(EncodeHintType.MARGIN, 1));
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xff132a4c : 0xffffffff);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void horizontal(PDPageContentStream cs, float x1, float y, float x2,
                            Color color, float width) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(width);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
    }

    private float draw(PDPageContentStream cs, PDFont font, float fontSize, Color color,
                       float x, float y, String value) throws IOException {
        String str = ascii(value);
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(str);
        cs.endText();
        return font.getStringWidth(str) / 1000f * fontSize;
    }

    private void drawFit(PDPageContentStream cs, PDFont font, float size, Color color,
                         float x, float y, String value, float maxWidth) throws IOException {
        String string = ascii(value);
        if (font.getStringWidth(string) * size / 1000f > maxWidth) {
            // Preserve as much identifying text as fits in a two-column row.
            while (!string.isEmpty() && font.getStringWidth(string + "...") * size / 1000f > maxWidth) {
                string = string.substring(0, string.length() - 1);
            }
            string += "...";
        }
        draw(cs, font, size, color, x, y, string);
    }

    /** PDF standard fonts are limited to WinAnsi; never allow unsupported glyphs to throw. */
    private String ascii(String value) {
        if (value == null) return "-";
        return value.replaceAll("[^\\x20-\\x7e]", "?");
    }
}
