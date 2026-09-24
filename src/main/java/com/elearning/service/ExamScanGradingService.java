package com.elearning.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.elearning.entity.Exam;
import com.elearning.entity.ExamQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Corrige une copie d'examen scannée (photos ou PDF de la copie papier d'un étudiant)
 * en s'appuyant sur la vision de Claude pour lire l'écriture manuscrite et la comparer
 * aux réponses de référence fournies par le professeur.
 */
@Service
@Slf4j
public class ExamScanGradingService {

    @Autowired(required = false)
    private AnthropicClient client;

    @Value("${anthropic.model:claude-opus-4-8}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Nombre maximum de fichiers (pages) acceptés pour une copie scannée. */
    public static final int MAX_FILES = 15;

    public record ScannedFile(byte[] data, String contentType, String filename) {}

    public ExamGradingService.GradingResult gradeScannedCopy(Exam exam, List<ScannedFile> files) {
        if (client == null) {
            log.warn("L'IA n'est pas configurée (anthropic.api-key vide). Correction automatique de la copie scannée désactivée pour l'examen {}.", exam.getId());
            return buildFallbackResult(exam);
        }

        List<ContentBlockParam> content = new ArrayList<>();
        content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(buildPrompt(exam)).build()));
        for (ScannedFile file : files) {
            content.add(toContentBlock(file));
        }

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(8192L)
                .addUserMessageOfBlockParams(content)
                .build();

            Message response = client.messages().create(params);
            String rawText = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("{}");

