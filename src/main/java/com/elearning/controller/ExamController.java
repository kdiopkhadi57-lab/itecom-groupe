package com.elearning.controller;

import com.elearning.dto.request.ExamCreateRequest;
import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.ExamResponse;
import com.elearning.dto.response.ExamSubmissionDetailResponse;
import com.elearning.service.ExamReportService;
import com.elearning.service.ExamScanSubmissionService;
import com.elearning.service.ExamService;
import com.elearning.service.StudentListParserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher/exams")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamReportService reportService;
    private final ExamScanSubmissionService scanSubmissionService;
    private final StudentListParserService parserService;

    // ── Créer un examen ───────────────────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExamResponse>> createExam(
            @RequestPart("exam") @Valid ExamCreateRequest request,
            @RequestPart("studentList") MultipartFile studentListFile,
            @RequestPart(value = "examFile", required = false) MultipartFile examFile,
            @RequestPart(value = "correctionFile", required = false) MultipartFile correctionFile,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        ExamResponse exam = examService.createExam(request, studentListFile, examFile, correctionFile, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Examen créé avec succès", exam));
    }

    // ── Template Excel à télécharger ──────────────────────────────────────
    @GetMapping("/student-template")
    public ResponseEntity<byte[]> downloadStudentTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Étudiants");

            // Style en-tête
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // En-tête
            Row header = sheet.createRow(0);
            Cell h1 = header.createCell(0); h1.setCellValue("Nom complet"); h1.setCellStyle(headerStyle);
            Cell h2 = header.createCell(1); h2.setCellValue("Email");       h2.setCellStyle(headerStyle);

            // Exemples
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Moussa Ndiaye");
            r1.createCell(1).setCellValue("moussa.ndiaye@etudiant.com");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Fatou Diallo");
            r2.createCell(1).setCellValue("fatou.diallo@etudiant.com");

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Ibrahima Seck");
            r3.createCell(1).setCellValue("ibrahima.seck@etudiant.com");

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

            wb.write(out);
            byte[] bytes = out.toByteArray();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_etudiants.xlsx\"")
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(bytes);
        }
    }

    // ── Parser la liste sans sauvegarder (prévisualisation) ──────────────
    @PostMapping("/parse-students")
    public ResponseEntity<?> parseStudentList(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            if (file == null || file.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide ou manquant"));
            List<StudentListParserService.StudentInfo> students = parserService.parseFile(file);
            List<Map<String, String>> result = students.stream()
                .map(s -> Map.of("name", s.name(), "email", s.email()))
                .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("students", result, "count", students.size()));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    // ── Ajouter des étudiants à un examen existant ────────────────────────
    @PostMapping(value = "/{id}/add-students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExamResponse>> addStudents(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {
        try {
            ExamResponse exam = examService.addStudentsToExam(id, file, user.getUsername());
            return ResponseEntity.ok(ApiResponse.success(
                exam.getStudentCount() + " étudiants ajoutés à l'examen", exam));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage() != null ? e.getMessage() : "Erreur"));
        }
    }

    // ── Liste des examens ─────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamResponse>>> getMyExams(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(examService.getExamsByProfessor(user.getUsername())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExamResponse>> getExam(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(examService.getExamById(id, user.getUsername())));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ExamResponse>> publishExam(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        ExamResponse exam = examService.publishExam(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Examen publié et invitations envoyées", exam));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<ExamResponse>> closeExam(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        ExamResponse exam = examService.closeExam(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Examen clôturé", exam));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExam(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        examService.deleteExam(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Examen supprimé", null));
    }

    @PostMapping(value = "/{id}/students/{studentId}/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExamSubmissionDetailResponse>> uploadScannedCopy(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        ExamSubmissionDetailResponse detail = scanSubmissionService.uploadAndGradeScan(id, studentId, files, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Copie scannée analysée et corrigée", detail));
    }

    @GetMapping("/{id}/students/{studentId}/submission")
    public ResponseEntity<ApiResponse<ExamSubmissionDetailResponse>> getSubmissionDetail(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserDetails user) {
        ExamSubmissionDetailResponse detail = scanSubmissionService.getSubmissionDetail(id, studentId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) throws IOException {
        byte[] report = reportService.generateExcelReport(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exam-report-" + id + ".xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(report);
    }
}
