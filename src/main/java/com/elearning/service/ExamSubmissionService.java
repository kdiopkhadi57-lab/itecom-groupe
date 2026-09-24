package com.elearning.service;

import com.elearning.dto.request.ExamSubmitRequest;
import com.elearning.dto.request.FullscreenViolationRequest;
import com.elearning.dto.response.ExamTakeResponse;
import com.elearning.dto.response.ExamQuestionResponse;
import com.elearning.dto.response.StudentExamResponse;
import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSubmissionService {

    private final ExamStudentRepository examStudentRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final ExamAnswerRepository answerRepository;
    private final FullscreenViolationRepository fullscreenViolationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.exam.grading-delay-minutes:60}")
    private long gradingDelayMinutes;

    // Temps estimé par défaut si l'examen n'en précise pas (ne devrait pas arriver, valeur par défaut posée à la création)
    private static final int DEFAULT_ESTIMATED_DURATION_MINUTES = 60;

    private LocalDateTime computeUnlockAt(ExamStudent student) {
        int duration = student.getExam().getEstimatedDurationMinutes() != null
            ? student.getExam().getEstimatedDurationMinutes() : DEFAULT_ESTIMATED_DURATION_MINUTES;
        LocalDateTime reference = student.getStartedAt() != null ? student.getStartedAt()
            : (student.getExcludedAt() != null ? student.getExcludedAt() : LocalDateTime.now());
        return reference.plusMinutes(duration);
    }

    private boolean isStillLocked(ExamStudent student) {
        return Boolean.TRUE.equals(student.getExcludedForViolations())
            && computeUnlockAt(student).isAfter(LocalDateTime.now());
    }

    public List<StudentExamResponse> getMyExams(String studentEmail) {
        return examStudentRepository.findByStudentEmailOrderByExam_CreatedAtDesc(studentEmail)
            .stream()
            .filter(student -> student.getExam().getStatus() != ExamStatus.DRAFT)
            .map(student -> {
                Exam exam = student.getExam();
                ExamSubmission sub = student.getSubmission();
                boolean locked = isStillLocked(student);
                String studentStatus = locked && student.getStatus() == StudentExamStatus.GRADED
                    ? StudentExamStatus.SUBMITTED.name() : student.getStatus().name();
                return StudentExamResponse.builder()
                    .examId(exam.getId())
                    .examTitle(exam.getTitle())
                    .examDescription(exam.getDescription())
                    .professorName(exam.getProfessor().getFirstName() + " " + exam.getProfessor().getLastName())
                    .examStatus(exam.getStatus().name())
                    .studentStatus(studentStatus)
                    .accessToken(student.getAccessToken())
                    .totalScore(locked || sub == null ? null : sub.getTotalScore())
                    .maxScore(locked || sub == null ? null : sub.getMaxScore())
                    .percentage(!locked && sub != null && sub.getMaxScore() != null && sub.getMaxScore() > 0
                        ? Math.round(sub.getTotalScore() / sub.getMaxScore() * 10000.0) / 100.0 : null)
                    .createdAt(exam.getCreatedAt())
                    .build();
            })
            .toList();
    }

    public ExamTakeResponse getExamByToken(String token) {
        ExamStudent student = examStudentRepository.findByAccessToken(token)
            .orElseThrow(() -> new RuntimeException("Token d'accès invalide"));

        Exam exam = student.getExam();
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("Cet examen n'est pas disponible");
        }

        List<ExamQuestionResponse> questions = exam.getQuestions().stream()
            .map(q -> ExamQuestionResponse.builder()
                .id(q.getId())
                .questionText(q.getQuestionText())
                .maxScore(q.getMaxScore())
                .orderIndex(q.getOrderIndex())
                .questionType(q.getQuestionType())
                .correctionData(q.getCorrectionData())
                .choices(q.getChoices().stream().map(c -> com.elearning.dto.response.ExamChoiceResponse.builder()
                    .id(c.getId())
                    .choiceText(c.getChoiceText())
                    .isCorrect(c.getIsCorrect())
                    .orderIndex(c.getOrderIndex())
                    .build()).toList())
                .build())
            .toList();

        boolean locked = isStillLocked(student);

        return ExamTakeResponse.builder()
            .examId(exam.getId())
            .examTitle(exam.getTitle())
            .examDescription(exam.getDescription())
            .studentName(student.getStudentName())
            .studentEmail(student.getStudentEmail())
            .status(student.getStatus().name())
            .questions(questions)
            .gradingDelayMinutes(gradingDelayMinutes)
            .resultsLocked(locked)
            .resultsAvailableAt(locked ? computeUnlockAt(student) : null)
            .build();
    }

    @Transactional
    public ExamTakeResponse startExam(String token) {
        ExamStudent student = examStudentRepository.findByAccessToken(token)
            .orElseThrow(() -> new RuntimeException("Token d'accès invalide"));

        if (student.getStatus() == StudentExamStatus.SUBMITTED || student.getStatus() == StudentExamStatus.GRADED) {
            throw new IllegalStateException("Vous avez déjà soumis cet examen");
        }

        student.setStatus(StudentExamStatus.STARTED);
        if (student.getStartedAt() == null) {
            student.setStartedAt(LocalDateTime.now());
        }

        // Créer une submission si elle n'existe pas encore
        if (student.getSubmission() == null) {
            ExamSubmission submission = ExamSubmission.builder()
                .examStudent(student)
                .submittedAt(LocalDateTime.now())
                .isGraded(false)
                .build();
            submissionRepository.save(submission);
            student.setSubmission(submission);
        }

        examStudentRepository.save(student);

        return getExamByToken(token);
    }

    @Transactional
    public void submitExam(String token, ExamSubmitRequest request) {
        ExamStudent student = examStudentRepository.findByAccessToken(token)
            .orElseThrow(() -> new RuntimeException("Token d'accès invalide"));

        if (student.getStatus() == StudentExamStatus.SUBMITTED || student.getStatus() == StudentExamStatus.GRADED) {
            throw new IllegalStateException("Vous avez déjà soumis cet examen");
        }

        if (student.getStatus() == StudentExamStatus.INVITED) {
            student.setStatus(StudentExamStatus.STARTED);
        }

        Exam exam = student.getExam();
        Map<Long, ExamQuestion> questionsById = exam.getQuestions().stream()
            .collect(Collectors.toMap(ExamQuestion::getId, q -> q));

        ExamSubmission submission = ExamSubmission.builder()
            .examStudent(student)
            .submittedAt(LocalDateTime.now())
            .isGraded(false)
            .build();

        List<ExamAnswer> answers = new ArrayList<>();
        for (var answerReq : request.getAnswers()) {
            ExamQuestion question = questionsById.get(answerReq.getQuestionId());
            if (question != null) {
                answers.add(ExamAnswer.builder()
                    .submission(submission)
                    .question(question)
                    .studentAnswer(answerReq.getAnswer())
                    .build());
            }
        }
        submission.setAnswers(answers);

        submissionRepository.save(submission);

        student.setStatus(StudentExamStatus.SUBMITTED);
        examStudentRepository.save(student);

        log.info("Examen soumis par {} à {}", student.getStudentEmail(), submission.getSubmittedAt());
    }

    @Transactional
    public void recordFullscreenViolation(String token, FullscreenViolationRequest request) {
        ExamStudent student = examStudentRepository.findByAccessToken(token)
            .orElseThrow(() -> new RuntimeException("Token d'accès invalide"));

        ExamSubmission submission = student.getSubmission();
        if (submission == null) {
            log.warn("Tentative d'enregistrement de violation sans submission pour le token: {}", token);
            return;
        }

        FullscreenViolation violation = FullscreenViolation.builder()
            .submission(submission)
            .violationNumber(request.getViolationNumber())
            .details(request.getDetails())
            .build();

        fullscreenViolationRepository.save(violation);
        log.info("Violation fullscreen enregistrée: {} pour l'étudiant {}",
            request.getViolationNumber(), student.getStudentEmail());

        // À la 3ème violation, l'étudiant est exclu. La soumission réelle des réponses
        // (avec le statut SUBMITTED) est déclenchée par le frontend via /submit, afin
        // que les réponses en cours soient bien enregistrées.
        if (request.getViolationNumber() >= 3 && Boolean.TRUE.equals(request.getShouldTerminate())) {
            student.setExcludedForViolations(true);
            student.setExcludedAt(LocalDateTime.now());
            examStudentRepository.save(student);

            LocalDateTime unlockAt = computeUnlockAt(student);
            userRepository.findByEmail(student.getStudentEmail()).ifPresent(user -> {
                user.setBlockedUntil(unlockAt);
                userRepository.save(user);
            });

            Exam exam = student.getExam();
            emailService.sendFullscreenExclusionAlert(
                exam.getProfessor().getEmail(),
                exam.getProfessor().getFirstName() + " " + exam.getProfessor().getLastName(),
                student.getStudentName(), student.getStudentEmail(),
                exam.getTitle(), request.getViolationNumber(), "EXAM"
            );

            log.info("Étudiant exclu suite à violation fullscreen pour {} (examen: {})", student.getStudentEmail(), exam.getTitle());
        }
    }
}
