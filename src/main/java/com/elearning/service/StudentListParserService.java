package com.elearning.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class StudentListParserService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    public record StudentInfo(String name, String email) {}

    public List<StudentInfo> parseFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return parseExcel(file);
        } else if (filename.endsWith(".pdf")) {
            return parsePdf(file);
        } else if (filename.endsWith(".docx") || filename.endsWith(".doc")) {
            return parseWord(file);
        } else if (filename.endsWith(".csv") || filename.endsWith(".txt")) {
            return parseCsv(file);
        } else {
            throw new IllegalArgumentException("Format non supporté. Utilisez Excel (.xlsx), PDF, Word (.docx) ou CSV.");
        }
    }

    private List<StudentInfo> parseCsv(MultipartFile file) throws IOException {
        List<StudentInfo> students = new ArrayList<>();
        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            // Ignorer en-tête si première ligne sans email valide
            if (i == 0 && !EMAIL_PATTERN.matcher(line).find()) continue;
            String[] parts = line.split(",", 2);
            if (parts.length == 2) {
                String name  = parts[0].trim().replaceAll("^\"|\"$", "");
                String email = parts[1].trim().replaceAll("^\"|\"$", "").toLowerCase();
                if (isValidStudent(name, email)) students.add(new StudentInfo(name, email));
            } else {
                // Ligne sans virgule : tenter extraction par regex (email + nom)
                Matcher m = EMAIL_PATTERN.matcher(line);
                if (m.find()) {
                    String email = m.group().toLowerCase();
                    String name  = line.substring(0, m.start()).trim().replaceAll("[,;]+$", "");
                    if (!name.isEmpty()) students.add(new StudentInfo(name, email));
                }
            }
        }
        return students;
    }

    private List<StudentInfo> parseExcel(MultipartFile file) throws IOException {
        List<StudentInfo> students = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // skip header
                String name = getCellValue(row.getCell(0));
                String email = getCellValue(row.getCell(1));
                if (isValidStudent(name, email)) {
                    students.add(new StudentInfo(name.trim(), email.trim().toLowerCase()));
                }
            }
        }
        return students;
    }

    private List<StudentInfo> parsePdf(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return extractStudentsFromText(text);
        }
    }

    private List<StudentInfo> parseWord(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                sb.append(paragraph.getText()).append("\n");
            }
        }
        return extractStudentsFromText(sb.toString());
    }

    private List<StudentInfo> extractStudentsFromText(String text) {
        List<StudentInfo> students = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher emailMatcher = EMAIL_PATTERN.matcher(line);
            if (emailMatcher.find()) {
                String email = emailMatcher.group().toLowerCase();
                String name = line.substring(0, emailMatcher.start()).trim();
                if (name.isEmpty()) {
                    name = email.split("@")[0];
                }
                name = name.replaceAll("[,;:\\-|]+$", "").trim();
                if (!name.isEmpty()) {
                    students.add(new StudentInfo(name, email));
                }
            }
        }
        return students;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    private boolean isValidStudent(String name, String email) {
        return name != null && !name.isBlank()
            && email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
