package com.elearning.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compare les chiffres de la correction du professeur avec ceux de la réponse de l'étudiant
 * (réponse saisie et/ou transcription de la copie scannée).
 *
 * Les valeurs attendues sont prises dans la colonne « Réponse attendue » quand la correction
 * est un tableau ; sinon ce sont les chiffres de la correction absents du sujet (les données
 * de l'énoncé ne sont pas des résultats à retrouver).
 */
@Service
public class CorrectionComparisonService {

    public static final double DEFAULT_RELATIVE_TOLERANCE = 0.001; // 0,1 %

    /** Nombre au format français (« 5 280 000 », « 1.250,50 », « 12,5 ») ou simple (« 5280000 », « 12.5 »). */
    private static final Pattern NUMBER = Pattern.compile(
        "(?<![\\d.,])-?(?:\\d{1,3}(?:[ \\u00a0\\u202f.]\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?)(?![\\d])");

    /** Numéros de questions / d'items, qui ne sont pas des résultats. */
    private static final Pattern ENUMERATOR = Pattern.compile(
        "(?im)(?:^\\s*\\d{1,2}\\s*[.)\\t-](?=\\s|\\t|$)|(?:question|exercice|partie|dossier|annexe|n°|no|q)\\s*\\d{1,2}\\b)");

    private static final Pattern TOLERANCE_TEXT = Pattern.compile("(?i)tol[ée]rance[^\\n\\t]*");

    public record ExpectedValue(String label, double value, double relativeTolerance, String raw) {}

    public record Result(int matched, int total, List<ExpectedValue> found, List<ExpectedValue> missing) {
        public double ratio() { return total == 0 ? 0 : (double) matched / total; }

        public String report() {
            StringBuilder sb = new StringBuilder("Comparaison des chiffres avec la correction : ")
                .append(matched).append("/").append(total).append(" valeur(s) retrouvée(s).");
            for (ExpectedValue v : found) sb.append("\n✓ ").append(describe(v));
            for (ExpectedValue v : missing) sb.append("\n✗ ").append(describe(v)).append(" — absent ou incorrect");
            return sb.toString();
        }

        private static String describe(ExpectedValue v) {
            return (v.label() == null || v.label().isBlank() ? "" : v.label() + " : ") + v.raw();
        }
    }

    // ── Valeurs attendues ────────────────────────────────────────────────

    public List<ExpectedValue> expectedValues(String correction, String subject) {
        if (correction == null || correction.isBlank()) return List.of();
        List<ExpectedValue> fromTable = fromAnswerColumn(correction);
        if (!fromTable.isEmpty()) return fromTable;

        List<Double> subjectNumbers = subject == null ? List.of() : numbers(subject);
        List<ExpectedValue> values = new ArrayList<>();
        String cleaned = ENUMERATOR.matcher(TOLERANCE_TEXT.matcher(correction).replaceAll(" ")).replaceAll(" ");
        Matcher m = NUMBER.matcher(cleaned);
        while (m.find()) {
            Double value = parse(m.group());
            if (value == null) continue;
            if (subjectNumbers.stream().anyMatch(s -> close(s, value, 1e-9))) continue; // donnée de l'énoncé
            if (values.stream().anyMatch(v -> close(v.value(), value, 1e-9))) continue;  // déjà retenue
            values.add(new ExpectedValue(null, value, DEFAULT_RELATIVE_TOLERANCE, m.group().trim()));
        }
        return values;
    }

    /** Tableau de correction : on lit la colonne « Réponse attendue » (et « Tolérance » si présente). */
    private List<ExpectedValue> fromAnswerColumn(String correction) {
        List<ExpectedValue> values = new ArrayList<>();
        String[] lines = correction.split("\\R");
        int answerCol = -1, toleranceCol = -1;
        for (String line : lines) {
            if (!line.contains("\t")) { answerCol = -1; continue; }
            String[] cells = line.split("\t", -1);
            int headerAnswer = -1, headerTolerance = -1;
            for (int i = 0; i < cells.length; i++) {
                String h = normalize(cells[i]);
                if (headerAnswer < 0 && (h.contains("reponse attendue") || h.contains("resultat attendu")
                        || h.equals("reponse") || h.equals("resultat") || h.equals("solution") || h.contains("corrige"))) {
                    headerAnswer = i;
                }
                if (h.startsWith("tolerance")) headerTolerance = i;
            }
            if (headerAnswer >= 0 && NUMBER.matcher(cells[headerAnswer]).results().findAny().isEmpty()) {
                answerCol = headerAnswer; toleranceCol = headerTolerance;
                continue;
            }
            if (answerCol < 0 || answerCol >= cells.length) continue;
            double tolerance = DEFAULT_RELATIVE_TOLERANCE;
            if (toleranceCol >= 0 && toleranceCol < cells.length) {
                Matcher t = NUMBER.matcher(cells[toleranceCol]);
                if (t.find()) {
                    Double pct = parse(t.group());
                    if (pct != null) tolerance = cells[toleranceCol].contains("%") ? pct / 100.0 : pct;
                }
            }
            String label = cells[0].trim().isEmpty() ? null
                : (cells[0].trim().matches("\\d{1,3}") ? "Q" + cells[0].trim() : cells[0].trim());
            Matcher m = NUMBER.matcher(cells[answerCol]);
            while (m.find()) {
                Double value = parse(m.group());
                if (value != null) values.add(new ExpectedValue(label, value, tolerance, cells[answerCol].trim()));
            }
        }
        return values;
    }

    // ── Comparaison ──────────────────────────────────────────────────────

    public Result compare(List<ExpectedValue> expected, String studentText) {
        List<Double> submitted = studentText == null ? List.of() : numbers(studentText);
        List<ExpectedValue> found = new ArrayList<>();
        List<ExpectedValue> missing = new ArrayList<>();
        for (ExpectedValue e : expected) {
            boolean ok = submitted.stream().anyMatch(a -> close(e.value(), a, e.relativeTolerance()));
            (ok ? found : missing).add(e);
        }
        return new Result(found.size(), expected.size(), found, missing);
    }

    public List<Double> numbers(String text) {
        List<Double> values = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            Double value = parse(m.group());
            if (value != null) values.add(value);
        }
        return values;
    }

    Double parse(String raw) {
        String s = raw.trim().replaceAll("[ \\u00a0\\u202f]", "");
        // « 1.250.000 » ou « 1.250,5 » : points de milliers
        if (s.matches("-?\\d{1,3}(\\.\\d{3})+(,\\d+)?")) s = s.replace(".", "");
        s = s.replace(',', '.');
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean close(double expected, double actual, double relativeTolerance) {
        return Math.abs(expected - actual) <= Math.max(0.005, Math.abs(expected) * relativeTolerance);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
