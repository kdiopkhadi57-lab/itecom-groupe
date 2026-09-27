package com.elearning.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentTextExtractorService {

    public String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".pdf")) {
            return extractFromPdf(file);
        } else if (filename.endsWith(".docx")) {
            return extractFromWord(file);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return extractFromExcel(file);
        } else {
            throw new IllegalArgumentException("Format de fichier non supporté. Utilisez PDF, Word (.docx) ou Excel.");
        }
    }

    private String extractFromPdf(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractFromWord(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                sb.append(paragraph.getText()).append("\n");
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText()).append("\t");
                    }
                    sb.append("\n");
                }
            }
        }
        return normalizeExamText(sb.toString());
    }

    /**
     * Extraction qui respecte la mise en page du document d'origine : paragraphes et tableaux
     * dans l'ordre du document, retours à la ligne conservés, numérotation des listes restituée.
     * Utilisée pour afficher le sujet / la correction tels que le professeur les a rédigés.
     */
    public String extractLayoutText(MultipartFile file) throws IOException {
        return extractLayoutText(file.getOriginalFilename(), file.getBytes());
    }

    public String extractLayoutText(String filename, byte[] data) throws IOException {
        String name = filename != null ? filename.toLowerCase() : "";
        if (name.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(data)) {
                return cleanLayout(new PDFTextStripper().getText(doc));
            }
        } else if (name.endsWith(".docx")) {
            try (InputStream in = new ByteArrayInputStream(data)) {
                return cleanLayout(extractWordLayout(in));
            }
        } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder();
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
                for (Sheet sheet : workbook) {
                    for (Row row : sheet) {
                        StringBuilder line = new StringBuilder();
                        for (Cell cell : row) {
                            if (line.length() > 0) line.append('\t');
                            line.append(formatter.formatCellValue(cell));
                        }
                        sb.append(line).append('\n');
                    }
                    sb.append('\n');
                }
            }
            return cleanLayout(sb.toString());
        }
        throw new IllegalArgumentException("Format de fichier non supporté. Utilisez PDF, Word (.docx) ou Excel.");
    }

    private String extractWordLayout(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        // Compteurs de numérotation par liste (numId) et par niveau
        Map<BigInteger, int[]> counters = new HashMap<>();
        try (XWPFDocument doc = new XWPFDocument(in)) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    sb.append(listPrefix(paragraph, counters)).append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder line = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (line.length() > 0) line.append('\t');
                            line.append(cell.getText().replaceAll("\\s*\\R\\s*", " ").trim());
                        }
                        sb.append(line).append('\n');
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String listPrefix(XWPFParagraph paragraph, Map<BigInteger, int[]> counters) {
        BigInteger numId = paragraph.getNumID();
        if (numId == null) return "";
        int level = paragraph.getNumIlvl() != null ? paragraph.getNumIlvl().intValue() : 0;
        String format = paragraph.getNumFmt();
        if (format == null || "bullet".equals(format)) return "  ".repeat(level) + "• ";
        int[] levels = counters.computeIfAbsent(numId, k -> new int[9]);
        if (level < 0 || level >= levels.length) return "";
        levels[level]++;
        for (int i = level + 1; i < levels.length; i++) levels[i] = 0;
        String text = paragraph.getNumLevelText();
        if (text == null || text.isBlank()) text = "%" + (level + 1) + ".";
        for (int i = 0; i <= level; i++) {
            text = text.replace("%" + (i + 1), formatNumber(Math.max(levels[i], 1), i == level ? format : "decimal"));
        }
        return "  ".repeat(level) + text + " ";
    }

    private String formatNumber(int n, String format) {
        return switch (format) {
            case "lowerLetter" -> String.valueOf((char) ('a' + (n - 1) % 26));
            case "upperLetter" -> String.valueOf((char) ('A' + (n - 1) % 26));
            case "lowerRoman" -> toRoman(n).toLowerCase();
            case "upperRoman" -> toRoman(n);
            default -> String.valueOf(n);
        };
    }

    private String toRoman(int n) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) { sb.append(symbols[i]); n -= values[i]; }
        }
        return sb.toString();
    }

    /** Uniformise les fins de ligne sans toucher à la mise en page (3 lignes vides max). */
    private String cleanLayout(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\u00a0', ' ').replace('\u202f', ' ')
            .replaceAll(" +\n", "\n")
            .replaceAll("\n{4,}", "\n\n\n")
            .strip();
    }

    public String normalizeExamText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String compact = rawText
            .replace("\r", "\n")
            .replace("\t", " ")
            .replace("\u00a0", " ")
            .replace("\u202F", " ")
            .replaceAll("\\s+", " ")
            .trim();

        String normalized = compact
            .replaceAll("(?i)\\bQ\\s*(\\d+)\\s*(?=\\p{Lu})", "Question $1: ")
            .replaceAll("(?i)(?<=^|\\s)([A-D])(?=É|\\p{Lu}|\\d)", "$1) ")
            .replaceAll("(?i)(?<=^|\\s)([A-D])\\s+(?=\\d|\\p{Lu})", "$1) ")
            .replaceAll("(?i)(?<=^|\\s)([A-D])\\.(?=\\s)", "$1) ")
            .replaceAll("(?i)(?<=^|\\s)(A\\)|B\\)|C\\)|D\\))", "\n$1")
            .replaceAll("(?i)(?<=\\S)(?=Question\\s*\\d+\\s*:)", "\n")
            .replaceAll("(?i)(?<=\\S)Bonne réponse:", "\nBonne réponse:")
            .replaceAll("(?i)(?<=\\S)Bonnes réponses:", "\nBonnes réponses:")
            .replaceAll("\\n\\s+", "\n")
            .replaceAll("\\s{2,}", " ")
            .trim();

        return normalized;
    }

    private String extractFromExcel(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        sb.append(getCellValue(cell)).append("\t");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
