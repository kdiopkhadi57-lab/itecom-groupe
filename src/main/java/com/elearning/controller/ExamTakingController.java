package com.elearning.controller;

import com.elearning.dto.request.ExamSubmitRequest;
import com.elearning.dto.request.FullscreenViolationRequest;
import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.ExamTakeResponse;
import com.elearning.service.ExamSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamTakingController {

    private final ExamSubmissionService submissionService;

    @Value("${app.exam.grading-delay-minutes:60}")
    private long gradingDelayMinutes;

    @GetMapping("/access/{token}")
    public ResponseEntity<ApiResponse<ExamTakeResponse>> getExamByToken(@PathVariable String token) {
        ExamTakeResponse exam = submissionService.getExamByToken(token);
        return ResponseEntity.ok(ApiResponse.success(exam));
    }

    @PostMapping("/access/{token}/start")
    public ResponseEntity<ApiResponse<ExamTakeResponse>> startExam(@PathVariable String token) {
        ExamTakeResponse exam = submissionService.startExam(token);
        return ResponseEntity.ok(ApiResponse.success("Examen démarré", exam));
    }

    @PostMapping("/access/{token}/submit")
    public ResponseEntity<ApiResponse<Void>> submitExam(
            @PathVariable String token,
            @RequestBody @Valid ExamSubmitRequest request) {
        submissionService.submitExam(token, request);
        String delay = gradingDelayMinutes >= 60
            ? (gradingDelayMinutes / 60) + " heure(s)"
            : gradingDelayMinutes + " minute(s)";
        return ResponseEntity.ok(ApiResponse.success(
            "Examen soumis avec succès. Vos résultats vous seront envoyés par email dans " + delay + ".", null));
    }

    @PostMapping("/access/{token}/fullscreen-violation")
    public ResponseEntity<ApiResponse<Void>> recordFullscreenViolation(
            @PathVariable String token,
            @RequestBody @Valid FullscreenViolationRequest request) {
        submissionService.recordFullscreenViolation(token, request);
        return ResponseEntity.ok(ApiResponse.success("Violation enregistrée", null));
    }
}
