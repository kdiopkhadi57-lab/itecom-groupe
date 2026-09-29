package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.QcmPassageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Soumission et correction d'un devoir (QCM, cas pratique, calculs, réponse rédigée).
 *
 * Les réponses sont enregistrées au fil de l'eau (brouillon) : si la soumission du navigateur
 * n'aboutit pas (fin du temps, exclusion, coupure réseau), le serveur soumet lui-même la copie
 * avec le dernier brouillon une fois le temps du devoir écoulé. Aucune copie n'est perdue.
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

    public static final int DEFAULT_DURATION_MINUTES = 30;
    /** Délai laissé au navigateur pour soumettre lui-même avant la soumission automatique par le serveur. */
    private static final int AUTO_SUBMIT_GRACE_MINUTES = 3;

    private final QcmPassageRepository passageRepo;
    private final ObjectMapper objectMapper;
    private final QcmCaseOcrGradingService caseOcrGradingService;
    private final CorrectionComparisonService comparisonService;
    private final CorrectionGridService gridService;

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

    private Submission readDraft(QcmPassage passage) {
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

    /** Soumet la copie avec le dernier brouillon si le temps du devoir est écoulé. */
    @Transactional
    public boolean autoSubmitIfExpired(QcmPassage passage) {
        if (Boolean.TRUE.equals(passage.getIsSubmitted()) || passage.getStartedAt() == null) return false;
        if (deadline(passage).isAfter(LocalDateTime.now())) return false;
        try {
            submit(passage, readDraft(passage));
            log.info("Devoir #{} : copie du passage {} soumise automatiquement (temps écoulé)",
                passage.getQcm().getId(), passage.getId());
            return true;
        } catch (Exception e) {
            log.warn("Soumission automatique impossible pour le passage {} : {}", passage.getId(), e.getMessage());
            return false;
        }
    }

    // ── Soumission et correction ───────────────────────────────────────────

    @Transactional
    public QcmPassage submit(QcmPassage passage, Submission input) {
        Qcm qcm = passage.getQcm();
        if (Boolean.TRUE.equals(passage.getIsSubmitted())) {
            throw new SubmissionRefusedException("Ce devoir a déjà été soumis.");
        }
        Map<Long, Answer> responseMap = input.reponses() == null ? Map.of() :
            input.reponses().stream()
                .filter(r -> r != null && r.questionId() != null)
                .collect(Collectors.toMap(Answer::questionId, r -> r, (a, b) -> b));

        boolean hasPaperCopy = passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank();
        // Le cas pratique peut être rédigé directement dans la zone de saisie : la copie papier
        // n'est alors plus obligatoire, sauf si le professeur l'a exigée explicitement.
        boolean allCasesTyped = hasPaperCase(qcm) && qcm.getQuestions().stream().filter(this::isPaperCase)
            .allMatch(q -> hasCaseAnswer(responseMap.get(q.getId())));
        boolean paperMandatory = Boolean.TRUE.equals(qcm.getPaperCorrectionRequired()) || (hasPaperCase(qcm) && !allCasesTyped);
        // Une soumission forcée (fin du temps, exclusion) est toujours acceptée : la copie est corrigée en l'état
        if (paperMandatory && !hasPaperCopy && !Boolean.TRUE.equals(input.forced())) {
            throw new SubmissionRefusedException(Boolean.TRUE.equals(qcm.getPaperCorrectionRequired())
                ? "Joignez la photo ou le scan de votre copie papier avant de soumettre."
                : "Rédigez votre réponse au cas pratique dans la zone de saisie, ou joignez votre copie papier, avant de soumettre.");
        }

        passage.setDocumentAnswer(input.documentAnswer());

        // Pas de copie scannée ni de chiffres dans la correction : avis de l'IA sur la réponse saisie
        if (!hasPaperCopy && allCasesTyped && passage.getOcrScore() == null
                && qcm.getQuestions().stream().filter(this::isPaperCase).allMatch(q -> gridService.gridFor(q, qcm).isEmpty())) {
            Map<Long, String> typed = qcm.getQuestions().stream().filter(this::isPaperCase)
                .collect(Collectors.toMap(QcmQuestion::getId, q -> java.util.Objects.toString(textAnswerOf(responseMap.get(q.getId())), "")));
            QcmCaseOcrGradingService.GradeResult grade = caseOcrGradingService.gradeTypedAnswers(qcm, typed);
            passage.setOcrScore(grade.score());
            passage.setOcrCorrectionNote(grade.comment());
        }

        int score = 0, maxScore = 0;
        passage.getReponses().clear();

        List<String> comparisonReports = new ArrayList<>();
        List<Map<String, Object>> correctionDetail = new ArrayList<>();
        Map<String, String> extracted = readMap(passage.getExtractedAnswers());
        if (qcm.getQuestions().isEmpty()) {
            var expected = comparisonService.expectedValues(qcm.getCorrectionText(), qcm.getSubjectText());
            var result = comparisonService.compare(expected, joinTexts(input.documentAnswer(), passage.getOcrExtractedText()));
            score = result.matched();
            maxScore = result.total();
            if (!expected.isEmpty()) comparisonReports.add(result.report());
        }

        for (QcmQuestion question : qcm.getQuestions()) {
            maxScore += question.getPoints();
            Answer response = responseMap.get(question.getId());
            Long chosenId = response != null ? response.choiceId() : null;

            QcmChoice chosen = question.getChoices().stream()
                .filter(c -> c.getId().equals(chosenId)).findFirst().orElse(null);

            boolean paperCase = isPaperCase(question);
            double practicalRatio = "PRACTICAL".equals(question.getQuestionType()) && !paperCase
                ? gradePractical(question, response != null ? response.values() : Map.of()) : 0;
            boolean correct = paperCase
                ? passage.getOcrScore() != null && passage.getOcrScore() >= question.getPoints()
                : "PRACTICAL".equals(question.getQuestionType())
                ? practicalRatio >= 0.999
                : chosen != null && Boolean.TRUE.equals(chosen.getIsCorrect());
            var grid = paperCase ? gridService.gridFor(question, qcm) : List.<CorrectionGridService.GridRow>of();
            if (paperCase && !grid.isEmpty()) {
                // Comparaison ligne par ligne : valeur saisie, sinon valeur lue sur la copie, sinon texte libre
                Map<String, String> typed = new java.util.HashMap<>(response != null && response.values() != null ? response.values() : Map.of());
                typed.remove("answer");
                String prefix = question.getId() + ":";
                Map<String, String> scanned = new java.util.HashMap<>();
                extracted.forEach((k, v) -> { if (k.startsWith(prefix)) scanned.put(k.substring(prefix.length()), v); });
                var result = gridService.compare(grid, typed, scanned,
                    joinTexts(textAnswerOf(response), passage.getOcrExtractedText()));
                score += (int) Math.round(question.getPoints() * result.ratio());
                correct = result.rows().stream().allMatch(CorrectionGridService.RowResult::correct);
                comparisonReports.add(result.report());
                correctionDetail.add(Map.of("questionId", question.getId(), "earned", result.earned(),
                    "total", result.total(), "rows", result.rows()));
                if (hasPaperCopy && (passage.getOcrExtractedText() == null || passage.getOcrExtractedText().isBlank())) {
                    comparisonReports.add("Transcription de la copie scannée indisponible : vérifiez la copie manuellement.");
                }
            } else if (paperCase) {
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

        if (!comparisonReports.isEmpty()) {
            String aiNote = passage.getOcrCorrectionNote();
            passage.setOcrCorrectionNote(String.join("\n\n", comparisonReports)
                + (aiNote == null || aiNote.isBlank() ? "" : "\n\nAvis de l'IA : " + aiNote));
        }
        try {
            passage.setCorrectionDetail(correctionDetail.isEmpty() ? null : objectMapper.writeValueAsString(correctionDetail));
        } catch (Exception e) {
            log.warn("Détail de correction non enregistré : {}", e.getMessage());
        }
        passage.setScore(score);
        passage.setMaxScore(maxScore > 0 ? maxScore : 1);
        passage.setIsSubmitted(true);
        passage.setSubmittedAt(LocalDateTime.now());
        return passageRepo.save(passage);
    }

    /**
     * Recorrige une copie déjà soumise avec ses réponses enregistrées, par exemple quand la lecture de la
     * copie papier se termine après la soumission. Une note modifiée par le professeur n'est jamais écrasée.
     */
    @Transactional
    public void regrade(QcmPassage passage) {
        if (!Boolean.TRUE.equals(passage.getIsSubmitted()) || passage.getManualScore() != null) return;
        LocalDateTime submittedAt = passage.getSubmittedAt();
        passage.setIsSubmitted(false);
        submit(passage, readDraft(passage));
        passage.setSubmittedAt(submittedAt);
        passageRepo.save(passage);
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

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }


    private static String joinTexts(String... texts) {
        return Arrays.stream(texts).filter(t -> t != null && !t.isBlank()).collect(Collectors.joining("\n"));
    }

    /** Cas pratique répondu : rédaction ou au moins un résultat saisi dans le tableau de la grille. */
    private static boolean hasCaseAnswer(Answer response) {
        return response != null && response.values() != null
            && response.values().values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    private static String textAnswerOf(Answer response) {
        if (response == null || response.values() == null) return null;
        String answer = response.values().get("answer");
        return answer == null || answer.isBlank() ? null : answer.trim();
    }

    public boolean isPaperCase(QcmQuestion question) {
        return "CASE".equalsIgnoreCase(question.getQuestionType())
            || ("PRACTICAL".equalsIgnoreCase(question.getQuestionType())
                && question.getCaseScenario() != null && !question.getCaseScenario().isBlank());
    }

    public boolean hasPaperCase(Qcm qcm) {
        return qcm.getQuestions().stream().anyMatch(this::isPaperCase);
    }

    private int ocrScoreForQuestion(QcmPassage passage, QcmQuestion question, Qcm qcm) {
        if (passage.getOcrScore() == null) return 0;
        int caseMax = qcm.getQuestions().stream().filter(this::isPaperCase).mapToInt(QcmQuestion::getPoints).sum();
        if (caseMax <= 0) return 0;
        return (int) Math.round(passage.getOcrScore() * question.getPoints() / (double) caseMax);
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
