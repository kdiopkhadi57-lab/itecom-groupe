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

    /** Consigne de correction préparée à partir du devoir (sans accès à la base pendant l'appel à l'IA). */
    public record GradingPrompt(Long qcmId, String prompt, int maxScore) {}

    public GradingPrompt prepareGrading(Qcm qcm) {
        List<QcmQuestion> caseQuestions = qcm.getQuestions().stream()
            .filter(this::isPaperCase)
            .toList();
        int maxScore = caseQuestions.stream().mapToInt(QcmQuestion::getPoints).sum();
        return new GradingPrompt(qcm.getId(), caseQuestions.isEmpty() ? null : buildPrompt(qcm, caseQuestions), maxScore);
    }

    public GradeResult grade(Qcm qcm, List<ScannedFile> studentFiles) {
        return grade(prepareGrading(qcm), studentFiles);
    }

    public GradeResult grade(GradingPrompt grading, List<ScannedFile> studentFiles) {
        int maxScore = grading.maxScore();
        if (grading.prompt() == null) return new GradeResult(0, 0, "Aucune question papier à corriger.", "");
        if (client == null) return new GradeResult(0, maxScore, "Correction OCR/IA indisponible : correction manuelle requise.", "");

        try {
            List<ContentBlockParam> content = new ArrayList<>();
            content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(grading.prompt()).build()));
            content.addAll(toContentBlocks(studentFiles));
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(12000L)
                .addUserMessageOfBlockParams(content).build();
            Message response = client.messages().create(params);
            String raw = response.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).findFirst().orElse("{}");
            return parse(raw, maxScore);
        } catch (Exception e) {
            log.error("Erreur correction OCR du devoir {}: {}", grading.qcmId(), e.getMessage());
            return new GradeResult(0, maxScore, "La correction automatique a échoué : correction manuelle requise.", "");
        }
    }

    /** Ligne de grille à retrouver sur la copie : clé « idQuestion:idLigne », libellé et énoncé (jamais la réponse). */
    public record RowToRead(String key, String label, String question) {}

    /** {@code read} est faux quand la copie n'a pas pu être lue (appel IA en échec, réponse tronquée…). */
    public record Extraction(Map<String, String> answers, String transcription, boolean read) {}

    /**
     * Lecture de la copie (PDF texte ou scanné, photo) et extraction des résultats de l'étudiant
     * sous forme JSON, une valeur par ligne de la grille de correction.
     */
    public Extraction extractAnswers(List<RowToRead> rows, List<ScannedFile> studentFiles) {
        if (client == null || studentFiles.isEmpty()) return new Extraction(Map.of(), "", false);
        StringBuilder prompt = new StringBuilder("""
            Tu lis la copie d'un étudiant (PDF ou photos, manuscrite ou imprimée ; une copie peut compter plusieurs
            pages, jointes dans l'ordre : lis-les toutes). Lis tout : texte, calculs, tableaux
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
        try {
            List<ContentBlockParam> content = new ArrayList<>();
            content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(prompt.toString()).build()));
            content.addAll(toContentBlocks(studentFiles));
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(16000L)
                .addUserMessageOfBlockParams(content).build();
            Message response = client.messages().create(params);
            if (response.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
                log.warn("Lecture de la copie tronquée (limite de tokens atteinte)");
            }
            String raw = response.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).findFirst().orElse("{}");
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            Map<String, String> answers = new java.util.LinkedHashMap<>();
            node.path("answers").fields().forEachRemaining(e -> {
                if (!e.getValue().isNull() && !e.getValue().asText().isBlank()) answers.put(e.getKey(), e.getValue().asText().trim());
            });
            return new Extraction(answers, node.path("transcription").asText(""), true);
        } catch (Exception e) {
            log.error("Extraction des réponses de la copie impossible : {}", e.getMessage());
            return new Extraction(Map.of(), "", false);
        }
    }

    /** Transcription seule d'une copie scannée (devoir sans question « cas pratique »). */
    public String transcribe(List<ScannedFile> studentFiles) {
        if (client == null || studentFiles.isEmpty()) return "";
        try {
            List<ContentBlockParam> content = new ArrayList<>();
            content.add(ContentBlockParam.ofText(TextBlockParam.builder().text("""
                Transcris fidèlement la copie d'étudiant jointe, toutes les pages dans l'ordre (texte, calculs, tableaux ligne par ligne
                avec les colonnes séparées par « | »). Recopie tous les chiffres exactement comme ils sont écrits.
                N'invente rien : écris [illisible] si nécessaire. Retourne uniquement la transcription.
                """).build()));
            content.addAll(toContentBlocks(studentFiles));
            MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.of(model)).maxTokens(12000L)
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

    /** Formats lisibles par l'OCR : PDF et images JPEG, PNG, WEBP, GIF (pas de HEIC, TIFF…). */
    public static boolean isSupported(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        String name = filename == null ? "" : filename.toLowerCase();
        return type.contains("pdf") || name.endsWith(".pdf") || resolveImageType(type, name) != null;
    }

    /** Taille totale maximale des fichiers envoyés en une requête (limite de l'API : 32 Mo). */
    private static final long MAX_REQUEST_BASE64 = 28L * 1024 * 1024;

    private List<ContentBlockParam> toContentBlocks(List<ScannedFile> files) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        long total = 0;
        for (ScannedFile file : files) {
            ContentBlockParam block = toContentBlock(file);
            total += block.image().flatMap(i -> i.source().base64()).map(b -> (long) b.data().length())
                .or(() -> block.document().flatMap(d -> d.source().base64()).map(b -> (long) b.data().length()))
                .orElse(0L);
            if (total > MAX_REQUEST_BASE64) throw new IllegalArgumentException("copie trop volumineuse pour être lue en une fois");
            blocks.add(block);
        }
        return blocks;
    }

    private ContentBlockParam toContentBlock(ScannedFile file) {
        String type = file.contentType() == null ? "" : file.contentType().toLowerCase();
        String name = file.filename() == null ? "" : file.filename().toLowerCase();
        if (type.contains("pdf") || name.endsWith(".pdf")) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(file.data())).build()).build());
        }
        Base64ImageSource.MediaType mediaType = resolveImageType(type, name);
        byte[] data = file.data();
        byte[] reduced = downscale(data);
        if (reduced != null) {
            data = reduced;
            mediaType = Base64ImageSource.MediaType.IMAGE_JPEG;
        }
        return ContentBlockParam.ofImage(ImageBlockParam.builder()
            .source(Base64ImageSource.builder().data(Base64.getEncoder().encodeToString(data))
                .mediaType(mediaType == null ? Base64ImageSource.MediaType.IMAGE_JPEG : mediaType).build()).build());
    }

    private static Base64ImageSource.MediaType resolveImageType(String type, String name) {
        if (type.contains("png") || name.endsWith(".png")) return Base64ImageSource.MediaType.IMAGE_PNG;
        if (type.contains("webp") || name.endsWith(".webp")) return Base64ImageSource.MediaType.IMAGE_WEBP;
        if (type.contains("gif") || name.endsWith(".gif")) return Base64ImageSource.MediaType.IMAGE_GIF;
        if (type.contains("jpeg") || type.contains("jpg") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return Base64ImageSource.MediaType.IMAGE_JPEG;
        return null;
    }

    /** Poids maximal d'une page image (l'API accepte 5 Mo par image et 32 Mo par requête : une copie compte plusieurs pages). */
    private static final int MAX_IMAGE_BYTES = 1_500_000;
    /** Au-delà, l'API réduit elle-même l'image : inutile d'envoyer plus de pixels. */
    private static final int MAX_IMAGE_SIDE = 2000;

    /**
     * Photo de téléphone trop lourde ou trop grande : réduite et réencodée en JPEG, sinon l'API la
     * refuse et la copie n'est pas lue. Retourne null si l'image peut être envoyée telle quelle.
     */
    private static byte[] downscale(byte[] data) {
        try {
            java.awt.image.BufferedImage source = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (source == null) return null; // format non décodable (WEBP…) : envoyé tel quel
            int side = Math.max(source.getWidth(), source.getHeight());
            if (data.length <= MAX_IMAGE_BYTES && side <= MAX_IMAGE_SIDE) return null;
            double ratio = Math.min(1.0, MAX_IMAGE_SIDE / (double) side);
            int w = Math.max(1, (int) Math.round(source.getWidth() * ratio));
            int h = Math.max(1, (int) Math.round(source.getHeight() * ratio));
            java.awt.image.BufferedImage target = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = target.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, w, h, null);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(target, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Réduction de l'image de la copie impossible : {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(Qcm qcm, List<QcmQuestion> questions) {
        StringBuilder prompt = new StringBuilder("""
            Tu es un correcteur expert pour une école supérieure. Les fichiers joints sont les pages manuscrites ou imprimées de la réponse d'un étudiant, dans l'ordre.
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
