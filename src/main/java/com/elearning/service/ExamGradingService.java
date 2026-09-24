package com.elearning.service;

import com.elearning.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExamGradingService {

    public record GradingResult(List<AnswerScore> scores, String summary) {}
    public record AnswerScore(Long questionId, double score, String comment, String transcribedAnswer) {}

    public GradingResult gradeSubmission(ExamSubmission submission) {
        List<AnswerScore> scores = submission.getAnswers().stream()
            .map(this::compareWithReference)
            .toList();
        long correct = scores.stream().filter(score -> score.score() > 0).count();
        return new GradingResult(scores,
            "Correction automatique effectuée par comparaison avec les réponses de référence enregistrées en base. " +
            correct + " réponse(s) contiennent les éléments attendus.");
    }

    private AnswerScore compareWithReference(ExamAnswer answer) {
        ExamQuestion question = answer.getQuestion();
        String student = normalize(answer.getStudentAnswer());
        String reference = normalize(question.getReferenceAnswer());
        double ratio = comparisonRatio(student, reference, question.getQuestionText());
        double score = Math.round(question.getMaxScore() * ratio * 100.0) / 100.0;
        String comment = ratio >= 0.99 ? "Réponse correcte." : ratio >= 0.5
            ? "Réponse partiellement correcte : certains éléments de la réponse de référence sont présents."
            : "Réponse insuffisante par rapport à la réponse de référence enregistrée.";
        return new AnswerScore(question.getId(), score, comment, null);
    }

    private double comparisonRatio(String student, String reference, String question) {
        if (student.isBlank() || reference.isBlank()) return 0;
        if (student.equals(reference)) return 1;

        // Les examens QCM stockent la bonne lettre (A, B, C ou D) en base.
        if (reference.matches("[a-d]")) {
            if (student.matches("[a-d]") && student.equals(reference)) return 1;
            String expectedOption = extractOption(question, reference);
            return !expectedOption.isBlank() && student.equals(expectedOption) ? 1 : 0;
        }

        Set<String> expectedWords = significantWords(reference);
        Set<String> studentWords = significantWords(student);
        if (expectedWords.isEmpty() || studentWords.isEmpty()) return 0;
        long matched = studentWords.stream().filter(expectedWords::contains).count();
        double precision = (double) matched / studentWords.size();
        double recall = (double) matched / expectedWords.size();
        return 2 * precision * recall / (precision + recall);
    }

    private String extractOption(String question, String letter) {
        for (String line : question.split("\\n")) {
            String trimmed = line.trim().toLowerCase(Locale.ROOT);
            if (trimmed.matches("^" + letter + "[.)].*")) {
                return normalize(trimmed.substring(2));
            }
        }
        return "";
    }

    private Set<String> significantWords(String text) {
        Set<String> ignored = Set.of("le", "la", "les", "un", "une", "des", "de", "du", "et", "ou", "en", "a", "à", "est", "sont", "pour", "dans", "qui", "que");
        return Arrays.stream(text.split("\\s+"))
            .filter(word -> word.length() > 2 && !ignored.contains(word))
            .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }
}
