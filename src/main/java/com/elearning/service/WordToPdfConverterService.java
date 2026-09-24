package com.elearning.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Convertit un document Word (.doc/.docx) en PDF lisible (texte + tableaux),
 * sans dépendance externe (LibreOffice non disponible) — utilise Apache POI
 * pour l'extraction du contenu et PDFBox pour le rendu paginé.
 */
@Service
public class WordToPdfConverterService {

    private static final float MARGIN = 50f;
    private static final float FONT_SIZE = 11f;
    private static final float TITLE_FONT_SIZE = 14f;
    private static final float LEADING = 16f;

    public byte[] convertDocxToPdf(InputStream docxStream) throws IOException {
        List<Block> blocks = extractBlocks(docxStream);

        try (PDDocument pdf = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PageCursor cursor = new PageCursor(pdf, font);

            for (Block block : blocks) {
                PDFont activeFont = block.heading ? boldFont : font;
                float activeSize = block.heading ? TITLE_FONT_SIZE : FONT_SIZE;
                cursor.writeParagraph(block.text, activeFont, activeSize);
            }
            cursor.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private List<Block> extractBlocks(InputStream docxStream) throws IOException {
        List<Block> blocks = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(docxStream)) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null) continue;
                boolean heading = isHeading(paragraph);
                blocks.add(new Block(text, heading));
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (line.length() > 0) line.append("  |  ");
                        line.append(cell.getText().trim());
                    }
                    blocks.add(new Block(line.toString(), false));
                }
                blocks.add(new Block("", false));
            }
        }
        return blocks;
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style != null && style.toLowerCase().contains("heading")) return true;
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.isBold() && run.getFontSize() > 0 && run.getFontSize() >= 13) return true;
        }
        return false;
    }

    private record Block(String text, boolean heading) {}

    /** Gère la pagination et le retour à la ligne au fil de l'écriture du texte. */
    private static class PageCursor {
        private final PDDocument pdf;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private final float pageWidth;
        private final float usableWidth;

        PageCursor(PDDocument pdf, PDFont defaultFont) throws IOException {
            this.pdf = pdf;
            this.pageWidth = PDRectangle.A4.getWidth();
            this.usableWidth = pageWidth - 2 * MARGIN;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        void writeParagraph(String text, PDFont font, float fontSize) throws IOException {
            if (text == null || text.isBlank()) {
                y -= LEADING * 0.6f;
                return;
            }
            for (String line : wrap(text, font, fontSize)) {
                if (y < MARGIN + LEADING) newPage();
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(sanitize(line));
                stream.endText();
                y -= LEADING;
            }
        }

        private List<String> wrap(String text, PDFont font, float fontSize) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String rawLine : text.split("\n")) {
                StringBuilder current = new StringBuilder();
                for (String word : rawLine.split(" ")) {
                    String candidate = current.length() == 0 ? word : current + " " + word;
                    float width = font.getStringWidth(sanitize(candidate)) / 1000 * fontSize;
                    if (width > usableWidth && current.length() > 0) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                lines.add(current.toString());
            }
            return lines;
        }

        /** PDFBox WinAnsi ne supporte pas tous les caractères Unicode (emojis, etc.). */
        private String sanitize(String s) {
            return s.replaceAll("[^\\x00-\\xFF]", "?");
        }

        void close() throws IOException {
            if (stream != null) stream.close();
        }
    }
}
