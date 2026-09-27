package com.elearning.controller;

import com.elearning.dto.request.QcmFullscreenViolationRequest;
import com.elearning.entity.*;
import com.elearning.exception.ResultLockedException;
import com.elearning.repository.*;
import com.elearning.service.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.elearning.service.FileStorageService;
import com.elearning.service.QcmCaseOcrGradingService;
import com.elearning.service.QcmSubmissionService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/qcm")
@RequiredArgsConstructor
public class QcmEtudiantController {

    private final QcmRepository qcmRepo;
    private final QcmPassageRepository passageRepo;
    private final UserRepository userRepo;
    private final QcmFullscreenViolationRepository fullscreenViolationRepo;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final QcmCaseOcrGradingService caseOcrGradingService;
    private final QcmSubmissionService submissionService;

    private static final int DEFAULT_ESTIMATED_DURATION_MINUTES = 30;

    private LocalDateTime computeUnlockAt(QcmPassage passage) {
        int duration = passage.getQcm().getEstimatedDurationMinutes() != null
            ? passage.getQcm().getEstimatedDurationMinutes() : DEFAULT_ESTIMATED_DURATION_MINUTES;
        LocalDateTime reference = passage.getStartedAt() != null ? passage.getStartedAt()
            : (passage.getExcludedAt() != null ? passage.getExcludedAt() : LocalDateTime.now());
        return reference.plusMinutes(duration);
    }

    private boolean isStillLocked(QcmPassage passage) {
        return Boolean.TRUE.equals(passage.getExcludedForViolations())
            && computeUnlockAt(passage).isAfter(LocalDateTime.now());
    }

    // ── DTOs ───────────────────────────────────────────────────────────────

    @Data static class ChoiceDto    { Long id; String choiceText; Integer orderIndex; }
    @Data static class QuestionDto  { Long id; String questionText; Integer points; Integer orderIndex; String questionType; String caseScenario; List<String> valueLabels; List<ChoiceDto> choices; }
    @Data static class QcmListDto   { Long id; String title; String description; String professorName; int questionCount; boolean alreadyTaken; String createdAt; Integer score; Integer maxScore; }
    @Data static class QcmTakeDto   { Long id; String title; String description; String subjectFileUrl; String subjectText; Long passageId; Integer estimatedDurationMinutes; Boolean paperCorrectionRequired; String paperCorrectionUrl; String paperCorrectionFilename; String startedAt; String draftAnswers; List<QuestionDto> questions; }

    @Data static class AccesDto     { Long id; String title; String description; Integer estimatedDurationMinutes; int questionCount; String studentName; String studentLevel;
                                      String lastName; String firstName; String birthDate; String level; }
    @Data static class CommencerInput { String lastName; String firstName; String birthDate; String level; }


    @Data static class ResultatDto {
        Long passageId; String qcmTitle; Integer score; Integer maxScore;
        String mention; String percentage;
        List<ReponseResultDto> detail;
    }
    @Data static class ReponseResultDto {
        String questionText; Integer points;
        String choiceSelected; Boolean isCorrect; String correctChoice;
    }

    // ── Liste des QCMs publiés ─────────────────────────────────────────────

