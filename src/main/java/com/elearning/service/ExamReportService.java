package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.ExamRepository;
import com.elearning.repository.ExamSubmissionRepository;
import com.elearning.repository.FullscreenViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamReportService {

    private static final double PASS_THRESHOLD = 50.0;

    private final ExamRepository examRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final FullscreenViolationRepository fullscreenViolationRepository;

    @Transactional(readOnly = true)
    public byte[] generateExcelReport(Long examId) throws IOException {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("Examen introuvable"));

        List<ExamSubmission> submissions = submissionRepository.findByExamStudentExamId(examId);
        Map<Long, ExamSubmission> submissionByStudentId = submissions.stream()
            .collect(Collectors.toMap(s -> s.getExamStudent().getId(), s -> s));

        double totalMaxScore = exam.getQuestions().stream().mapToInt(ExamQuestion::getMaxScore).sum();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReportStyles styles = new ReportStyles(workbook);

            buildSummarySheet(workbook, styles, exam, submissions, totalMaxScore);
            buildResultsSheet(workbook, styles, exam, submissionByStudentId, totalMaxScore);
            buildDetailsSheet(workbook, styles, exam, submissionByStudentId);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Sheet 1: Résumé
    // ------------------------------------------------------------------
    private void buildSummarySheet(XSSFWorkbook workbook, ReportStyles styles, Exam exam,
                                    List<ExamSubmission> submissions, double totalMaxScore) {
        Sheet sheet = workbook.createSheet("Résumé");
        int rowNum = 0;

        rowNum = writeMergedTitle(sheet, styles, rowNum, "Rapport de résultats", 0, 4);

        Row examTitleRow = sheet.createRow(rowNum++);
        examTitleRow.createCell(0).setCellValue(exam.getTitle());
        examTitleRow.getCell(0).setCellStyle(styles.subtitle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4));
        rowNum++;

        rowNum = writeLabelValue(sheet, styles, rowNum, "Professeur", exam.getProfessor().getFirstName() + " " + exam.getProfessor().getLastName());
        rowNum = writeLabelValue(sheet, styles, rowNum, "Description", exam.getDescription() != null ? exam.getDescription() : "-");
        rowNum = writeLabelValue(sheet, styles, rowNum, "Statut de l'examen", translateExamStatus(exam.getStatus()));
        rowNum = writeLabelValue(sheet, styles, rowNum, "Date de génération", java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        rowNum++;

        rowNum = writeMergedTitle(sheet, styles, rowNum, "Statistiques", 0, 4);

        long totalStudents = exam.getStudents().size();
        long submittedCount = submissions.size();
        long gradedCount = submissions.stream().filter(s -> Boolean.TRUE.equals(s.getIsGraded())).count();

        List<Double> percentages = submissions.stream()
            .filter(s -> Boolean.TRUE.equals(s.getIsGraded()) && s.getTotalScore() != null && s.getMaxScore() != null && s.getMaxScore() > 0)
            .map(s -> s.getTotalScore() / s.getMaxScore() * 100.0)
            .toList();

        double avgPercentage = percentages.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxPercentage = percentages.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double minPercentage = percentages.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        long passCount = percentages.stream().filter(p -> p >= PASS_THRESHOLD).count();
        double passRate = gradedCount > 0 ? (double) passCount / gradedCount * 100.0 : 0;

        rowNum = writeLabelValue(sheet, styles, rowNum, "Nombre de questions", String.valueOf(exam.getQuestions().size()));
        rowNum = writeLabelValue(sheet, styles, rowNum, "Note totale possible", formatScore(totalMaxScore) + " pts");
        rowNum = writeLabelValue(sheet, styles, rowNum, "Étudiants inscrits", String.valueOf(totalStudents));
        rowNum = writeLabelValue(sheet, styles, rowNum, "Copies soumises", submittedCount + " / " + totalStudents);
        rowNum = writeLabelValue(sheet, styles, rowNum, "Copies corrigées par l'IA", gradedCount + " / " + submittedCount);

        if (gradedCount > 0) {
            rowNum = writeLabelValue(sheet, styles, rowNum, "Moyenne générale", String.format("%.1f%%", avgPercentage));
            rowNum = writeLabelValue(sheet, styles, rowNum, "Meilleure note", String.format("%.1f%%", maxPercentage));
            rowNum = writeLabelValue(sheet, styles, rowNum, "Note la plus basse", String.format("%.1f%%", minPercentage));
            rowNum = writeLabelValue(sheet, styles, rowNum, "Taux de réussite (≥ 50%)", passCount + " / " + gradedCount + " (" + String.format("%.1f%%", passRate) + ")");
        } else {
            rowNum = writeLabelValue(sheet, styles, rowNum, "Moyenne générale", "En attente de correction");
        }

        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 12000);
    }

    private int writeMergedTitle(Sheet sheet, ReportStyles styles, int rowNum, String title, int firstCol, int lastCol) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(firstCol);
        cell.setCellValue(title);
        cell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, firstCol, lastCol));
        return rowNum + 1;
    }

    private int writeLabelValue(Sheet sheet, ReportStyles styles, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.value);
        return rowNum + 1;
    }

    // ------------------------------------------------------------------
    // Sheet 2: Résultats
    // ------------------------------------------------------------------
    private void buildResultsSheet(XSSFWorkbook workbook, ReportStyles styles, Exam exam,
                                    Map<Long, ExamSubmission> submissionByStudentId, double totalMaxScore) {
        Sheet sheet = workbook.createSheet("Résultats");
        int rowNum = 0;

        rowNum = writeMergedTitle(sheet, styles, rowNum, "Résultats - " + exam.getTitle(), 0, 6);
        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Généré le " +
            java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        dateRow.getCell(0).setCellStyle(styles.subtitle);
        rowNum++;

        int headerRowIndex = rowNum;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Nom", "Email", "Note obtenue", "Note max", "Pourcentage", "Statut", "Violations Fullscreen", "Appréciation générale (IA)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        List<ExamStudent> sortedStudents = exam.getStudents().stream()
            .sorted(Comparator.comparing(ExamStudent::getStudentName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        for (ExamStudent student : sortedStudents) {
            Row row = sheet.createRow(rowNum++);
            ExamSubmission sub = submissionByStudentId.get(student.getId());

            row.createCell(0).setCellValue(student.getStudentName());
            row.getCell(0).setCellStyle(styles.data);
            row.createCell(1).setCellValue(student.getStudentEmail());
            row.getCell(1).setCellStyle(styles.data);

            if (sub != null && Boolean.TRUE.equals(sub.getIsGraded())) {
                double obtained = sub.getTotalScore() != null ? sub.getTotalScore() : 0;
                double max = sub.getMaxScore() != null ? sub.getMaxScore() : totalMaxScore;
                double percentage = max > 0 ? obtained / max * 100.0 : 0;

                setNumericCell(row, 2, obtained, styles.score);
                setNumericCell(row, 3, max, styles.score);

                Cell pctCell = row.createCell(4);
                pctCell.setCellValue(percentage / 100.0);
                pctCell.setCellStyle(percentage >= PASS_THRESHOLD ? styles.percentPass : styles.percentFail);

                Cell statusCell = row.createCell(5);
                statusCell.setCellValue(percentage >= PASS_THRESHOLD ? "Admis" : "Non admis");
                statusCell.setCellStyle(percentage >= PASS_THRESHOLD ? styles.pass : styles.fail);

                // Violations fullscreen
                List<FullscreenViolation> violations = fullscreenViolationRepository.findBySubmissionIdOrderByViolationTimeAsc(sub.getId());
                Cell violationsCell = row.createCell(6);
                String violationsText = violations.isEmpty() ? "Aucune" : violations.size() + " violation(s)";
                violationsCell.setCellValue(violationsText);
                violationsCell.setCellStyle(violations.size() >= 3 ? styles.fail : styles.data);

                Cell reportCell = row.createCell(7);
                reportCell.setCellValue(sub.getAiReport() != null ? sub.getAiReport() : "");
                reportCell.setCellStyle(styles.wrap);
            } else if (sub != null) {
                setTextCell(row, 2, "-", styles.data);
                setNumericCell(row, 3, totalMaxScore, styles.score);
                setTextCell(row, 4, "-", styles.data);
                setTextCell(row, 5, "Soumis - correction en cours", styles.data);
                
                List<FullscreenViolation> violations = fullscreenViolationRepository.findBySubmissionIdOrderByViolationTimeAsc(sub.getId());
                Cell violationsCell = row.createCell(6);
                String violationsText = violations.isEmpty() ? "Aucune" : violations.size() + " violation(s)";
                violationsCell.setCellValue(violationsText);
                violationsCell.setCellStyle(violations.size() >= 3 ? styles.fail : styles.data);

                setTextCell(row, 7, "", styles.data);
            } else {
                setTextCell(row, 2, "-", styles.data);
                setNumericCell(row, 3, totalMaxScore, styles.score);
                setTextCell(row, 4, "-", styles.data);
                setTextCell(row, 5, translateStudentStatus(student.getStatus()), styles.data);
                setTextCell(row, 6, "N/A", styles.data);
                setTextCell(row, 7, "", styles.data);
            }
        }

        sheet.createFreezePane(0, headerRowIndex + 1);
        sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, headerRowIndex, 0, headers.length - 1));

        sheet.setColumnWidth(0, 6500);
        sheet.setColumnWidth(1, 8500);
        sheet.setColumnWidth(2, 3500);
        sheet.setColumnWidth(3, 3500);
        sheet.setColumnWidth(4, 3500);
        sheet.setColumnWidth(5, 6000);
        sheet.setColumnWidth(6, 5500);
        sheet.setColumnWidth(7, 18000);
    }

    // ------------------------------------------------------------------
    // Sheet 3: Détails par question
    // ------------------------------------------------------------------
    private void buildDetailsSheet(XSSFWorkbook workbook, ReportStyles styles, Exam exam,
                                    Map<Long, ExamSubmission> submissionByStudentId) {
        Sheet sheet = workbook.createSheet("Détails par question");

        List<ExamQuestion> questions = exam.getQuestions().stream()
            .sorted(Comparator.comparing(ExamQuestion::getOrderIndex))
            .toList();

        int rowNum = 0;
        int lastCol = 1 + questions.size() * 2 - 1;
        rowNum = writeMergedTitle(sheet, styles, rowNum, "Détail des notes par question", 0, Math.max(lastCol, 4));
        rowNum++;

        int headerRowIndex = rowNum;
        Row headerRow = sheet.createRow(rowNum++);
        Cell nameHeader = headerRow.createCell(0);
        nameHeader.setCellValue("Nom");
        nameHeader.setCellStyle(styles.header);
        Cell emailHeader = headerRow.createCell(1);
        emailHeader.setCellValue("Email");
        emailHeader.setCellStyle(styles.header);

        int col = 2;
        for (ExamQuestion q : questions) {
            Cell scoreHeader = headerRow.createCell(col++);
            scoreHeader.setCellValue("Q" + q.getOrderIndex() + " (/" + q.getMaxScore() + ")");
            scoreHeader.setCellStyle(styles.header);

            Cell commentHeader = headerRow.createCell(col++);
            commentHeader.setCellValue("Q" + q.getOrderIndex() + " - Commentaire IA");
            commentHeader.setCellStyle(styles.header);
        }

        // Question statements as a sub-header row for context
        Row questionRow = sheet.createRow(rowNum++);
        questionRow.createCell(0).setCellValue("");
        questionRow.createCell(1).setCellValue("Énoncés");
        questionRow.getCell(1).setCellStyle(styles.subtitle);
        col = 2;
        for (ExamQuestion q : questions) {
            Cell qCell = questionRow.createCell(col);
            qCell.setCellValue(q.getQuestionText());
            qCell.setCellStyle(styles.wrap);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, col, col + 1));
            col += 2;
        }

        List<ExamStudent> sortedStudents = exam.getStudents().stream()
            .sorted(Comparator.comparing(ExamStudent::getStudentName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        for (ExamStudent student : sortedStudents) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(student.getStudentName());
            row.getCell(0).setCellStyle(styles.data);
            row.createCell(1).setCellValue(student.getStudentEmail());
            row.getCell(1).setCellStyle(styles.data);

            ExamSubmission sub = submissionByStudentId.get(student.getId());
            Map<Long, ExamAnswer> answersByQuestionId = sub != null
                ? sub.getAnswers().stream().collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a))
                : Map.of();

            int c = 2;
            for (ExamQuestion q : questions) {
                ExamAnswer answer = answersByQuestionId.get(q.getId());
                if (sub != null && Boolean.TRUE.equals(sub.getIsGraded()) && answer != null) {
                    setNumericCell(row, c, answer.getObtainedScore() != null ? answer.getObtainedScore() : 0, styles.score);
                    Cell commentCell = row.createCell(c + 1);
                    commentCell.setCellValue(answer.getAiComment() != null ? answer.getAiComment() : "");
                    commentCell.setCellStyle(styles.wrap);
                } else {
                    setTextCell(row, c, "-", styles.data);
                    setTextCell(row, c + 1, "", styles.data);
                }
                c += 2;
            }
        }

        sheet.createFreezePane(2, headerRowIndex + 2);

        sheet.setColumnWidth(0, 6500);
        sheet.setColumnWidth(1, 8500);
        for (int i = 2; i < 2 + questions.size() * 2; i += 2) {
            sheet.setColumnWidth(i, 3500);
            sheet.setColumnWidth(i + 1, 12000);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private void setNumericCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setTextCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String formatScore(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    private String translateExamStatus(ExamStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case PUBLISHED -> "Publié";
            case CLOSED -> "Clôturé";
        };
    }

    private String translateStudentStatus(StudentExamStatus status) {
        return switch (status) {
            case INVITED -> "Invité (pas encore commencé)";
            case STARTED -> "En cours";
            case SUBMITTED -> "Soumis - correction en cours";
            case GRADED -> "Corrigé";
        };
    }

    /**
     * Centralizes all cell styles so they are created once per workbook.
     */
    private static class ReportStyles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle label;
        final CellStyle value;
        final CellStyle header;
        final CellStyle data;
        final CellStyle score;
        final CellStyle percentPass;
        final CellStyle percentFail;
        final CellStyle pass;
        final CellStyle fail;
        final CellStyle wrap;

        ReportStyles(XSSFWorkbook workbook) {
            title = createTitleStyle(workbook);
            subtitle = createSubtitleStyle(workbook);
            label = createLabelStyle(workbook);
            value = createValueStyle(workbook);
            header = createHeaderStyle(workbook);
            data = createBorderedStyle(workbook, null, IndexedColors.BLACK, false);
            score = createScoreStyle(workbook);
            percentPass = createPercentStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN);
            percentFail = createPercentStyle(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED);
            pass = createBorderedStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN, true);
            fail = createBorderedStyle(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED, true);
            wrap = createWrapStyle(workbook);
        }

        private CellStyle createTitleStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 16);
            font.setColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFont(font);
            return style;
        }

        private CellStyle createSubtitleStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setItalic(true);
            font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setFont(font);
            return style;
        }

        private CellStyle createLabelStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            return style;
        }

        private CellStyle createValueStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            return style;
        }

        private CellStyle createHeaderStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyThinBorders(style);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            return style;
        }

        private CellStyle createBorderedStyle(Workbook workbook, IndexedColors fill, IndexedColors fontColor, boolean bold) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(bold);
            if (fontColor != null) font.setColor(fontColor.getIndex());
            style.setFont(font);
            if (fill != null) {
                style.setFillForegroundColor(fill.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            applyThinBorders(style);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private CellStyle createScoreStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("0.##"));
            applyThinBorders(style);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private CellStyle createPercentStyle(Workbook workbook, IndexedColors fill, IndexedColors fontColor) {
            CellStyle style = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("0.0%"));
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(fontColor.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyThinBorders(style);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private CellStyle createWrapStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            applyThinBorders(style);
            return style;
        }

        private void applyThinBorders(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
