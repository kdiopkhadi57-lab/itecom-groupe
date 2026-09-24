package com.elearning.service;

import com.elearning.dto.response.ExamAnswerDetailResponse;
import com.elearning.dto.response.ExamSubmissionDetailResponse;
import com.elearning.dto.response.FullscreenViolationResponse;
import com.elearning.entity.*;
import com.elearning.repository.ExamStudentRepository;
import com.elearning.repository.ExamSubmissionRepository;
import com.elearning.repository.FullscreenViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gère l'import des copies scannées (photos ou PDF) d'un étudiant après un examen papier,
 * et déclenche la correction IA qui compare l'écrit de la copie aux réponses de référence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamScanSubmissionService {

    private final ExamStudentRepository examStudentRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final FullscreenViolationRepository fullscreenViolationRepository;
    private final FileStorageService fileStorageService;
    private final ExamScanGradingService scanGradingService;
    private final ExamGradingApplierService gradingApplierService;

    @Transactional
    public ExamSubmissionDetailResponse uploadAndGradeScan(Long examId, Long studentId, List<MultipartFile> files,
                                                            String professorEmail) throws IOException {
        ExamStudent student = getStudent(examId, studentId, professorEmail);
        Exam exam = student.getExam();

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Veuillez fournir au moins une photo ou un fichier scanné de la copie.");
        }
        if (files.size() > ExamScanGradingService.MAX_FILES) {
            throw new IllegalArgumentException("Trop de fichiers : maximum " + ExamScanGradingService.MAX_FILES + " pages par copie.");
        }

        List<String> urls = new ArrayList<>();
        List<ExamScanGradingService.ScannedFile> scannedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(fileStorageService.store(file, "exam-scans/" + examId + "/" + studentId));
            scannedFiles.add(new ExamScanGradingService.ScannedFile(file.getBytes(), file.getContentType(), file.getOriginalFilename()));
        }

        ExamSubmission submission = student.getSubmission();
        if (submission == null) {
            submission = ExamSubmission.builder()
                .examStudent(student)
                .submittedAt(LocalDateTime.now())
                .isGraded(false)
                .submissionType(SubmissionType.SCANNED_COPY)
                .build();

            List<ExamAnswer> answers = new ArrayList<>();
            for (ExamQuestion question : exam.getQuestions()) {
                answers.add(ExamAnswer.builder().submission(submission).question(question).build());
            }
            submission.setAnswers(answers);
            student.setSubmission(submission);
        } else {
            submission.setSubmissionType(SubmissionType.SCANNED_COPY);
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setIsGraded(false);
        }
        submission.getScannedFileUrls().addAll(urls);

        if (student.getStatus() == StudentExamStatus.INVITED || student.getStatus() == StudentExamStatus.STARTED) {
            student.setStatus(StudentExamStatus.SUBMITTED);
        }

        submission = submissionRepository.save(submission);
        examStudentRepository.save(student);

        log.info("Copie scannée importée pour l'étudiant {} (examen {}): {} fichier(s)",
            student.getStudentEmail(), examId, files.size());

        ExamGradingService.GradingResult result = scanGradingService.gradeScannedCopy(exam, scannedFiles);
        gradingApplierService.applyGradingResult(submission, result);

        return toDetailResponse(submission);
    }

    @Transactional(readOnly = true)
    public ExamSubmissionDetailResponse getSubmissionDetail(Long examId, Long studentId, String professorEmail) {
        ExamStudent student = getStudent(examId, studentId, professorEmail);
        ExamSubmission submission = student.getSubmission();
        if (submission == null) {
            throw new IllegalStateException("Aucune soumission trouvée pour cet étudiant.");
        }
        return toDetailResponse(submission);
    }

    private ExamStudent getStudent(Long examId, Long studentId, String professorEmail) {
        ExamStudent student = examStudentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Étudiant introuvable"));
        if (!student.getExam().getId().equals(examId)) {
            throw new IllegalArgumentException("Cet étudiant n'appartient pas à cet examen.");
        }
        if (professorEmail != null && !student.getExam().getProfessor().getEmail().equals(professorEmail)) {
            throw new AccessDeniedException("Accès refusé");
        }
        return student;
    }

    private ExamSubmissionDetailResponse toDetailResponse(ExamSubmission submission) {
        ExamStudent student = submission.getExamStudent();

        List<ExamAnswerDetailResponse> answers = submission.getAnswers().stream()
            .sorted(Comparator.comparingInt(a -> a.getQuestion().getOrderIndex()))
            .map(a -> ExamAnswerDetailResponse.builder()
                .questionId(a.getQuestion().getId())
                .orderIndex(a.getQuestion().getOrderIndex())
                .questionText(a.getQuestion().getQuestionText())
                .referenceAnswer(a.getQuestion().getReferenceAnswer())
                .maxScore(a.getQuestion().getMaxScore())
                .studentAnswer(a.getStudentAnswer())
                .obtainedScore(a.getObtainedScore())
                .aiComment(a.getAiComment())
                .build())
            .toList();

        List<FullscreenViolationResponse> violations = fullscreenViolationRepository
            .findBySubmissionIdOrderByViolationTimeAsc(submission.getId()).stream()
            .map(v -> FullscreenViolationResponse.builder()
                .id(v.getId())
                .violationNumber(v.getViolationNumber())
                .violationTime(v.getViolationTime())
                .recoveredAt(v.getRecoveredAt())
                .details(v.getDetails())
                .build())
            .toList();

        return ExamSubmissionDetailResponse.builder()
            .studentId(student.getId())
            .studentName(student.getStudentName())
            .studentEmail(student.getStudentEmail())
            .status(student.getStatus().name())
            .submissionType(submission.getSubmissionType().name())
            .scannedFileUrls(submission.getScannedFileUrls())
            .totalScore(submission.getTotalScore())
            .maxScore(submission.getMaxScore())
            .aiReport(submission.getAiReport())
            .answers(answers)
            .fullscreenViolations(violations)
            .build();
    }
}
