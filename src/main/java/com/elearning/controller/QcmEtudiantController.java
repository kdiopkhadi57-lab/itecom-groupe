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
import com.elearning.service.QcmSubmissionService;
import com.elearning.service.CaseGradingService;
import com.elearning.service.PaperCopyService;
import java.util.concurrent.CompletableFuture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/qcm")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class QcmEtudiantController {

    private final QcmRepository qcmRepo;
    private final QcmPassageRepository passageRepo;
    private final UserRepository userRepo;
    private final QcmFullscreenViolationRepository fullscreenViolationRepo;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final QcmSubmissionService submissionService;
    private final CaseGradingService caseGradingService;
    private final PaperCopyService paperCopyService;
    private final com.elearning.service.QcmAudienceService audienceService;

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
    @Data static class QcmTakeDto   { Long id; String title; String description; String subjectFileUrl; String subjectText; Long passageId; Integer estimatedDurationMinutes; Boolean paperCorrectionRequired; String paperCorrectionUrl; String paperCorrectionFilename; List<PaperCopyService.Page> paperPages; String paperTranscription; String startedAt; String draftAnswers; List<QuestionDto> questions; }

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
        List<QcmListDto> list = qcmRepo.findByStatusOrderByCreatedAtDesc("PUBLISHED")
            .stream()
            // Sans liste ni niveau ciblé → visible par tous
            .filter(qcm -> audienceService.canAccess(qcm, student))
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
        return audienceService.findAssignment(qcm, student);
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
        if (!qcm.isOpenToAll() && assignment.isEmpty())
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
        if (!qcm.isOpenToAll() && assignment.isEmpty())
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
        try {
            passage.setBirthDate(java.time.LocalDate.parse(input.birthDate.trim()));
        } catch (java.time.format.DateTimeParseException e) {
            passage.setBirthDate(null);
        }
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
        dto.paperPages = paperCopyService.pages(passage);
        dto.paperTranscription = passage.getOcrExtractedText();
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

    /**
     * Ajout d'une ou plusieurs pages à la copie papier, puis lecture de toute la copie par l'OCR.
     * Traité hors du thread de la requête (voir {@link PaperCopyService#async}) : la connexion à la base
     * n'est pas retenue pendant l'appel à l'IA.
     */
    @PostMapping(value = "/{id}/passage/{passageId}/paper-correction", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<?>> uploadPaperCorrection(
            @PathVariable Long id,
            @PathVariable Long passageId,
            @RequestPart("file") List<MultipartFile> files,
            Authentication auth) {
        String email = auth.getName();
        List<PaperCopyService.Upload> uploads = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                uploads.add(new PaperCopyService.Upload(file.getBytes(), file.getContentType(), file.getOriginalFilename()));
            }
        } catch (java.io.IOException ex) {
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError().body(Map.of("message", "Impossible d'enregistrer la copie.")));
        }
        return paperCopyService.async(() -> paperCopyService.addPages(id, passageId, email, uploads))
            .thenApply(this::paperCopyResponse);
    }

    /** Retrait d'une page de la copie papier, puis relecture des pages restantes. */
    @DeleteMapping("/{id}/passage/{passageId}/paper-correction/{index}")
    public CompletableFuture<ResponseEntity<?>> removePaperCorrectionPage(
            @PathVariable Long id,
            @PathVariable Long passageId,
            @PathVariable int index,
            Authentication auth) {
        String email = auth.getName();
        return paperCopyService.async(() -> paperCopyService.removePage(id, passageId, email, index))
            .thenApply(this::paperCopyResponse);
    }

    private ResponseEntity<?> paperCopyResponse(PaperCopyService.Result result) {
        if (result.error() != null) {
            return ResponseEntity.status(result.error().status()).body(Map.of("message", result.error().getMessage()));
        }
        PaperCopyService.Outcome outcome = result.outcome();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        PaperCopyService.Page first = outcome.pages().isEmpty() ? null : outcome.pages().get(0);
        body.put("url", first == null ? null : first.url());
        body.put("filename", first == null ? null : first.filename());
        body.put("pages", outcome.pages());
        // Texte lu sur la copie, renvoyé à l'étudiant pour qu'il vérifie la lecture
        body.put("transcription", outcome.transcription());
        if (outcome.warning() != null) body.put("ocrWarning", outcome.warning());
        return ResponseEntity.ok(body);
    }

    // ── Soumettre les réponses ─────────────────────────────────────────────

    /**
     * Soumission : traitée hors du thread de la requête, car le cas pratique est corrigé par l'IA
     * (voir {@link CaseGradingService}) sans retenir de connexion à la base pendant l'appel.
     */
    @PostMapping("/{id}/soumettre")
    public CompletableFuture<ResponseEntity<?>> soumettre(
            @PathVariable Long id,
            @RequestBody QcmSubmissionService.Submission input,
            Authentication auth) {
        return caseGradingService.submit(id, auth.getName(), input, passage -> buildResultat(passage, passage.getQcm()))
            .<ResponseEntity<?>>thenApply(outcome -> outcome.refusal() != null
                ? ResponseEntity.status(outcome.status()).body(Map.of("message", outcome.refusal()))
                : ResponseEntity.ok(outcome.result()))
            .exceptionally(e -> {
                log.error("Soumission du devoir {} impossible : {}", id, e.getMessage(), e);
                return ResponseEntity.internalServerError().body(Map.of("message", "La soumission a échoué : réessayez."));
            });
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
        // Contrôle de la date de naissance (âge, format) désactivé pour le moment : toute date est acceptée
        return null;
    }


    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }



    private boolean isPaperCase(QcmQuestion question) {
        return submissionService.isPaperCase(question);
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
