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
    private final StudentAudienceService audienceService;

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

        exam.setTargetLevels(StudentAudienceService.normalizeLevels(request.getTargetLevels()));
        exam.setStudents(new ArrayList<>());
        List<StudentListParserService.StudentInfo> listed = studentListFile == null || studentListFile.isEmpty()
            ? List.of() : parserService.parseFile(studentListFile);
        enroll(exam, listed, exam.getTargetLevels());
        if (exam.getStudents().isEmpty()) {
            throw new IllegalArgumentException("Choisissez au moins un niveau ou un étudiant.");
        }

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
        return addStudents(exam, parserService.parseFile(file), null);
    }

    /** Ajout d'étudiants (comptes existants) et/ou de niveaux à un examen existant. */
    @Transactional
    public ExamResponse addStudentsToExam(Long examId, List<String> emails, List<String> levels, String professorEmail) {
        Exam exam = getExamForProfessor(examId, professorEmail);
        List<StudentListParserService.StudentInfo> infos = emails == null ? List.of() : emails.stream()
            .filter(e -> e != null && !e.isBlank())
            .map(e -> new StudentListParserService.StudentInfo(e.trim(), e.trim().toLowerCase()))
            .toList();
        return addStudents(exam, infos, StudentAudienceService.normalizeLevels(levels));
    }

    private ExamResponse addStudents(Exam exam, List<StudentListParserService.StudentInfo> infos, String newLevels) {
        if (newLevels != null) {
            List<String> merged = new ArrayList<>(StudentAudienceService.levelList(exam.getTargetLevels()));
            merged.addAll(StudentAudienceService.levelList(newLevels));
            exam.setTargetLevels(StudentAudienceService.normalizeLevels(merged));
        }
        List<ExamStudent> added = enroll(exam, infos, newLevels);
        Exam saved = examRepository.save(exam);
        invite(exam, added);
        return toExamResponse(saved);
    }

    /**
     * Inscrit les étudiants de la liste puis ceux des niveaux, sans doublon. Chaque email doit
     * correspondre à un compte étudiant : l'étudiant se connecte avec le mot de passe de ce compte.
     */
    private List<ExamStudent> enroll(Exam exam, List<StudentListParserService.StudentInfo> listed, String levels) {
        audienceService.requireStudentAccounts(listed.stream().map(StudentListParserService.StudentInfo::email).toList());
        java.util.Set<String> existing = exam.getStudents().stream()
            .map(s -> s.getStudentEmail().toLowerCase())
            .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
        boolean isPublished = exam.getStatus() == ExamStatus.PUBLISHED;
        List<ExamStudent> added = new ArrayList<>();
        java.util.function.BiConsumer<String, String> add = (name, email) -> {
            if (!existing.add(email.toLowerCase())) return;
            ExamStudent s = ExamStudent.builder()
                .exam(exam)
                .studentName(name)
                .studentEmail(email.toLowerCase())
                .accessToken(UUID.randomUUID().toString())
                .status(StudentExamStatus.INVITED)
                .invitedAt(isPublished ? LocalDateTime.now() : null)
                .build();
            exam.getStudents().add(s);
            added.add(s);
        };
        for (StudentListParserService.StudentInfo info : listed) {
            User account = userRepository.findByEmail(info.email().trim().toLowerCase()).orElseThrow();
            add.accept(StudentAudienceService.fullName(account), account.getEmail());
        }
        for (User u : audienceService.studentsOfLevels(levels)) {
            add.accept(StudentAudienceService.fullName(u), u.getEmail());
        }
        return added;
    }

    /** Si l'examen est déjà publié, les nouveaux inscrits reçoivent leur invitation. */
    private void invite(Exam exam, List<ExamStudent> added) {
        if (exam.getStatus() != ExamStatus.PUBLISHED) return;
        added.forEach(s -> emailService.sendExamInvitation(
            s.getStudentEmail(), s.getStudentName(), exam.getTitle(), s.getAccessToken()));
    }

    /** Un compte étudiant vient d'être créé : il rejoint les examens non clos qui ciblent son niveau. */
    @Transactional
    public void enrollNewStudent(User student) {
        if (student.getLevel() == null) return;
        for (Exam exam : examRepository.findAll()) {
            if (exam.getStatus() == ExamStatus.CLOSED || !StudentAudienceService.inLevels(student, exam.getTargetLevels())) continue;
            List<ExamStudent> added = enroll(exam, List.of(new StudentListParserService.StudentInfo(
                StudentAudienceService.fullName(student), student.getEmail())), null);
            if (added.isEmpty()) continue;
            examRepository.save(exam);
            invite(exam, added);
        }
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
            .targetLevels(StudentAudienceService.levelList(exam.getTargetLevels()))
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
