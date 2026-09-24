package com.elearning.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamQuestionExtractionService {

    @Autowired(required = false)
    private AnthropicClient client;

    @Value("${anthropic.model:claude-opus-4-8}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CHARS = 30_000;

    public record ExtractedQuestion(String questionText, String referenceAnswer, Integer maxScore) {}

    public List<ExtractedQuestion> extractQuestions(String examText, String correctionText) {
        if (examText == null || examText.isBlank()) {
            throw new IllegalStateException("Le document d'examen est vide ou illisible.");
        }
        if (client == null) {
            throw new IllegalStateException("L'IA n'est pas configurée. Définissez ANTHROPIC_API_KEY pour activer l'extraction automatique des questions.");
        }

        String effectiveCorrection = (correctionText == null || correctionText.isBlank()) ? examText : correctionText;
        String prompt = buildPrompt(examText, effectiveCorrection);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(8192L)
                .addUserMessage(prompt)
                .build();

            Message response = client.messages().create(params);
            String rawText = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("[]");

            List<ExtractedQuestion> questions = parseResponse(rawText);
            if (questions.isEmpty()) {
                throw new IllegalStateException("Aucune question n'a pu être extraite des fichiers fournis.");
            }
            return questions;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur extraction des questions: {}", e.getMessage());
            throw new IllegalStateException("Erreur lors de l'analyse des fichiers par l'IA: " + e.getMessage());
        }
    }

    private String buildPrompt(String examText, String correctionText) {
        return """
            Tu es un assistant qui structure un examen à partir de deux documents : le sujet d'examen et son corrigé.

            SUJET D'EXAMEN:
            %s

            CORRIGÉ / RÉPONSES DE RÉFÉRENCE:
            %s

            Analyse ces documents et extrait la liste des questions de l'examen avec, pour chacune,
            sa réponse de référence correspondante (issue du corrigé) et son barème.

            Réponds UNIQUEMENT avec un JSON valide (sans markdown, sans texte autour), sous la forme:
            [
              {"questionText": "<énoncé complet de la question>", "referenceAnswer": "<réponse de référence>", "maxScore": <entier>},
              ...
            ]

            Règles:
            - Une entrée par question identifiée dans le sujet, dans l'ordre du document
            - La réponse de référence doit provenir du corrigé et être complète
            - Si le barème n'est pas explicite dans les documents, répartis équitablement les points sur un total de 20
            - maxScore doit être un entier positif
            """.formatted(truncate(examText), truncate(correctionText));
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private List<ExtractedQuestion> parseResponse(String rawText) throws Exception {
        String jsonText = rawText.trim();
        int start = jsonText.indexOf('[');
        int end = jsonText.lastIndexOf(']');
        if (start >= 0 && end > start) {
            jsonText = jsonText.substring(start, end + 1);
        }

        JsonNode root = objectMapper.readTree(jsonText);
        List<ExtractedQuestion> result = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                result.add(new ExtractedQuestion(
                    node.get("questionText").asText(),
                    node.get("referenceAnswer").asText(),
                    node.has("maxScore") ? node.get("maxScore").asInt() : 10
                ));
            }
        }
        return result;
    }
}
