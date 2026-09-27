package com.elearning.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class StudentListParserService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    public record StudentInfo(String name, String email, String firstName, String lastName, String level, String password) {
        public StudentInfo(String name, String email) {
            this(name, email, null, null, null, null);
        }
    }

    private enum Column { FULL_NAME, LAST_NAME, FIRST_NAME, LEVEL, EMAIL, PASSWORD }

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
        if (lines.length > 0) {
            String separator = lines[0].contains(";") ? ";" : ",";
            List<List<String>> rows = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank()) continue;
                List<String> cells = new ArrayList<>();
                for (String cell : line.split(separator, -1)) cells.add(cell.trim().replaceAll("^\"|\"$", ""));
                rows.add(cells);
            }
            List<StudentInfo> fromHeader = parseRowsWithHeader(rows);
            if (fromHeader != null) return fromHeader;
        }
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
            List<List<String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(0, row.getLastCellNum()); c++) cells.add(getCellValue(row.getCell(c)).trim());
                rows.add(cells);
            }
            List<StudentInfo> fromHeader = parseRowsWithHeader(rows);
            if (fromHeader != null) return fromHeader;
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
            for (XWPFTable table : doc.getTables()) {
                List<List<String>> rows = new ArrayList<>();
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) cells.add(cell.getText().trim());
                    rows.add(cells);
                }
                List<StudentInfo> fromHeader = parseRowsWithHeader(rows);
                if (fromHeader != null) return fromHeader;
            }
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

    private static final DataFormatter CELL_FORMATTER = new DataFormatter();

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        // DataFormatter rend la valeur telle qu'affichée (ex. mot de passe numérique « 0123 » ou « 1234 » sans « .0 »)
        return CELL_FORMATTER.formatCellValue(cell);
    }

    /**
     * Lecture d'un tableau dont la première ligne non vide est un en-tête
     * (Nom, Prénom, Niveau, Email, Mot de passe…). Retourne null si aucun en-tête reconnu,
     * afin de retomber sur l'ancien format « Nom complet | Email ».
     */
    private List<StudentInfo> parseRowsWithHeader(List<List<String>> rows) {
        int headerIndex = -1;
        Map<Column, Integer> columns = null;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).stream().allMatch(String::isBlank)) continue;
            headerIndex = i;
            columns = detectColumns(rows.get(i));
            break;
        }
        if (columns == null || !columns.containsKey(Column.EMAIL)) return null;
        boolean hasName = columns.containsKey(Column.FULL_NAME)
            || columns.containsKey(Column.LAST_NAME) || columns.containsKey(Column.FIRST_NAME);
        // Sans colonne mot de passe / niveau / prénom, l'ancien format suffit
        if (!hasName) return null;

        List<StudentInfo> students = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String email = cell(row, columns.get(Column.EMAIL)).toLowerCase();
            String lastName = cell(row, columns.get(Column.LAST_NAME));
            String firstName = cell(row, columns.get(Column.FIRST_NAME));
            String fullName = cell(row, columns.get(Column.FULL_NAME));
            if (fullName.isBlank()) {
                fullName = (firstName + " " + lastName).trim();
            } else if (firstName.isBlank() && lastName.isBlank()) {
                // « Prénom(s) Nom » : le dernier mot est le nom de famille
                int lastSpace = fullName.lastIndexOf(' ');
                firstName = lastSpace > 0 ? fullName.substring(0, lastSpace).trim() : "";
                lastName = lastSpace > 0 ? fullName.substring(lastSpace + 1).trim() : fullName;
            }
            if (!isValidStudent(fullName, email)) continue;
            students.add(new StudentInfo(fullName, email, blankToNull(firstName), blankToNull(lastName),
                blankToNull(cell(row, columns.get(Column.LEVEL))), blankToNull(cell(row, columns.get(Column.PASSWORD)))));
        }
        return students;
    }

    private Map<Column, Integer> detectColumns(List<String> header) {
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        for (int i = 0; i < header.size(); i++) {
            String h = normalizeHeader(header.get(i));
            Column column;
            if (h.isEmpty()) continue;
            if (h.contains("mot de passe") || h.contains("password") || h.equals("mdp") || h.equals("code") || h.startsWith("code d acces")) column = Column.PASSWORD;
            else if (h.contains("mail")) column = Column.EMAIL;
            else if (h.contains("prenom") || h.contains("first name") || h.equals("firstname")) {
                // « Nom et prénom » / « Prénom Nom » désignent le nom complet
                column = h.contains("nom et") || h.startsWith("nom ") || h.contains("prenom nom") || h.contains("prenom et nom")
                    ? Column.FULL_NAME : Column.FIRST_NAME;
            }
            else if (h.contains("niveau") || h.contains("classe") || h.contains("level") || h.contains("promotion")) column = Column.LEVEL;
            else if (h.contains("complet") || h.equals("name") || h.contains("full name") || h.equals("etudiant")) column = Column.FULL_NAME;
            else if (h.equals("nom") || h.contains("nom de famille") || h.contains("last name") || h.equals("lastname")) column = Column.LAST_NAME;
            else continue;
            columns.putIfAbsent(column, i);
        }
        // Une colonne « Nom » seule (sans « Prénom ») contient le nom complet, comme dans l'ancien modèle
        if (columns.containsKey(Column.LAST_NAME) && !columns.containsKey(Column.FIRST_NAME)
                && !columns.containsKey(Column.FULL_NAME)) {
            columns.put(Column.FULL_NAME, columns.remove(Column.LAST_NAME));
        }
        return columns;
    }

    private String normalizeHeader(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String cell(List<String> row, Integer index) {
        if (index == null || index >= row.size() || row.get(index) == null) return "";
        return row.get(index).trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isValidStudent(String name, String email) {
        return name != null && !name.isBlank()
            && email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
