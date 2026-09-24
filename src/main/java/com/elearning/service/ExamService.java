package com.elearning.service;

import com.elearning.dto.request.ExamCreateRequest;
import com.elearning.dto.response.ExamResponse;
import com.elearning.dto.response.ExamQuestionResponse;
import com.elearning.dto.response.ExamStudentResponse;
import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamStudentRepository examStudentRepository;
    private final StudentListParserService parserService;
    private final DocumentTextExtractorService documentTextExtractorService;
    private final ExamQuestionExtractionService questionExtractionService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Transactional
    public ExamResponse createExam(ExamCreateRequest request, MultipartFile studentListFile,
                                    MultipartFile examFile, MultipartFile correctionFile,
                                    String professorEmail) throws IOException {
        User professor = userRepository.findByEmail(professorEmail)
            .orElseThrow(() -> new RuntimeException("Professeur introuvable"));

        Exam exam = Exam.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .estimatedDurationMinutes(request.getEstimatedDurationMinutes() != null ? request.getEstimatedDurationMinutes() : 60)
            .professor(professor)
            .status(ExamStatus.DRAFT)
            .build();

        List<ExamQuestion> questions = new ArrayList<>();
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            for (int i = 0; i < request.getQuestions().size(); i++) {
                var qReq = request.getQuestions().get(i);
                ExamQuestion examQuestion = ExamQuestion.builder()
                    .exam(exam)
                    .questionText(qReq.getQuestionText())
                    .referenceAnswer(qReq.getReferenceAnswer() != null ? qReq.getReferenceAnswer() : "")
                    .maxScore(qReq.getMaxScore())
                    .questionType(qReq.getQuestionType() != null ? qReq.getQuestionType() : "QCM")
                    .correctionData(qReq.getCorrectionData())
                    .orderIndex(i + 1)
                    .build();

                if (qReq.getChoices() != null && !qReq.getChoices().isEmpty()) {
                    for (int j = 0; j < qReq.getChoices().size(); j++) {
                        var choiceReq = qReq.getChoices().get(j);
                        examQuestion.getChoices().add(ExamChoice.builder()
                            .question(examQuestion)
                            .choiceText(choiceReq.getChoiceText())
                            .isCorrect(Boolean.TRUE.equals(choiceReq.getIsCorrect()))
                            .orderIndex(j + 1)
                            .build());
                    }
                }

                questions.add(examQuestion);
            }
        } else {
            if (examFile == null || examFile.isEmpty()) {
                throw new IllegalArgumentException(
                    "Veuillez soit saisir les questions manuellement, soit fournir un document d'examen.");
            }
            String examText = documentTextExtractorService.extractText(examFile);
            String correctionText = (correctionFile != null && !correctionFile.isEmpty())
                ? documentTextExtractorService.extractText(correctionFile)
                : examText;
            List<ExamQuestionExtractionService.ExtractedQuestion> extracted =
                questionExtractionService.extractQuestions(examText, correctionText);

            for (int i = 0; i < extracted.size(); i++) {
                var eq = extracted.get(i);
                questions.add(ExamQuestion.builder()
                    .exam(exam)
                    .questionText(eq.questionText())
                    .referenceAnswer(eq.referenceAnswer())
                    .maxScore(eq.maxScore() != null ? eq.maxScore() : 10)
                    .orderIndex(i + 1)
                    .build());
            }
        }
        exam.setQuestions(questions);

        List<StudentListParserService.StudentInfo> studentInfos = parserService.parseFile(studentListFile);
        List<ExamStudent> students = new ArrayList<>();
        for (var info : studentInfos) {
            students.add(ExamStudent.builder()
                .exam(exam)
                .studentName(info.name())
                .studentEmail(info.email())
                .accessToken(UUID.randomUUID().toString())
                .status(StudentExamStatus.INVITED)
                .build());
        }
        exam.setStudents(students);

        Exam saved = examRepository.save(exam);
        return toExamResponse(saved);
    }

    @Transactional
    public ExamResponse publishExam(Long examId, String professorEmail) {
        Exam exam = getExamForProfessor(examId, professorEmail);
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalStateException("L'examen n'est pas en état DRAFT");
        }
        exam.setStatus(ExamStatus.PUBLISHED);
        exam.getStudents().forEach(s -> {
            s.setInvitedAt(LocalDateTime.now());
            s.setStatus(StudentExamStatus.INVITED);
        });
        Exam saved = examRepository.save(exam);

        saved.getStudents().forEach(student ->
            emailService.sendExamInvitation(
                student.getStudentEmail(),
                student.getStudentName(),
                exam.getTitle(),
                student.getAccessToken()
            )
        );

        return toExamResponse(saved);
    }

    @Transactional
    public ExamResponse closeExam(Long examId, String professorEmail) {
        Exam exam = getExamForProfessor(examId, professorEmail);
        exam.setStatus(ExamStatus.CLOSED);
        return toExamResponse(examRepository.save(exam));
    }

    public List<ExamResponse> getExamsByProfessor(String professorEmail) {
        User professor = userRepository.findByEmail(professorEmail)
            .orElseThrow(() -> new RuntimeException("Professeur introuvable"));
        return examRepository.findByProfessorOrderByCreatedAtDesc(professor)
            .stream().map(this::toExamResponse).toList();
    }

    public List<ExamResponse> getAllExams() {
        return examRepository.findAllByOrderByCreatedAtDesc()
            .stream().map(this::toExamResponse).toList();
    }

    public ExamResponse getExamById(Long examId, String userEmail) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("Examen introuvable"));
        return toExamResponse(exam);
    }

    @Transactional
    public void deleteExam(Long examId, String professorEmail) {
        Exam exam = getExamForProfessor(examId, professorEmail);
        examRepository.delete(exam);
    }

    @Transactional
    public ExamResponse addStudentsToExam(Long examId, org.springframework.web.multipart.MultipartFile file,
                                          String professorEmail) throws java.io.IOException {
        Exam exam = getExamForProfessor(examId, professorEmail);
        List<StudentListParserService.StudentInfo> parsed = parserService.parseFile(file);

        // Emails déjà présents pour éviter les doublons
        java.util.Set<String> existing = exam.getStudents().stream()
            .map(ExamStudent::getStudentEmail)
            .collect(java.util.stream.Collectors.toSet());

        boolean isPublished = exam.getStatus() == ExamStatus.PUBLISHED;
        List<ExamStudent> added = new ArrayList<>();

        for (StudentListParserService.StudentInfo info : parsed) {
            if (existing.contains(info.email())) continue;
            ExamStudent s = ExamStudent.builder()
                .exam(exam)
                .studentName(info.name())
                .studentEmail(info.email())
                .accessToken(UUID.randomUUID().toString())
                .status(StudentExamStatus.INVITED)
                .invitedAt(isPublished ? LocalDateTime.now() : null)
                .build();
            exam.getStudents().add(s);
            added.add(s);
        }

        Exam saved = examRepository.save(exam);

        // Si l'examen est déjà publié, on envoie les invitations aux nouveaux étudiants
        if (isPublished) {
            added.forEach(s -> emailService.sendExamInvitation(
                s.getStudentEmail(), s.getStudentName(), exam.getTitle(), s.getAccessToken()));
        }

        return toExamResponse(saved);
    }

    private Exam getExamForProfessor(Long examId, String professorEmail) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("Examen introuvable"));
        if (!exam.getProfessor().getEmail().equals(professorEmail)) {
            throw new AccessDeniedException("Accès refusé");
        }
        return exam;
    }

    public ExamResponse toExamResponse(Exam exam) {
        List<ExamStudentResponse> studentResponses = exam.getStudents().stream().map(s -> {
            ExamSubmission sub = s.getSubmission();
            return ExamStudentResponse.builder()
                .id(s.getId())
                .studentName(s.getStudentName())
                .studentEmail(s.getStudentEmail())
                .status(s.getStatus().name())
                .totalScore(sub != null ? sub.getTotalScore() : null)
                .maxScore(sub != null ? sub.getMaxScore() : null)
                .percentage(sub != null && sub.getMaxScore() != null && sub.getMaxScore() > 0
                    ? Math.round(sub.getTotalScore() / sub.getMaxScore() * 10000.0) / 100.0 : null)
                .aiReport(sub != null ? sub.getAiReport() : null)
                .submissionType(sub != null ? sub.getSubmissionType().name() : null)
                .build();
        }).toList();

        return ExamResponse.builder()
            .id(exam.getId())
            .title(exam.getTitle())
            .description(exam.getDescription())
            .professorName(exam.getProfessor().getFirstName() + " " + exam.getProfessor().getLastName())
            .estimatedDurationMinutes(exam.getEstimatedDurationMinutes())
            .status(exam.getStatus().name())
            .questionCount(exam.getQuestions().size())
            .studentCount(exam.getStudents().size())
            .createdAt(exam.getCreatedAt())
            .questions(exam.getQuestions().stream().map(q -> ExamQuestionResponse.builder()
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
                .build()).toList())
            .students(studentResponses)
            .build();
    }
}
