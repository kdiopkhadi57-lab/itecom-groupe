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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![A-Za-z])[-+]?\\d[\\d .]*?(?:,\\d+|\\.\\d+)?(?=\\s*(?:FCFA|%|$))", Pattern.CASE_INSENSITIVE);

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
    @Data static class QcmTakeDto   { Long id; String title; String description; String subjectFileUrl; String subjectText; Long passageId; Integer estimatedDurationMinutes; Boolean paperCorrectionRequired; String paperCorrectionUrl; String paperCorrectionFilename; String startedAt; List<QuestionDto> questions; }

    @Data static class AccesDto     { Long id; String title; String description; Integer estimatedDurationMinutes; int questionCount; String studentName; String studentLevel; }

    @Data static class SoumettreInput { List<ReponseInput> reponses; String documentAnswer; }
    @Data static class ReponseInput   { Long questionId; Long choiceId; Map<String, String> values; }

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
    @Transactional(readOnly = true)
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
        return ResponseEntity.ok(dto);
    }

    // ── Commencer ou reprendre un QCM ─────────────────────────────────────

    @PostMapping("/{id}/commencer")
    @Transactional
    public ResponseEntity<?> commencer(@PathVariable Long id, Authentication auth) {
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

        if (Boolean.TRUE.equals(passage.getIsSubmitted()))
            return ResponseEntity.badRequest().build();

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
            if (hasPaperCase(qcm)) {
                QcmCaseOcrGradingService.GradeResult grade = caseOcrGradingService.grade(qcm, List.of(
                    new QcmCaseOcrGradingService.ScannedFile(file.getBytes(), file.getContentType(), file.getOriginalFilename())
                ));
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
    public ResponseEntity<ResultatDto> soumettre(
            @PathVariable Long id,
            @RequestBody SoumettreInput input,
            Authentication auth) {

        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        QcmPassage passage = passageRepo.findByQcmAndStudent(qcm, student).orElseThrow();

        // Map questionId -> choice choisi
        Map<Long, ReponseInput> responseMap = input.reponses == null ? Map.of() :
            input.reponses.stream()
            .filter(r -> r.questionId != null)
            .collect(Collectors.toMap(r -> r.questionId, r -> r, (a, b) -> b));

        boolean hasPaperCopy = passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank();
        // Le cas pratique peut être rédigé directement dans la zone de saisie : la copie papier
        // n'est alors plus obligatoire, sauf si le professeur l'a exigée explicitement.
        boolean allCasesTyped = hasPaperCase(qcm) && qcm.getQuestions().stream().filter(this::isPaperCase)
            .allMatch(q -> textAnswerOf(responseMap.get(q.getId())) != null);
        boolean paperMandatory = Boolean.TRUE.equals(qcm.getPaperCorrectionRequired()) || (hasPaperCase(qcm) && !allCasesTyped);
        if (paperMandatory && !hasPaperCopy) {
            return ResponseEntity.badRequest().build();
        }

        if (Boolean.TRUE.equals(passage.getIsSubmitted()))
            return ResponseEntity.badRequest().build();

        passage.setDocumentAnswer(input.documentAnswer);

        // Pas de copie scannée : on corrige le cas pratique à partir de la réponse saisie
        if (!hasPaperCopy && allCasesTyped && passage.getOcrScore() == null) {
            Map<Long, String> typed = qcm.getQuestions().stream().filter(this::isPaperCase)
                .collect(Collectors.toMap(QcmQuestion::getId, q -> textAnswerOf(responseMap.get(q.getId()))));
            QcmCaseOcrGradingService.GradeResult grade = caseOcrGradingService.gradeTypedAnswers(qcm, typed);
            passage.setOcrScore(grade.score());
            passage.setOcrCorrectionNote(grade.comment());
        }

        int score = 0, maxScore = 0;
        passage.getReponses().clear();

        if (qcm.getQuestions().isEmpty()) {
            List<Double> expected = extractDocumentCorrectionValues(qcm.getCorrectionText());
            List<Double> submitted = extractDocumentAnswerValues(input.documentAnswer);
            maxScore = expected.size();
            for (int i = 0; i < expected.size(); i++) {
                if (i < submitted.size() && closeEnough(expected.get(i), submitted.get(i))) score++;
            }
        }

        for (QcmQuestion question : qcm.getQuestions()) {
            maxScore += question.getPoints();
            ReponseInput response = responseMap.get(question.getId());
            Long chosenId = response != null ? response.choiceId : null;

            QcmChoice chosen = question.getChoices().stream()
                .filter(c -> c.getId().equals(chosenId)).findFirst().orElse(null);

            boolean paperCase = isPaperCase(question);
            double practicalRatio = "PRACTICAL".equals(question.getQuestionType()) && !paperCase
                ? gradePractical(question, response != null ? response.values : Map.of()) : 0;
            boolean correct = paperCase
                ? passage.getOcrScore() != null && passage.getOcrScore() >= question.getPoints()
                : "PRACTICAL".equals(question.getQuestionType())
                ? practicalRatio >= 0.999
                : chosen != null && Boolean.TRUE.equals(chosen.getIsCorrect());
            if (paperCase) {
                score += ocrScoreForQuestion(passage, question, qcm);
            } else if ("PRACTICAL".equals(question.getQuestionType())) {
                score += (int) Math.round(question.getPoints() * practicalRatio);
            } else if (correct) {
                score += question.getPoints();
            }

            String typedAnswer = paperCase || "LONG_TEXT".equalsIgnoreCase(question.getQuestionType())
                ? textAnswerOf(response) : null;
            passage.getReponses().add(QcmReponse.builder()
                .passage(passage).question(question)
                .choiceSelected(chosen).textAnswer(typedAnswer).isCorrect(correct).build());
        }

        passage.setScore(score);
        passage.setMaxScore(maxScore > 0 ? maxScore : 1);
        passage.setIsSubmitted(true);
        passage.setSubmittedAt(LocalDateTime.now());
        passageRepo.save(passage);

        return ResponseEntity.ok(buildResultat(passage, qcm));
    }

    private String textAnswerOf(ReponseInput response) {
        if (response == null || response.values == null) return null;
        String answer = response.values.get("answer");
        return answer == null || answer.isBlank() ? null : answer.trim();
    }

    private boolean isPaperCase(QcmQuestion question) {
        return "CASE".equalsIgnoreCase(question.getQuestionType())
            || ("PRACTICAL".equalsIgnoreCase(question.getQuestionType())
                && question.getCaseScenario() != null && !question.getCaseScenario().isBlank());
    }

    private int ocrScoreForQuestion(QcmPassage passage, QcmQuestion question, Qcm qcm) {
        if (passage.getOcrScore() == null) return 0;
        int caseMax = qcm.getQuestions().stream().filter(this::isPaperCase)
            .mapToInt(QcmQuestion::getPoints).sum();
        if (caseMax <= 0) return 0;
        return (int) Math.round(passage.getOcrScore() * question.getPoints() / (double) caseMax);
    }

    private boolean hasPaperCase(Qcm qcm) {
        return qcm.getQuestions().stream().anyMatch(question ->
            "CASE".equalsIgnoreCase(question.getQuestionType())
                || ("PRACTICAL".equalsIgnoreCase(question.getQuestionType())
                    && question.getCaseScenario() != null && !question.getCaseScenario().isBlank()));
    }

    private List<Double> extractDocumentCorrectionValues(String correctionText) {
        List<Double> values = new ArrayList<>();
        if (correctionText == null) return values;
        for (String line : correctionText.split("\\R")) {
            String[] cells = line.split("\\t");
            String candidate = cells.length > 2 ? cells[2] : line;
            Matcher matcher = NUMBER_PATTERN.matcher(candidate);
            if (matcher.find()) values.add(parseDocumentNumber(matcher.group()));
        }
        return values;
    }

    private List<Double> extractDocumentAnswerValues(String answer) {
        List<Double> values = new ArrayList<>();
        if (answer == null) return values;
        Matcher matcher = NUMBER_PATTERN.matcher(answer);
        while (matcher.find()) values.add(parseDocumentNumber(matcher.group()));
        return values;
    }

    private double parseDocumentNumber(String value) {
        String normalized = value.replace(" ", "").replace(".", "").replace(',', '.');
        return Double.parseDouble(normalized);
    }

    private boolean closeEnough(double expected, double actual) {
        return Math.abs(expected - actual) <= Math.max(0.001, Math.abs(expected) * 0.001);
    }

    private void refreshDocumentScore(QcmPassage passage, Qcm qcm) {
        if (!qcm.getQuestions().isEmpty() || passage.getDocumentAnswer() == null) return;
        List<Double> expected = extractDocumentCorrectionValues(qcm.getCorrectionText());
        List<Double> submitted = extractDocumentAnswerValues(passage.getDocumentAnswer());
        int score = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (i < submitted.size() && closeEnough(expected.get(i), submitted.get(i))) score++;
        }
        passage.setScore(score);
        passage.setMaxScore(expected.isEmpty() ? 1 : expected.size());
        passageRepo.save(passage);
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

    private double gradePractical(QcmQuestion question, Map<String, String> submitted) {
        if (submitted == null || submitted.isEmpty()) return 0;
        try {
            Map<String, Object> expected = objectMapper.readValue(question.getCorrectionData(), new TypeReference<>() {});
            if (expected.isEmpty() || submitted.size() != expected.size()) return 0;
            long valid = expected.entrySet().stream().filter(entry -> {
                String actual = submitted.get(entry.getKey());
                if (actual == null || actual.isBlank()) return false;
                try {
                    double expectedValue = parseNumber(String.valueOf(entry.getValue()));
                    double actualValue = parseNumber(actual);
                    double tolerance = Math.max(0.001, Math.abs(expectedValue) * 0.001);
                    return Math.abs(expectedValue - actualValue) <= tolerance;
                } catch (NumberFormatException ex) {
                    return normalize(actual).equals(normalize(String.valueOf(entry.getValue())));
                }
            }).count();
            return (double) valid / expected.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseNumber(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", "")
            .replaceAll("[^0-9,.-]", "");
        int comma = normalized.lastIndexOf(',');
        int dot = normalized.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            normalized = comma > dot
                ? normalized.replace(".", "").replace(',', '.')
                : normalized.replace(",", "");
        } else if (comma >= 0 && normalized.indexOf(',') != comma) {
            normalized = normalized.replace(",", "");
        } else if (comma >= 0) {
            normalized = normalized.replace(',', '.');
        }
        return Double.parseDouble(normalized);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // ── Voir mon résultat ──────────────────────────────────────────────────

    @GetMapping("/{id}/mon-resultat")
    @Transactional
    public ResponseEntity<ResultatDto> monResultat(@PathVariable Long id, Authentication auth) {
        User student = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        QcmPassage passage = passageRepo.findByQcmAndStudent(qcm, student)
            .filter(p -> Boolean.TRUE.equals(p.getIsSubmitted()))
            .orElseThrow(() -> new RuntimeException("Résultat non disponible"));
        if (isStillLocked(passage)) {
            throw new ResultLockedException(computeUnlockAt(passage));
        }
        refreshDocumentScore(passage, qcm);
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
