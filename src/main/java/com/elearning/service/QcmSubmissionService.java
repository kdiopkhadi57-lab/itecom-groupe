package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.QcmPassageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Soumission et correction d'un devoir (QCM, cas pratique, calculs, réponse rédigée).
 *
 * Les réponses sont enregistrées au fil de l'eau (brouillon) : si la soumission du navigateur
 * n'aboutit pas (fin du temps, exclusion, coupure réseau), le serveur soumet lui-même la copie
 * avec le dernier brouillon une fois le temps du devoir écoulé. Aucune copie n'est perdue.
 *
 * Le cas pratique est répondu dans la zone de saisie, sur une copie papier, ou les deux. Il est noté
 * par l'IA (corrigé du professeur comparé à l'ensemble de la réponse) ; la correction est préparée
 * hors transaction par {@link CaseGradingService} et transmise ici. Sans elle, la note provisoire est
 * la part des chiffres du corrigé retrouvés dans la réponse, et la correction par l'IA est relancée.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QcmSubmissionService {

    public record Answer(Long questionId, Long choiceId, Map<String, String> values) {}

    public record Submission(List<Answer> reponses, String documentAnswer, Boolean forced) {
        public static Submission empty() { return new Submission(List.of(), null, true); }
    }

    /** Soumission refusée (copie papier manquante, déjà soumis…), avec un message pour l'étudiant. */
    public static class SubmissionRefusedException extends RuntimeException {
        public SubmissionRefusedException(String message) { super(message); }
    }

    /** Copie soumise sans correction par l'IA : à corriger en arrière-plan une fois la transaction validée. */
    public record CaseGradingRequested(Long passageId) {}

    public static final int DEFAULT_DURATION_MINUTES = 30;
    /** Délai laissé au navigateur pour soumettre lui-même avant la soumission automatique par le serveur. */
    private static final int AUTO_SUBMIT_GRACE_MINUTES = 3;

    private static final String PLACEHOLDER_ANSWER = "Référence de correction fournie par le professeur.";

    private final QcmPassageRepository passageRepo;
    private final ObjectMapper objectMapper;
    private final CorrectionComparisonService comparisonService;
    private final ApplicationEventPublisher events;

    // ── Brouillon ──────────────────────────────────────────────────────────

    @Transactional
    public void saveDraft(QcmPassage passage, Submission submission) {
        if (Boolean.TRUE.equals(passage.getIsSubmitted())) return;
        try {
            passage.setDraftAnswers(objectMapper.writeValueAsString(submission));
            passage.setDraftSavedAt(LocalDateTime.now());
            passageRepo.save(passage);
        } catch (Exception e) {
            log.warn("Brouillon du passage {} non enregistré : {}", passage.getId(), e.getMessage());
        }
    }

    public Submission readDraft(QcmPassage passage) {
        if (passage.getDraftAnswers() == null || passage.getDraftAnswers().isBlank()) return Submission.empty();
        try {
            Submission draft = objectMapper.readValue(passage.getDraftAnswers(), Submission.class);
            return new Submission(draft.reponses() == null ? List.of() : draft.reponses(), draft.documentAnswer(), true);
        } catch (Exception e) {
            log.warn("Brouillon illisible pour le passage {} : {}", passage.getId(), e.getMessage());
            return Submission.empty();
        }
    }

    // ── Soumission automatique des copies dont le temps est écoulé ────────

    public LocalDateTime deadline(QcmPassage passage) {
        Integer minutes = passage.getQcm().getEstimatedDurationMinutes();
        return passage.getStartedAt().plusMinutes((minutes != null ? minutes : DEFAULT_DURATION_MINUTES) + AUTO_SUBMIT_GRACE_MINUTES);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void autoSubmitExpiredPassages() {
        for (QcmPassage passage : passageRepo.findByIsSubmittedFalseAndStartedAtIsNotNull()) {
            autoSubmitIfExpired(passage);
        }
    }

    /**
     * Soumet la copie avec le dernier brouillon si le temps du devoir est écoulé. Aucun appel à l'IA ici :
     * le cas pratique reçoit une note provisoire (chiffres retrouvés), puis la correction par l'IA se fait
     * en arrière-plan une fois la transaction validée.
     */
    @Transactional
    public boolean autoSubmitIfExpired(QcmPassage passage) {
        if (Boolean.TRUE.equals(passage.getIsSubmitted()) || passage.getStartedAt() == null) return false;
        if (deadline(passage).isAfter(LocalDateTime.now())) return false;
        try {
            submit(passage, readDraft(passage), null);
            log.info("Devoir #{} : copie du passage {} soumise automatiquement (temps écoulé)",
                passage.getQcm().getId(), passage.getId());
            return true;
        } catch (Exception e) {
            log.warn("Soumission automatique impossible pour le passage {} : {}", passage.getId(), e.getMessage());
            return false;
        }
    }

    // ── Soumission et correction ───────────────────────────────────────────

    /** Motif de refus de la soumission, ou null si elle est acceptée. */
    public String refusal(QcmPassage passage, Submission input) {
        if (Boolean.TRUE.equals(passage.getIsSubmitted())) return "Ce devoir a déjà été soumis.";
        // Une soumission forcée (fin du temps, exclusion) est toujours acceptée : la copie est corrigée en l'état
        if (Boolean.TRUE.equals(input.forced())) return null;
        Qcm qcm = passage.getQcm();
        Map<Long, Answer> responseMap = responseMap(input);
        boolean hasPaperCopy = passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank();
        if (Boolean.TRUE.equals(qcm.getPaperCorrectionRequired()) && !hasPaperCopy) {
            return "Joignez la photo ou le scan de votre copie papier avant de soumettre.";
        }
        // Cas pratique : réponse dans la zone de saisie, copie papier, ou les deux
        boolean caseUnanswered = qcm.getQuestions().stream().filter(this::isPaperCase)
            .anyMatch(q -> !hasCaseAnswer(responseMap.get(q.getId())));
        if (caseUnanswered && !hasPaperCopy) {
            return "Rédigez votre réponse au cas pratique dans la zone de saisie, ou joignez votre copie papier, avant de soumettre.";
        }
        return null;
    }

    /**
     * Questions « cas pratique » à faire corriger par l'IA, avec la réponse saisie et le texte lu sur la
     * copie ; null s'il n'y a pas de cas pratique. À appeler en transaction, avant l'appel à l'IA.
     */
    public QcmCaseOcrGradingService.CaseRequest caseRequest(QcmPassage passage, Submission input) {
        Qcm qcm = passage.getQcm();
        Map<Long, Answer> responseMap = responseMap(input);
        List<QcmCaseOcrGradingService.CaseQuestion> questions = qcm.getQuestions().stream()
            .filter(this::isPaperCase)
            .map(q -> new QcmCaseOcrGradingService.CaseQuestion(q.getId(), q.getPoints(), q.getQuestionText(),
                q.getCaseScenario(), correctionTextFor(q, qcm), typedAnswerOf(responseMap.get(q.getId()))))
            .toList();
        return questions.isEmpty() ? null
            : new QcmCaseOcrGradingService.CaseRequest(qcm.getTitle(), questions, passage.getOcrExtractedText());
    }

    /** Soumission sans correction par l'IA (tests, soumission automatique) : note provisoire du cas pratique. */
    @Transactional
    public QcmPassage submit(QcmPassage passage, Submission input) {
        return submit(passage, input, null);
    }

    /**
     * Enregistre la copie et calcule la note. {@code caseGrade} est la correction du cas pratique par
     * l'IA, préparée hors transaction ; null si elle n'a pas pu être faite.
     */
    @Transactional
    public QcmPassage submit(QcmPassage passage, Submission input, QcmCaseOcrGradingService.CaseGrade caseGrade) {
        Qcm qcm = passage.getQcm();
        String refused = refusal(passage, input);
        if (refused != null) throw new SubmissionRefusedException(refused);
        Map<Long, Answer> responseMap = responseMap(input);

        passage.setDocumentAnswer(input.documentAnswer());
        int score = 0, maxScore = 0;
        passage.getReponses().clear();

        List<String> notes = new ArrayList<>();
        List<Map<String, Object>> correctionDetail = new ArrayList<>();
        boolean caseGradePending = false;
        Integer caseScore = null;
        if (qcm.getQuestions().isEmpty()) {
            var expected = comparisonService.expectedValues(qcm.getCorrectionText(), qcm.getSubjectText());
            var result = comparisonService.compare(expected, joinTexts(input.documentAnswer(), passage.getOcrExtractedText()));
            score = result.matched();
            maxScore = result.total();
            if (!expected.isEmpty()) notes.add(result.report());
        }

        for (QcmQuestion question : qcm.getQuestions()) {
            maxScore += question.getPoints();
            Answer response = responseMap.get(question.getId());
            Long chosenId = response != null ? response.choiceId() : null;

            QcmChoice chosen = question.getChoices().stream()
                .filter(c -> c.getId().equals(chosenId)).findFirst().orElse(null);

            boolean paperCase = isPaperCase(question);
            boolean correct;
            if (paperCase) {
                String typed = typedAnswerOf(response);
                // Contrôle : chiffres du corrigé retrouvés dans la réponse (zone de saisie + copie papier)
                var expected = comparisonService.expectedValues(correctionTextFor(question, qcm), subjectTextFor(question, qcm));
                var numbers = comparisonService.compare(expected, joinTexts(typed, passage.getOcrExtractedText()));
                var aiGrade = caseGrade == null ? null : caseGrade.questions().get(question.getId());
                int questionScore;
                String source;
                if (aiGrade != null) {
                    questionScore = (int) Math.round(aiGrade.score());
                    source = "IA";
                } else if (!expected.isEmpty()) {
                    questionScore = (int) Math.round(question.getPoints() * numbers.ratio());
                    source = "chiffres";
                    caseGradePending = true;
                } else {
                    questionScore = 0;
                    source = "manuelle";
                    caseGradePending = true;
                }
                score += questionScore;
                caseScore = (caseScore == null ? 0 : caseScore) + questionScore;
                correct = questionScore >= question.getPoints();

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("questionId", question.getId());
                detail.put("questionText", shorten(question.getQuestionText(), 160));
                detail.put("points", question.getPoints());
                detail.put("score", questionScore);
                detail.put("source", source);
                detail.put("comment", aiGrade != null ? aiGrade.comment() : null);
                detail.put("numbersFound", numbers.matched());
                detail.put("numbersTotal", numbers.total());
                detail.put("numbersMissing", numbers.missing().stream().map(CorrectionComparisonService.ExpectedValue::raw).toList());
                correctionDetail.add(detail);
            } else if ("PRACTICAL".equals(question.getQuestionType())) {
                double ratio = gradePractical(question, response != null ? response.values() : Map.of());
                score += (int) Math.round(question.getPoints() * ratio);
                correct = ratio >= 0.999;
            } else {
                correct = chosen != null && Boolean.TRUE.equals(chosen.getIsCorrect());
                if (correct) score += question.getPoints();
            }

            String typedAnswer = paperCase || "LONG_TEXT".equalsIgnoreCase(question.getQuestionType())
                ? typedAnswerOf(response) : null;
            passage.getReponses().add(QcmReponse.builder()
                .passage(passage).question(question)
                .choiceSelected(chosen).textAnswer(typedAnswer).isCorrect(correct).build());
        }

        if (!correctionDetail.isEmpty()) notes.add(caseNote(correctionDetail, caseGrade, passage));
        passage.setOcrScore(caseGrade != null ? caseScore : null);
        passage.setOcrCorrectionNote(notes.isEmpty() ? null : String.join("\n\n", notes));
        try {
            passage.setCorrectionDetail(correctionDetail.isEmpty() ? null : objectMapper.writeValueAsString(correctionDetail));
        } catch (Exception e) {
            log.warn("Détail de correction non enregistré : {}", e.getMessage());
        }
        passage.setScore(score);
        passage.setMaxScore(maxScore > 0 ? maxScore : 1);
        passage.setIsSubmitted(true);
        passage.setSubmittedAt(LocalDateTime.now());
        QcmPassage saved = passageRepo.save(passage);
        // Note provisoire : correction par l'IA en arrière-plan, une fois la copie enregistrée
        if (caseGradePending && caseGrade == null) events.publishEvent(new CaseGradingRequested(saved.getId()));
        return saved;
    }

    /**
     * Recorrige une copie déjà soumise avec ses réponses enregistrées et une nouvelle correction par l'IA
     * (copie lue ou corrigée après la soumission). Une note modifiée par le professeur n'est jamais écrasée.
     */
    @Transactional
    public void regrade(QcmPassage passage, QcmCaseOcrGradingService.CaseGrade caseGrade) {
        if (!Boolean.TRUE.equals(passage.getIsSubmitted()) || passage.getManualScore() != null) return;
        LocalDateTime submittedAt = passage.getSubmittedAt();
        passage.setIsSubmitted(false);
        submit(passage, readDraft(passage), caseGrade);
        passage.setSubmittedAt(submittedAt);
        passageRepo.save(passage);
    }

    /** Note du professeur : synthèse de l'IA, note et commentaire par question, chiffres du corrigé retrouvés. */
    private String caseNote(List<Map<String, Object>> details, QcmCaseOcrGradingService.CaseGrade caseGrade, QcmPassage passage) {
        StringBuilder sb = new StringBuilder();
        if (caseGrade != null) {
            sb.append("Correction par l'IA (zone de saisie et copie papier).");
            if (caseGrade.comment() != null && !caseGrade.comment().isBlank()) sb.append("\n").append(caseGrade.comment());
        } else {
            sb.append("Correction par l'IA en attente ou indisponible : note provisoire calculée à partir des chiffres du corrigé retrouvés dans la réponse.");
        }
        for (Map<String, Object> d : details) {
            sb.append("\n\n").append(d.get("questionText")).append(" — ").append(d.get("score")).append("/").append(d.get("points"));
            if (d.get("comment") != null && !d.get("comment").toString().isBlank()) sb.append("\n").append(d.get("comment"));
            if (((Number) d.get("numbersTotal")).intValue() > 0) {
                sb.append("\nChiffres du corrigé retrouvés : ").append(d.get("numbersFound")).append("/").append(d.get("numbersTotal"));
                @SuppressWarnings("unchecked") List<String> missing = (List<String>) d.get("numbersMissing");
                if (!missing.isEmpty()) sb.append(" (absents ou différents : ").append(String.join(", ", missing)).append(")");
            }
        }
        boolean hasPaperCopy = passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank();
        if (hasPaperCopy && (passage.getOcrExtractedText() == null || passage.getOcrExtractedText().isBlank())) {
            sb.append("\n\nLa copie papier n'a pas pu être lue automatiquement : vérifiez-la.");
        }
        return sb.toString();
    }

    /** Devoir « documentaire » sans question : note recalculée tant que le professeur ne l'a pas modifiée. */
    @Transactional
    public void refreshDocumentScore(QcmPassage passage, Qcm qcm) {
        if (!qcm.getQuestions().isEmpty() || passage.getManualScore() != null) return;
        var expected = comparisonService.expectedValues(qcm.getCorrectionText(), qcm.getSubjectText());
        if (expected.isEmpty()) return;
        var result = comparisonService.compare(expected, joinTexts(passage.getDocumentAnswer(), passage.getOcrExtractedText()));
        passage.setScore(result.matched());
        passage.setMaxScore(result.total());
        passageRepo.save(passage);
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    private static Map<Long, Answer> responseMap(Submission input) {
        return input.reponses() == null ? Map.of() : input.reponses().stream()
            .filter(r -> r != null && r.questionId() != null)
            .collect(Collectors.toMap(Answer::questionId, r -> r, (a, b) -> b));
    }

    /** Corrigé d'une question : réponse attendue, sinon correction structurée, sinon correction du devoir. */
    public String correctionTextFor(QcmQuestion question, Qcm qcm) {
        return firstNonBlank(PLACEHOLDER_ANSWER.equals(question.getExpectedAnswer()) ? null : question.getExpectedAnswer(),
            question.getCorrectionData(), qcm.getCorrectionText());
    }

    private static String subjectTextFor(QcmQuestion question, Qcm qcm) {
        return firstNonBlank(qcm.getSubjectText(), question.getQuestionText());
    }

    private static String joinTexts(String... texts) {
        return Arrays.stream(texts).filter(t -> t != null && !t.isBlank()).collect(Collectors.joining("\n"));
    }

    /** Cas pratique répondu : rédaction ou au moins une case du tableau du sujet remplie. */
    private static boolean hasCaseAnswer(Answer response) {
        return response != null && response.values() != null
            && response.values().values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    /**
     * Réponse saisie pour une question : la rédaction de la zone de saisie, suivie des cases remplies
     * dans le tableau du sujet (« ligne : valeur »).
     */
    static String typedAnswerOf(Answer response) {
        if (response == null || response.values() == null) return null;
        List<String> parts = new ArrayList<>();
        String answer = response.values().get("answer");
        if (answer != null && !answer.isBlank()) parts.add(answer.trim());
        response.values().entrySet().stream()
            .filter(e -> !"answer".equals(e.getKey()) && e.getValue() != null && !e.getValue().isBlank())
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> parts.add(e.getKey() + " : " + e.getValue().trim()));
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    public boolean isPaperCase(QcmQuestion question) {
        return "CASE".equalsIgnoreCase(question.getQuestionType())
            || ("PRACTICAL".equalsIgnoreCase(question.getQuestionType())
                && question.getCaseScenario() != null && !question.getCaseScenario().isBlank());
    }

    public boolean hasPaperCase(Qcm qcm) {
        return qcm.getQuestions().stream().anyMatch(this::isPaperCase);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String shorten(String text, int max) {
        String t = text == null ? "" : text.trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
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
}