    @GetMapping
    @Transactional
    public ResponseEntity<List<QcmListDto>> list(Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        String studentEmail = student.getEmail().toLowerCase();
        List<QcmListDto> list = qcmRepo.findByStatusOrderByCreatedAtDesc("PUBLISHED")
            .stream()
            .filter(qcm -> {
                // Si aucun étudiant assigné → visible par tous
                if (qcm.getAssignedStudents().isEmpty()) return true;
                return qcm.getAssignedStudents().stream()
                    .anyMatch(s -> s.getStudentEmail().equalsIgnoreCase(studentEmail));
            })
            .map(qcm -> {
                QcmListDto d = new QcmListDto();
                d.id = qcm.getId(); d.title = qcm.getTitle();
                d.description = qcm.getDescription();
                d.professorName = qcm.getProfessor().getFirstName() + " " + qcm.getProfessor().getLastName();
                d.questionCount = qcm.getQuestions().size();
                d.createdAt = qcm.getCreatedAt() != null ? qcm.getCreatedAt().toString() : null;
                passageRepo.findByQcmAndStudent(qcm, student).ifPresent(p -> {
                    submissionService.autoSubmitIfExpired(p);
                    d.alreadyTaken = Boolean.TRUE.equals(p.getIsSubmitted());
                    if (d.alreadyTaken && !isStillLocked(p)) { d.score = p.getScore(); d.maxScore = p.getMaxScore(); }
                });
                return d;
            }).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    private java.util.Optional<QcmStudent> findAssignment(Qcm qcm, User student) {
        return qcm.getAssignedStudents().stream()
            .filter(s -> s.getStudentEmail().equalsIgnoreCase(student.getEmail()))
            .findFirst();
    }

    // ── Informations avant de commencer (écran d'accueil) ─────────────────

    @GetMapping("/{id}/acces")
    @Transactional(readOnly = true)
    public ResponseEntity<?> acces(@PathVariable Long id, Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        if (!"PUBLISHED".equals(qcm.getStatus()))
            return ResponseEntity.badRequest().body(Map.of("message", "Ce devoir n'est pas disponible."));
        java.util.Optional<QcmStudent> assignment = findAssignment(qcm, student);
        if (!qcm.getAssignedStudents().isEmpty() && assignment.isEmpty())
            return ResponseEntity.status(403).body(Map.of("message", "Vous ne figurez pas sur la liste des étudiants de ce devoir."));

        AccesDto dto = new AccesDto();
        dto.id = qcm.getId(); dto.title = qcm.getTitle(); dto.description = qcm.getDescription();
        dto.estimatedDurationMinutes = qcm.getEstimatedDurationMinutes() != null ? qcm.getEstimatedDurationMinutes() : DEFAULT_ESTIMATED_DURATION_MINUTES;
        dto.questionCount = qcm.getQuestions().size();
        dto.studentName = assignment.map(QcmStudent::getStudentName).orElse(student.getFirstName() + " " + student.getLastName());
        dto.studentLevel = assignment.map(QcmStudent::getLevel).orElse(null);
        // Pré-remplissage : identité déjà saisie, sinon liste du professeur, sinon compte
        QcmPassage existing = passageRepo.findByQcmAndStudent(qcm, student).orElse(null);
        dto.lastName = firstNonBlank(existing != null ? existing.getDeclaredLastName() : null,
            assignment.map(QcmStudent::getLastName).orElse(null), student.getLastName());
        dto.firstName = firstNonBlank(existing != null ? existing.getDeclaredFirstName() : null,
            assignment.map(QcmStudent::getFirstName).orElse(null), student.getFirstName());
        dto.level = firstNonBlank(existing != null ? existing.getDeclaredLevel() : null,
            assignment.map(QcmStudent::getLevel).orElse(null));
        dto.birthDate = existing != null && existing.getBirthDate() != null ? existing.getBirthDate().toString() : null;
        return ResponseEntity.ok(dto);
    }

    // ── Commencer ou reprendre un QCM ─────────────────────────────────────

    @PostMapping("/{id}/commencer")
    @Transactional
    public ResponseEntity<?> commencer(@PathVariable Long id,
                                       @RequestBody(required = false) CommencerInput input,
                                       Authentication auth) {
        String identityError = validateIdentity(input);
        if (identityError != null) return ResponseEntity.badRequest().body(Map.of("message", identityError));

        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        if (!"PUBLISHED".equals(qcm.getStatus()))
            return ResponseEntity.badRequest().build();

        java.util.Optional<QcmStudent> assignment = findAssignment(qcm, student);
        if (!qcm.getAssignedStudents().isEmpty() && assignment.isEmpty())
            return ResponseEntity.status(403).body(Map.of("message", "Vous ne figurez pas sur la liste des étudiants de ce devoir."));

        QcmPassage passage = passageRepo.findByQcmAndStudent(qcm, student)
            .orElseGet(() -> QcmPassage.builder().qcm(qcm).student(student).build());

        if (passage.getId() == null) {
            passage = passageRepo.save(passage);
        }

        // Temps écoulé sans soumission : la copie est soumise avec les réponses enregistrées
        submissionService.autoSubmitIfExpired(passage);
        if (Boolean.TRUE.equals(passage.getIsSubmitted()))
            return ResponseEntity.badRequest().body(Map.of("message", "Ce devoir a déjà été soumis."));

        passage.setDeclaredLastName(input.lastName.trim());
        passage.setDeclaredFirstName(input.firstName.trim());
        passage.setBirthDate(java.time.LocalDate.parse(input.birthDate.trim()));
        passage.setDeclaredLevel(input.level.trim());
        passageRepo.save(passage);

        if (passage.getStartedAt() == null) {
            passage.setStartedAt(LocalDateTime.now());
            passageRepo.save(passage);
        }

        QcmTakeDto dto = new QcmTakeDto();
        dto.id = qcm.getId(); dto.title = qcm.getTitle();
        dto.description = qcm.getDescription();
        dto.subjectFileUrl = qcm.getSubjectFileUrl();
        dto.subjectText = qcm.getSubjectText();
        dto.passageId = passage.getId();
        dto.estimatedDurationMinutes = qcm.getEstimatedDurationMinutes() != null ? qcm.getEstimatedDurationMinutes() : DEFAULT_ESTIMATED_DURATION_MINUTES;
        dto.paperCorrectionRequired = Boolean.TRUE.equals(qcm.getPaperCorrectionRequired());
        dto.paperCorrectionUrl = passage.getPaperCorrectionUrl();
        dto.paperCorrectionFilename = passage.getPaperCorrectionFilename();
        dto.startedAt = passage.getStartedAt() != null ? passage.getStartedAt().toString() : null;
        dto.draftAnswers = passage.getDraftAnswers();
        if (qcm.getQuestions().stream().noneMatch(question -> "CASE".equalsIgnoreCase(question.getQuestionType()))) {
            String subjectText = qcm.getSubjectText() == null ? "" : qcm.getSubjectText().trim();
            if (!subjectText.isBlank() && qcm.getQuestions().stream().noneMatch(question -> question.getQuestionText()!=null && question.getQuestionText().contains(subjectText.substring(0, Math.min(80, subjectText.length()))))) {
                qcm.getQuestions().add(QcmQuestion.builder()
                    .qcm(qcm)
                    .questionText(subjectText)
                    .questionType("CASE")
                    .points(10)
                    .orderIndex(qcm.getQuestions().size())
                    .caseScenario(subjectText)
                    .correctionData(qcm.getCorrectionText())
                    .expectedAnswer(qcm.getCorrectionText() == null || qcm.getCorrectionText().isBlank() ? "Référence de correction fournie par le professeur." : qcm.getCorrectionText())
                    .choices(new ArrayList<>())
                    .build());
                // Sans identifiant, la réponse saisie par l'étudiant ne pourrait pas être rattachée à la question
                qcmRepo.saveAndFlush(qcm);
            }
        }
        dto.questions = qcm.getQuestions().stream().map(q -> {
            QuestionDto qd = new QuestionDto();
            qd.id = q.getId(); qd.questionText = q.getQuestionText();
            qd.points = q.getPoints(); qd.orderIndex = q.getOrderIndex();
            qd.questionType = q.getQuestionType();
            qd.caseScenario = q.getCaseScenario();
            qd.valueLabels = practicalLabels(q.getCorrectionData());
            // On N'envoie PAS isCorrect à l'étudiant
            qd.choices = q.getChoices().stream().map(c -> {
                ChoiceDto cd = new ChoiceDto();
                cd.id = c.getId(); cd.choiceText = c.getChoiceText();
                cd.orderIndex = c.getOrderIndex();
                return cd;
            }).collect(Collectors.toList());
            return qd;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dto);
    }

    @PostMapping(value = "/{id}/passage/{passageId}/paper-correction", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<?> uploadPaperCorrection(
            @PathVariable Long id,
            @PathVariable Long passageId,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        QcmPassage passage = passageRepo.findById(passageId).orElseThrow();
        if (!passage.getQcm().getId().equals(qcm.getId()) || !passage.getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Accès interdit."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Sélectionnez une copie à envoyer."));
        }
        try {
            String url = fileStorageService.store(file, "devoirs/copies/" + student.getId());
            passage.setPaperCorrectionUrl(url);
            passage.setPaperCorrectionFilename(file.getOriginalFilename());
            passageRepo.save(passage);
            QcmCaseOcrGradingService.ScannedFile scanned =
                new QcmCaseOcrGradingService.ScannedFile(file.getBytes(), file.getContentType(), file.getOriginalFilename());
            if (!hasPaperCase(qcm)) {
                passage.setOcrExtractedText(caseOcrGradingService.transcribe(List.of(scanned)));
                passageRepo.save(passage);
            } else {
                QcmCaseOcrGradingService.GradeResult grade = caseOcrGradingService.grade(qcm, List.of(scanned));
                passage.setOcrExtractedText(grade.extractedText());
                passage.setOcrScore(grade.score());
                passage.setOcrCorrectionNote(grade.comment());
                passage.setScore(grade.score());
                passage.setMaxScore(grade.maxScore());
                passageRepo.save(passage);
            }
            return ResponseEntity.ok(Map.of("url", url, "filename", file.getOriginalFilename()));
        } catch (java.io.IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Impossible d'enregistrer la copie."));
        }
    }

    // ── Soumettre les réponses ─────────────────────────────────────────────

    @PostMapping("/{id}/soumettre")
    @Transactional
    public ResponseEntity<?> soumettre(
            @PathVariable Long id,
            @RequestBody QcmSubmissionService.Submission input,
            Authentication auth) {

        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        QcmPassage passage = passageRepo.findByQcmAndStudent(qcm, student).orElse(null);
        if (passage == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Commencez le devoir avant de le soumettre."));
        }
        if (Boolean.TRUE.equals(passage.getIsSubmitted())) {
            // Copie déjà soumise (par exemple automatiquement) : on renvoie le résultat enregistré
            return ResponseEntity.ok(buildResultat(passage, qcm));
        }
        // Les réponses sont d'abord conservées : rien n'est perdu si la soumission est refusée
        submissionService.saveDraft(passage, input);
        try {
            submissionService.submit(passage, input);
        } catch (QcmSubmissionService.SubmissionRefusedException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.ok(buildResultat(passage, qcm));
    }

    /** Enregistrement régulier des réponses pendant le devoir. */
    @PostMapping("/{id}/brouillon")
    @Transactional
    public ResponseEntity<Void> brouillon(@PathVariable Long id,
                                          @RequestBody QcmSubmissionService.Submission input,
                                          Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        passageRepo.findByQcmAndStudent(qcm, student).ifPresent(p -> submissionService.saveDraft(p, input));
        return ResponseEntity.noContent().build();
    }

    /** Nom, prénom, date de naissance et niveau sont obligatoires avant de commencer le devoir. */
    private static String validateIdentity(CommencerInput input) {
        if (input == null || firstNonBlank(input.lastName) == null || firstNonBlank(input.firstName) == null
                || firstNonBlank(input.birthDate) == null || firstNonBlank(input.level) == null) {
            return "Renseignez votre nom, prénom, date de naissance et niveau avant de commencer.";
        }
        try {
            java.time.LocalDate birth = java.time.LocalDate.parse(input.birthDate.trim());
            java.time.LocalDate today = java.time.LocalDate.now();
            if (birth.isAfter(today.minusYears(10)) || birth.isBefore(today.minusYears(100))) {
                return "Date de naissance invalide.";
            }
        } catch (java.time.format.DateTimeParseException e) {
            return "Date de naissance invalide.";
        }
        return null;
    }


    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }



    private boolean isPaperCase(QcmQuestion question) {
        return submissionService.isPaperCase(question);
    }


    private boolean hasPaperCase(Qcm qcm) {
        return submissionService.hasPaperCase(qcm);
    }


    private List<String> practicalLabels(String correctionData) {
        if (correctionData == null || correctionData.isBlank()) return List.of();
        try {
            Map<String, Object> values = objectMapper.readValue(correctionData, new TypeReference<>() {});
            return values.keySet().stream().sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }




    // ── Voir mon résultat ──────────────────────────────────────────────────

    @GetMapping("/{id}/mon-resultat")
    @Transactional
    public ResponseEntity<ResultatDto> monResultat(@PathVariable Long id, Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        QcmPassage passage = passageRepo.findByQcmAndStudent(qcm, student)
            .map(p -> { submissionService.autoSubmitIfExpired(p); return p; })
            .filter(p -> Boolean.TRUE.equals(p.getIsSubmitted()))
            .orElseThrow(() -> new RuntimeException("Résultat non disponible"));
        if (isStillLocked(passage)) {
            throw new ResultLockedException(computeUnlockAt(passage));
        }
        submissionService.refreshDocumentScore(passage, qcm);
        return ResponseEntity.ok(buildResultat(passage, qcm));
    }

    // ── Mes QCMs passés ───────────────────────────────────────────────────

    @GetMapping("/mes-resultats")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ResultatDto>> mesResultats(Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        List<ResultatDto> list = passageRepo.findByStudentOrderByStartedAtDesc(student)
            .stream().filter(p -> Boolean.TRUE.equals(p.getIsSubmitted()) && !isStillLocked(p))
            .map(p -> buildResultat(p, p.getQcm()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private ResultatDto buildResultat(QcmPassage passage, Qcm qcm) {
        ResultatDto r = new ResultatDto();
        r.passageId = passage.getId();
        r.qcmTitle = qcm.getTitle();
        r.score = passage.getScore();
        r.maxScore = passage.getMaxScore();
        double pct = r.maxScore > 0 ? (r.score * 100.0) / r.maxScore : 0;
        r.percentage = String.format("%.0f%%", pct);
        r.mention = pct >= 80 ? "Excellent" : pct >= 60 ? "Bien" : pct >= 50 ? "Passable" : "Insuffisant";
        r.detail = passage.getReponses().stream().map(rep -> {
            ReponseResultDto rd = new ReponseResultDto();
            rd.questionText = rep.getQuestion().getQuestionText();
            rd.points = rep.getQuestion().getPoints();
            rd.choiceSelected = resolveChoiceLabel(rep, passage);
            rd.isCorrect = rep.getIsCorrect();
            rd.correctChoice = rep.getQuestion().getChoices().stream()
                .filter(QcmChoice::getIsCorrect).findFirst()
                .map(QcmChoice::getChoiceText).orElse("—");
            return rd;
        }).collect(Collectors.toList());
        return r;
    }

    private String resolveChoiceLabel(QcmReponse rep, QcmPassage passage) {
        if (rep.getChoiceSelected() != null) {
            return rep.getChoiceSelected().getChoiceText();
        }
        if (rep.getTextAnswer() != null && !rep.getTextAnswer().isBlank()) {
            return passage != null && passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank()
                ? "Réponse saisie + copie scannée" : "Réponse saisie";
        }
        if (isPaperCase(rep.getQuestion())) {
            if (passage != null && passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank()) {
                return "Copie scannée / OCR";
            }
            return "Copie scannée attendue";
        }
        if ("PRACTICAL".equalsIgnoreCase(rep.getQuestion().getQuestionType())) {
            return "Réponse pratique saisie";
        }
        return "Sans réponse";
    }

    // ── Enregistrer violation fullscreen ───────────────────────────────────

    @PostMapping("/{id}/passage/{passageId}/fullscreen-violation")
    @Transactional
    public ResponseEntity<Void> recordFullscreenViolation(
            @PathVariable Long id,
            @PathVariable Long passageId,
            @RequestBody QcmFullscreenViolationRequest request) {

        QcmPassage passage = passageRepo.findById(passageId)
            .orElseThrow(() -> new RuntimeException("Passage introuvable"));

        QcmFullscreenViolation violation = QcmFullscreenViolation.builder()
            .passage(passage)
            .violationNumber(request.getViolationNumber())
            .details(request.getDetails())
            .build();

        fullscreenViolationRepo.save(violation);

        // La soumission automatique (à la 2ème violation) est déclenchée par le frontend
        // via l'appel réel à /soumettre, afin que les réponses et le score soient enregistrés.
        // Ici on ne fait que marquer l'exclusion et bloquer l'étudiant.
        if (request.getViolationNumber() >= 2 && Boolean.TRUE.equals(request.getShouldTerminate())) {
            passage.setExcludedForViolations(true);
            passage.setExcludedAt(LocalDateTime.now());
            passageRepo.save(passage);

            LocalDateTime unlockAt = computeUnlockAt(passage);
            User student = passage.getStudent();
            student.setBlockedUntil(unlockAt);
            userRepo.save(student);

            Qcm qcm = passage.getQcm();
            emailService.sendFullscreenExclusionAlert(
                qcm.getProfessor().getEmail(),
                qcm.getProfessor().getFirstName() + " " + qcm.getProfessor().getLastName(),
                student.getFirstName() + " " + student.getLastName(), student.getEmail(),
                qcm.getTitle(), request.getViolationNumber(), "QCM"
            );
        }

        return ResponseEntity.ok().build();
    }
}