            return parseResponse(rawText, exam);
        } catch (Exception e) {
            log.error("Erreur lors de la correction IA de la copie scannée pour l'examen {}: {}", exam.getId(), e.getMessage());
            return buildFallbackResult(exam);
        }
    }

    private ContentBlockParam toContentBlock(ScannedFile file) {
        String b64 = Base64.getEncoder().encodeToString(file.data());
        String contentType = file.contentType() != null ? file.contentType().toLowerCase() : "";
        String filename = file.filename() != null ? file.filename().toLowerCase() : "";

        if (contentType.contains("pdf") || filename.endsWith(".pdf")) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(b64).build())
                .build());
        }

        return ContentBlockParam.ofImage(ImageBlockParam.builder()
            .source(Base64ImageSource.builder()
                .data(b64)
                .mediaType(resolveImageMediaType(contentType, filename))
                .build())
            .build());
    }

    private Base64ImageSource.MediaType resolveImageMediaType(String contentType, String filename) {
        if (contentType.contains("png") || filename.endsWith(".png")) {
            return Base64ImageSource.MediaType.IMAGE_PNG;
        }
        if (contentType.contains("webp") || filename.endsWith(".webp")) {
            return Base64ImageSource.MediaType.IMAGE_WEBP;
        }
        if (contentType.contains("gif") || filename.endsWith(".gif")) {
            return Base64ImageSource.MediaType.IMAGE_GIF;
        }
        return Base64ImageSource.MediaType.IMAGE_JPEG;
    }

    private String buildPrompt(Exam exam) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            Tu es un correcteur d'examen expert et minutieux. On te fournit la copie scannée
            (une ou plusieurs photos ou pages PDF, dans l'ordre) d'un étudiant ainsi que les
            questions de l'examen et leurs réponses de référence (corrigé du professeur).

            CONSIGNES IMPORTANTES POUR LA LECTURE DE LA COPIE:
            - Lis très attentivement l'intégralité de chaque image/page, y compris les marges,
              annotations, schémas et ratures.
            - L'écriture manuscrite peut être difficile à lire selon l'élève (écriture rapide,
              floue, peu soignée, ratures, abréviations). Prends ton temps, examine les détails
              de chaque mot et déduis le sens le plus probable à partir du contexte de la question.
            - Pour chaque question du sujet, retrouve la portion de la copie qui y répond
              (les numéros de question écrits par l'étudiant peuvent être désorganisés,
              manquants ou ne pas correspondre exactement à l'ordre du sujet : utilise le
              contenu pour faire correspondre chaque réponse à la bonne question).
            - Transcris fidèlement ce que l'étudiant a écrit pour chaque question dans le champ
              "transcribedAnswer", en conservant le sens même si l'orthographe est incertaine.
              Si un passage reste réellement illisible malgré une lecture attentive, indique-le
              entre crochets, par exemple "[illisible: ... ]", sans inventer de contenu.
              Si l'étudiant n'a clairement pas répondu à une question, indique "(pas de réponse)".

            TITRE DE L'EXAMEN: %s

            QUESTIONS ET RÉPONSES DE RÉFÉRENCE (corrigé du professeur):
            """.formatted(exam.getTitle()));

        for (ExamQuestion q : exam.getQuestions()) {
            sb.append("Q").append(q.getOrderIndex()).append(" [ID:").append(q.getId()).append("] (max: ")
              .append(q.getMaxScore()).append(" pts): ").append(q.getQuestionText()).append("\n");
            sb.append("Réponse de référence: ").append(q.getReferenceAnswer()).append("\n\n");
        }

        sb.append("""

            Pour CHAQUE question listée ci-dessus, compare la réponse transcrite de l'étudiant
            à la réponse de référence et attribue une note.

            Réponds UNIQUEMENT avec un JSON valide dans ce format exact (sans markdown):
            {
              "answers": [
                {"questionId": <id>, "transcribedAnswer": "<texte écrit par l'étudiant pour cette question>", "score": <note obtenue>, "comment": "<commentaire constructif en français>"},
                ...
              ],
              "summary": "<résumé global de la performance de l'étudiant en français, 2-3 phrases>"
            }

            Règles de correction:
            - Une entrée par question listée ci-dessus, dans le même ordre
            - La note doit être un nombre entre 0 et le maximum de la question
            - Si la réponse est illisible ou absente, mets une note de 0 et explique-le dans le commentaire
            - Sois juste mais bienveillant dans les commentaires
            - Donne des explications constructives pour aider l'étudiant à progresser
            - Si la réponse est correcte, dis-le clairement, même si la formulation diffère du corrigé
            """);

        return sb.toString();
    }

    private ExamGradingService.GradingResult parseResponse(String rawText, Exam exam) {
        try {
            String jsonText = rawText.trim();
            int start = jsonText.indexOf('{');
            int end = jsonText.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonText = jsonText.substring(start, end + 1);
            }

            JsonNode root = objectMapper.readTree(jsonText);
            JsonNode answersNode = root.get("answers");
            String summary = root.has("summary") ? root.get("summary").asText() : "";

            List<ExamGradingService.AnswerScore> scores = new ArrayList<>();
            if (answersNode != null && answersNode.isArray()) {
                for (JsonNode a : answersNode) {
                    scores.add(new ExamGradingService.AnswerScore(
                        a.get("questionId").asLong(),
                        a.get("score").asDouble(),
                        a.has("comment") ? a.get("comment").asText() : "",
                        a.has("transcribedAnswer") ? a.get("transcribedAnswer").asText() : null
                    ));
                }
            }

            if (scores.isEmpty()) {
                return buildFallbackResult(exam);
            }

            return new ExamGradingService.GradingResult(scores, summary);
        } catch (Exception e) {
            log.error("Erreur parsing réponse IA (copie scannée): {}", e.getMessage());
            return buildFallbackResult(exam);
        }
    }

    private ExamGradingService.GradingResult buildFallbackResult(Exam exam) {
        List<ExamGradingService.AnswerScore> scores = exam.getQuestions().stream()
            .map(q -> new ExamGradingService.AnswerScore(q.getId(), 0.0, "Correction manuelle requise", null))
            .toList();
        return new ExamGradingService.GradingResult(scores,
            "La lecture et la correction automatique de la copie scannée ont échoué. Une révision manuelle est nécessaire.");
    }
}
