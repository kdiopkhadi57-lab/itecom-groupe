package com.elearning.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.elearning.entity.Qcm;
import com.elearning.entity.QcmQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class QcmCaseOcrGradingService {
    @Autowired(required = false)
    private AnthropicClient client;

    @Value("${anthropic.model:claude-opus-4-8}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ScannedFile(byte[] data, String contentType, String filename) {}
    public record GradeResult(int score, int maxScore, String comment, String extractedText) {}

    public GradeResult grade(Qcm qcm, List<ScannedFile> studentFiles) {
        List<QcmQuestion> caseQuestions = qcm.getQuestions().stream()
            .filter(this::isPaperCase)
            .toList();
        int maxScore = caseQuestions.stream().mapToInt(QcmQuestion::getPoints).sum();
        if (caseQuestions.isEmpty()) return new GradeResult(0, 0, "Aucune question papier à corriger.", "");
        if (client == null) return new GradeResult(0, maxScore, "Correction OCR/IA indisponible : correction manuelle requise.", "");

        List<ContentBlockParam> content = new ArrayList<>();
        content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(buildPrompt(qcm, caseQuestions)).build()));
        for (ScannedFile file : studentFiles) content.add(toContentBlock(file));

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(10000L)
                .addUserMessageOfBlockParams(content).build();
            Message response = client.messages().create(params);
            String raw = response.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).findFirst().orElse("{}");
            return parse(raw, maxScore);
        } catch (Exception e) {
            log.error("Erreur correction OCR du devoir {}: {}", qcm.getId(), e.getMessage());
            return new GradeResult(0, maxScore, "La correction automatique a échoué : correction manuelle requise.", "");
        }
    }

    /** Ligne de grille à retrouver sur la copie : clé « idQuestion:idLigne », libellé et énoncé (jamais la réponse). */
    public record RowToRead(String key, String label, String question) {}

    public record Extraction(Map<String, String> answers, String transcription) {}

    /**
     * Lecture de la copie (PDF texte ou scanné, photo) et extraction des résultats de l'étudiant
     * sous forme JSON, une valeur par ligne de la grille de correction.
     */
    public Extraction extractAnswers(List<RowToRead> rows, List<ScannedFile> studentFiles) {
        if (client == null || studentFiles.isEmpty()) return new Extraction(Map.of(), "");
        StringBuilder prompt = new StringBuilder("""
            Tu lis la copie d'un étudiant (PDF ou photo, manuscrite ou imprimée). Lis tout : texte, calculs, tableaux
            (chaque ligne et chaque colonne), annotations. Pour chaque ligne demandée ci-dessous, recopie le RÉSULTAT FINAL
            que l'étudiant a donné pour cette ligne, exactement comme il l'a écrit (chiffres, séparateurs, unité).
            Si l'étudiant n'a pas répondu à une ligne, ou si c'est illisible, mets null. N'invente jamais une valeur,
            ne corrige pas l'étudiant et ne calcule rien toi-même.

            LIGNES À RETROUVER :
            """);
        for (RowToRead r : rows) {
            prompt.append("- ").append(r.key()).append(" (").append(r.label()).append(")")
                .append(r.question() == null || r.question().isBlank() ? "" : " : " + r.question()).append("\n");
        }
        prompt.append("""

            Réponds uniquement avec ce JSON :
            {"answers": {"<clé>": "<valeur écrite par l'étudiant ou null>", ...}, "transcription": "<transcription fidèle de la copie, tableaux ligne par ligne avec des | >"}
            """);
        List<ContentBlockParam> content = new ArrayList<>();
        content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(prompt.toString()).build()));
        for (ScannedFile file : studentFiles) content.add(toContentBlock(file));
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(12000L)
                .addUserMessageOfBlockParams(content).build();
            Message response = client.messages().create(params);
            String raw = response.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).findFirst().orElse("{}");
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            Map<String, String> answers = new java.util.LinkedHashMap<>();
            node.path("answers").fields().forEachRemaining(e -> {
                if (!e.getValue().isNull() && !e.getValue().asText().isBlank()) answers.put(e.getKey(), e.getValue().asText().trim());
            });
            return new Extraction(answers, node.path("transcription").asText(""));
        } catch (Exception e) {
            log.error("Extraction des réponses de la copie impossible : {}", e.getMessage());
            return new Extraction(Map.of(), "");
        }
    }

    /** Transcription seule d'une copie scannée (devoir sans question « cas pratique »). */
    public String transcribe(List<ScannedFile> studentFiles) {
        if (client == null || studentFiles.isEmpty()) return "";
        List<ContentBlockParam> content = new ArrayList<>();
        content.add(ContentBlockParam.ofText(TextBlockParam.builder().text("""
            Transcris fidèlement la copie d'étudiant jointe (texte, calculs, tableaux ligne par ligne avec les colonnes séparées par « | »).
            Recopie tous les chiffres exactement comme ils sont écrits. N'invente rien : écris [illisible] si nécessaire.
            Retourne uniquement la transcription.
            """).build()));
        for (ScannedFile file : studentFiles) content.add(toContentBlock(file));
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(10000L)
                .addUserMessageOfBlockParams(content).build();
            Message response = client.messages().create(params);
            return response.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).findFirst().orElse("");
        } catch (Exception e) {
            log.error("Erreur de transcription de copie : {}", e.getMessage());
            return "";
        }
    }

    /** Correction du cas pratique à partir de la réponse rédigée dans la zone de saisie (sans copie scannée). */
    public GradeResult gradeTypedAnswers(Qcm qcm, Map<Long, String> answersByQuestionId) {
        List<QcmQuestion> caseQuestions = qcm.getQuestions().stream()
            .filter(this::isPaperCase)
            .toList();
        int maxScore = caseQuestions.stream().mapToInt(QcmQuestion::getPoints).sum();
        if (caseQuestions.isEmpty()) return new GradeResult(0, 0, "Aucun cas pratique à corriger.", "");
        if (client == null) return new GradeResult(0, maxScore, "Correction IA indisponible : correction manuelle requise.", "");

        StringBuilder prompt = new StringBuilder(buildPrompt(qcm, caseQuestions)
            .replace("Les fichiers joints sont les pages manuscrites ou imprimées de la réponse d'un étudiant.",
                "La réponse de l'étudiant a été saisie au clavier et figure ci-dessous (aucun fichier joint).")
            .replace("Utilise une lecture OCR/vision complète : lis le texte hors tableau, les tableaux, cellules, colonnes, lignes, unités, signes mathématiques, ratures et annotations.",
                "Lis attentivement le texte saisi, y compris les tableaux éventuellement tapés en texte."));
        prompt.append("\nREPONSE SAISIE PAR L'ETUDIANT:\n");
        for (QcmQuestion q : caseQuestions) {
            String answer = answersByQuestionId.get(q.getId());
            prompt.append("\nQUESTION ID ").append(q.getId()).append(":\n")
                .append(answer == null || answer.isBlank() ? "(pas de réponse)" : answer).append("\n");
        }

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(10000L)
                .addUserMessage(prompt.toString()).build();
            Message response = client.messages().create(params);
            String raw = response.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).findFirst().orElse("{}");
            return parse(raw, maxScore);
        } catch (Exception e) {
            log.error("Erreur correction du cas pratique saisi, devoir {}: {}", qcm.getId(), e.getMessage());
            return new GradeResult(0, maxScore, "La correction automatique a échoué : correction manuelle requise.", "");
        }
    }

    private boolean isPaperCase(QcmQuestion question) {
        String type = question.getQuestionType();
        return "CASE".equalsIgnoreCase(type) || ("PRACTICAL".equalsIgnoreCase(type)
            && question.getCaseScenario() != null && !question.getCaseScenario().isBlank());
    }

    private ContentBlockParam toContentBlock(ScannedFile file) {
        String b64 = Base64.getEncoder().encodeToString(file.data());
        String type = file.contentType() == null ? "" : file.contentType().toLowerCase();
        String name = file.filename() == null ? "" : file.filename().toLowerCase();
        if (type.contains("pdf") || name.endsWith(".pdf")) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(b64).build()).build());
        }
        return ContentBlockParam.ofImage(ImageBlockParam.builder()
            .source(Base64ImageSource.builder().data(b64).mediaType(resolveImageType(type, name)).build()).build());
    }

    private Base64ImageSource.MediaType resolveImageType(String type, String name) {
        if (type.contains("png") || name.endsWith(".png")) return Base64ImageSource.MediaType.IMAGE_PNG;
        if (type.contains("webp") || name.endsWith(".webp")) return Base64ImageSource.MediaType.IMAGE_WEBP;
        return Base64ImageSource.MediaType.IMAGE_JPEG;
    }

    private String buildPrompt(Qcm qcm, List<QcmQuestion> questions) {
        StringBuilder prompt = new StringBuilder("""
            Tu es un correcteur expert pour une école supérieure. Les fichiers joints sont les pages manuscrites ou imprimées de la réponse d'un étudiant.
            Utilise une lecture OCR/vision complète : lis le texte hors tableau, les tableaux, cellules, colonnes, lignes, unités, signes mathématiques, ratures et annotations.
            Les domaines peuvent être mathématiques, français, droit, comptabilité, gestion ou toute autre matière. Adapte les critères au domaine.
            Reconstitue les réponses même si elles sont réparties entre texte libre et tableaux. N'invente jamais un contenu illisible.

            DEVOIR: %s
            CORRIGE PROFESSEUR:
            """.formatted(qcm.getTitle()));
        for (QcmQuestion q : questions) {
            prompt.append("\nQUESTION ID ").append(q.getId()).append(" (" ).append(q.getPoints()).append(" points)\n")
                .append("Enoncé: ").append(q.getQuestionText()).append("\n")
                .append(q.getCaseScenario() == null || q.getCaseScenario().isBlank() ? "" : "Cas: " + q.getCaseScenario() + "\n")
                .append("Réponse attendue/grille: ").append(q.getExpectedAnswer()).append("\n")
                .append("Correction structurée: ").append(q.getCorrectionData()).append("\n");
        }
        return prompt.append("""

            Retourne uniquement un JSON valide :
            {"score": <entier total>, "extractedText": "<transcription structurée du texte et des tableaux>", "comment": "<commentaire détaillé en français>"}
            Recopie dans extractedText tous les chiffres exactement comme l'étudiant les a écrits.
            Note selon la justesse des calculs, du raisonnement, des concepts juridiques/linguistiques et des unités. Compare les tableaux cellule par cellule quand ils existent.
            Le score total doit être compris entre 0 et le total des points des questions papier.
            """).toString();
    }

    private GradeResult parse(String raw, int maxScore) {
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            int score = Math.max(0, Math.min(maxScore, node.path("score").asInt(0)));
            return new GradeResult(score, maxScore, node.path("comment").asText("Correction automatique effectuée."), node.path("extractedText").asText(""));
        } catch (Exception e) {
            return new GradeResult(0, maxScore, "Réponse OCR illisible ou format de correction invalide.", "");
        }
    }
}
