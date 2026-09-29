package com.elearning.service;

import com.elearning.entity.Qcm;
import com.elearning.entity.QcmQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grille de correction ligne par ligne.
 *
 * La correction du professeur est convertie en grille JSON (une ligne = un résultat attendu, avec sa
 * tolérance et ses points). Les réponses de l'étudiant — saisies ligne par ligne, ou extraites de sa
 * copie PDF / image par OCR sous la forme {identifiant de ligne → valeur} — sont comparées ligne à ligne.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionGridService {

    /** Une ligne de la grille : identifiant stable (Q1, Q2…), libellé, énoncé, valeur attendue, tolérance, points. */
    public record GridRow(String id, String label, String question, double expected, String expectedRaw,
                          double tolerance, double points) {}

    /**
     * Résultat d'une ligne : valeur de l'étudiant, provenance (saisie / copie / texte), points obtenus.
     * {@code scannedValue} est la valeur lue sur la copie ; {@code conflict} signale qu'elle diffère de la
     * valeur saisie (c'est la saisie, vérifiée par l'étudiant, qui est notée).
     */
    public record RowResult(String id, String label, String question, String expectedRaw, String studentValue,
                            String source, boolean correct, double points, double maxPoints,
                            String scannedValue, boolean conflict) {}

    public record GridResult(List<RowResult> rows, double earned, double total) {
        public double ratio() { return total <= 0 ? 0 : earned / total; }

        public long conflicts() { return rows.stream().filter(RowResult::conflict).count(); }

        public String report() {
            long ok = rows.stream().filter(RowResult::correct).count();
            StringBuilder sb = new StringBuilder("Correction ligne par ligne : ").append(ok).append("/").append(rows.size())
                .append(" ligne(s) juste(s), ").append(format(earned)).append("/").append(format(total)).append(" point(s).");
            for (RowResult r : rows) {
                sb.append("\n").append(r.correct() ? "✓ " : "✗ ").append(r.label()).append(" : attendu ").append(r.expectedRaw())
                    .append(" — réponse : ").append(r.studentValue() == null ? "(aucune)" : r.studentValue());
                if (r.source() != null) sb.append(" [").append(r.source()).append("]");
                if (r.conflict()) sb.append(" ⚠ copie : ").append(r.scannedValue());
            }
            if (conflicts() > 0) {
                sb.append("\n⚠ ").append(conflicts()).append(" ligne(s) où la valeur saisie diffère de la copie : vérifiez la copie.");
            }
            return sb.toString();
        }
    }

    public static final String SOURCE_TYPED = "saisie";
    public static final String SOURCE_SCAN = "copie scannée";
    public static final String SOURCE_TEXT = "retrouvé dans le texte";

    private static final Pattern NUMBER = Pattern.compile(
        "(?<![\\d.,])-?(?:\\d{1,3}(?:[ \\u00a0\\u202f.]\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?)(?![\\d])");

    private final CorrectionComparisonService comparisonService;
    private final ObjectMapper objectMapper;

    // ── Construction de la grille ─────────────────────────────────────────

    /** Grille d'une question « cas pratique » : celle enregistrée (validée par le professeur), sinon calculée. */
    public List<GridRow> gridFor(QcmQuestion question, Qcm qcm) {
        List<GridRow> stored = parseGrid(question.getCorrectionGrid());
        if (stored != null) return stored;
        return buildGrid(correctionTextFor(question, qcm), subjectTextFor(question, qcm), question.getPoints());
    }

    public List<GridRow> parseGrid(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<GridRow>>() {});
        } catch (Exception e) {
            log.warn("Grille de correction illisible : {}", e.getMessage());
            return null;
        }
    }

    public String toJson(List<GridRow> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String PLACEHOLDER_ANSWER = "Référence de correction fournie par le professeur.";

    public String correctionTextFor(QcmQuestion question, Qcm qcm) {
        return firstNonBlank(PLACEHOLDER_ANSWER.equals(question.getExpectedAnswer()) ? null : question.getExpectedAnswer(),
            question.getCorrectionData(), qcm.getCorrectionText());
    }

    private String subjectTextFor(QcmQuestion question, Qcm qcm) {
        return firstNonBlank(qcm.getSubjectText(), question.getQuestionText());
    }

    /**
     * Tableau de correction : une ligne par valeur de la colonne « Réponse attendue », avec l'énoncé,
     * la tolérance et les points de la ligne s'ils sont présents. Sans tableau : une ligne par chiffre
     * de la correction absent du sujet. Les points sont répartis également s'ils ne sont pas précisés.
     */
    public List<GridRow> buildGrid(String correction, String subject, Integer questionPoints) {
        if (correction == null || correction.isBlank()) return List.of();
        List<GridRow> rows = fromTable(correction);
        if (rows.isEmpty()) {
            int i = 1;
            for (CorrectionComparisonService.ExpectedValue v : comparisonService.expectedValues(correction, subject)) {
                String id = "V" + i++;
                rows.add(new GridRow(id, id, contextLine(correction, v.raw()), v.value(), v.raw(), v.relativeTolerance(), 0));
            }
        }
        // Points non précisés : répartition égale des points de la question
        if (!rows.isEmpty() && rows.stream().allMatch(r -> r.points() <= 0)) {
            double each = (questionPoints != null && questionPoints > 0 ? questionPoints : rows.size()) / (double) rows.size();
            rows = rows.stream().map(r -> new GridRow(r.id(), r.label(), r.question(), r.expected(), r.expectedRaw(),
                r.tolerance(), Math.round(each * 100) / 100.0)).toList();
        }
        return rows;
    }

    private List<GridRow> fromTable(String correction) {
        List<GridRow> rows = new ArrayList<>();
        int answerCol = -1, toleranceCol = -1, pointsCol = -1, questionCol = -1;
        int index = 0;
        Set<String> usedIds = new HashSet<>();
        for (String line : correction.split("\\R")) {
            if (!line.contains("\t")) { answerCol = -1; continue; }
            String[] cells = line.split("\t", -1);
            int a = -1, t = -1, p = -1, q = -1;
            for (int i = 0; i < cells.length; i++) {
                String h = normalize(cells[i]);
                if (a < 0 && (h.contains("reponse attendue") || h.contains("resultat attendu") || h.equals("reponse")
                        || h.equals("resultat") || h.equals("solution") || h.contains("corrige"))) a = i;
                else if (h.startsWith("tolerance")) t = i;
                else if (h.equals("points") || h.equals("point") || h.equals("pts") || h.startsWith("bareme")) p = i;
                else if (q < 0 && (h.contains("question") || h.contains("enonce") || h.contains("intitule") || h.contains("libelle"))) q = i;
            }
            if (a >= 0 && NUMBER.matcher(cells[a]).results().findAny().isEmpty()) {
                answerCol = a; toleranceCol = t; pointsCol = p; questionCol = q;
                continue;
            }
            if (answerCol < 0 || answerCol >= cells.length) continue;

            double tolerance = CorrectionComparisonService.DEFAULT_RELATIVE_TOLERANCE;
            Double pct = firstNumber(cell(cells, toleranceCol));
            if (pct != null) tolerance = cell(cells, toleranceCol).contains("%") ? pct / 100.0 : pct;
            Double rowPoints = firstNumber(cell(cells, pointsCol));

            String first = cells[0].trim();
            String baseId = first.matches("\\d{1,3}") ? "Q" + first
                : (!first.isEmpty() && first.length() <= 12 && answerCol != 0 && questionCol != 0 ? first : "L" + (index + 1));
            String question = questionCol >= 0 ? cell(cells, questionCol) : longestCell(cells, answerCol);

            List<String[]> values = new ArrayList<>();
            Matcher m = NUMBER.matcher(cells[answerCol]);
            while (m.find()) values.add(new String[]{m.group()});
            for (int v = 0; v < values.size(); v++) {
                Double value = comparisonService.parse(values.get(v)[0]);
                if (value == null) continue;
                String id = values.size() > 1 ? baseId + (char) ('a' + v) : baseId;
                while (!usedIds.add(id)) id = id + "'";
                double points = rowPoints != null ? rowPoints / values.size() : 0;
                rows.add(new GridRow(id, id, question, value,
                    values.size() > 1 ? values.get(v)[0].trim() : cells[answerCol].trim(), tolerance, points));
            }
            index++;
        }
        return rows;
    }

    // ── Comparaison ligne par ligne ───────────────────────────────────────

    /**
     * @param typed      valeurs saisies par l'étudiant (identifiant de ligne → valeur)
     * @param scanned    valeurs extraites de la copie PDF / image (identifiant de ligne → valeur)
     * @param freeText   texte libre (rédaction + transcription), utilisé seulement pour une ligne sans valeur
     */
    public GridResult compare(List<GridRow> grid, Map<String, String> typed, Map<String, String> scanned, String freeText) {
        List<RowResult> results = new ArrayList<>();
        double earned = 0, total = 0;
        List<Double> textNumbers = freeText == null ? List.of() : comparisonService.numbers(freeText);
        for (GridRow row : grid) {
            total += row.points();
            String value = valueFor(typed, row.id());
            String source = value != null ? SOURCE_TYPED : null;
            String scannedValue = valueFor(scanned, row.id());
            boolean conflict = value != null && scannedValue != null && !sameValue(row, value, scannedValue);
            if (value == null) {
                value = scannedValue;
                if (value != null) source = SOURCE_SCAN;
            }
            boolean correct;
            if (value != null) {
                correct = comparisonService.numbers(value).stream().anyMatch(a -> close(row, a));
            } else {
                // Ligne sans valeur dédiée : on cherche le résultat dans le texte libre
                correct = textNumbers.stream().anyMatch(a -> close(row, a));
                if (correct) { value = row.expectedRaw(); source = SOURCE_TEXT; }
            }
            if (correct) earned += row.points();
            results.add(new RowResult(row.id(), row.label(), row.question(), row.expectedRaw(), value, source,
                correct, correct ? row.points() : 0, row.points(), scannedValue, conflict));
        }
        return new GridResult(results, Math.round(earned * 100) / 100.0, Math.round(total * 100) / 100.0);
    }

    /**
     * Valeur d'une ligne ; une ligne de correction à plusieurs résultats (Q1a, Q1b) accepte aussi
     * la réponse saisie dans la ligne du sujet (Q1).
     */
    private static String valueFor(Map<String, String> values, String rowId) {
        if (values == null) return null;
        String value = blankToNull(values.get(rowId));
        if (value == null && rowId.matches(".+[a-z]'*")) value = blankToNull(values.get(rowId.replaceAll("[a-z]'*$", "")));
        return value;
    }

    /** Même résultat (à la tolérance de la ligne près), ou même texte quand les valeurs ne sont pas des nombres. */
    private boolean sameValue(GridRow row, String a, String b) {
        List<Double> na = comparisonService.numbers(a);
        List<Double> nb = comparisonService.numbers(b);
        if (na.isEmpty() || nb.isEmpty()) return normalize(a).replaceAll("\\s+", "").equals(normalize(b).replaceAll("\\s+", ""));
        double tolerance = Math.max(0.005, Math.abs(row.expected()) * row.tolerance());
        return na.stream().anyMatch(x -> nb.stream().anyMatch(y -> Math.abs(x - y) <= tolerance));
    }

    /**
     * Énoncé d'une ligne sans ses chiffres, pour guider la lecture de la copie : l'énoncé est tiré de la
     * correction et peut contenir le résultat attendu, que l'OCR risquerait alors de « lire » sur la copie.
     */
    public static String withoutNumbers(String text) {
        if (text == null || text.isBlank()) return "";
        return NUMBER.matcher(text).replaceAll("…").replaceAll("(…[\\s…]*)+", "… ").trim();
    }

    private boolean close(GridRow row, double actual) {
        return Math.abs(row.expected() - actual) <= Math.max(0.005, Math.abs(row.expected()) * row.tolerance());
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    private Double firstNumber(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = NUMBER.matcher(text);
        return m.find() ? comparisonService.parse(m.group()) : null;
    }

    private static String cell(String[] cells, int index) {
        return index >= 0 && index < cells.length ? cells[index].trim() : "";
    }

    private static String longestCell(String[] cells, int exclude) {
        String best = "";
        for (int i = 0; i < cells.length; i++) {
            if (i != exclude && cells[i].trim().length() > best.length()) best = cells[i].trim();
        }
        return best;
    }

    private static String contextLine(String text, String raw) {
        for (String line : text.split("\\R")) {
            if (line.contains(raw.trim())) {
                String t = line.trim();
                return t.length() > 160 ? t.substring(0, 160) + "…" : t;
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.FRANCE, "%.2f", value);
    }
}
