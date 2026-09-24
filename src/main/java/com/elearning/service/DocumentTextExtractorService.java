package com.elearning.service;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
