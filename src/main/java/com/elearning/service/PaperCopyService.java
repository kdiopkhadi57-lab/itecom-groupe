package com.elearning.service;

import com.elearning.entity.Qcm;
import com.elearning.entity.QcmPassage;
import com.elearning.entity.QcmQuestion;
import com.elearning.repository.QcmPassageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import jakarta.annotation.PreDestroy;

/**
 * Copie papier d'un devoir : une ou plusieurs pages (photos ou PDF), lues ensemble par l'OCR.
 *
 * La lecture par l'IA peut durer une minute : elle se fait hors transaction, entre deux accès
 * courts à la base (enregistrement des pages, puis des valeurs lues), pour ne pas monopoliser
 * une connexion pendant l'appel quand toute une classe envoie ses copies en même temps.
 * Ces méthodes doivent donc être appelées hors d'un contexte de persistance ouvert.
 */
@Service
@Slf4j
public class PaperCopyService {

    public static final int MAX_PAGES = 10;
    private static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

    public record Page(String url, String filename, String contentType) {}

    public record Upload(byte[] data, String contentType, String filename) {}

    /** Pages de la copie, valeurs lues par ligne de grille (idQuestion → idLigne → valeur), avertissement éventuel. */
    public record Outcome(List<Page> pages, Map<String, Map<String, String>> readValues, String warning) {}

    /** Envoi refusé, avec le code HTTP et le message à afficher à l'étudiant. */
    public static class RefusedException extends RuntimeException {
        private final int status;

        public RefusedException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() { return status; }
    }

    /** Issue d'un traitement asynchrone : le résultat, ou le refus à renvoyer à l'étudiant. */
    public record Result(Outcome outcome, RefusedException error) {}

    /** Ce qu'il faut pour lire la copie sans revenir à la base pendant l'appel à l'IA. */
    private record Snapshot(Long passageId, List<Page> pages, List<QcmCaseOcrGradingService.RowToRead> rows,
                            boolean hasPaperCase, QcmCaseOcrGradingService.GradingPrompt grading) {}

    private final QcmPassageRepository passageRepo;
    private final FileStorageService fileStorage;
    private final QcmCaseOcrGradingService ocr;
    private final CorrectionGridService gridService;
    private final QcmSubmissionService submissionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    /**
     * Threads de lecture des copies. Exécuter le traitement hors du thread de la requête évite le contexte
     * JPA ouvert pour la vue, qui garderait la connexion à la base pendant tout l'appel à l'IA ; le pool
     * limite aussi le nombre de lectures simultanées quand toute une classe rend sa copie.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "copie-ocr");
        thread.setDaemon(true);
        return thread;
    });

    public PaperCopyService(QcmPassageRepository passageRepo, FileStorageService fileStorage,
                            QcmCaseOcrGradingService ocr, CorrectionGridService gridService,
                            QcmSubmissionService submissionService, ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.passageRepo = passageRepo;
        this.fileStorage = fileStorage;
        this.ocr = ocr;
        this.gridService = gridService;
        this.submissionService = submissionService;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Exécute un traitement de copie sur le pool dédié ; les erreurs deviennent un refus avec message. */
    public CompletableFuture<Result> async(Supplier<Outcome> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new Result(action.get(), null);
            } catch (RefusedException e) {
                return new Result(null, e);
            } catch (Exception e) {
                log.error("Traitement de la copie papier impossible : {}", e.getMessage(), e);
                return new Result(null, new RefusedException(500, "Impossible d'enregistrer la copie."));
            }
        }, executor);
    }

    // ── Pages ──────────────────────────────────────────────────────────────

    /** Pages de la copie ; une copie envoyée avant le multi-pages n'a que paperCorrectionUrl. */
    public List<Page> pages(QcmPassage passage) {
        if (passage.getPaperCopyPages() != null && !passage.getPaperCopyPages().isBlank()) {
            try {
                return objectMapper.readValue(passage.getPaperCopyPages(), new TypeReference<List<Page>>() {});
            } catch (Exception e) {
                log.warn("Pages de la copie du passage {} illisibles : {}", passage.getId(), e.getMessage());
            }
        }
        if (passage.getPaperCorrectionUrl() == null || passage.getPaperCorrectionUrl().isBlank()) return List.of();
        return List.of(new Page(passage.getPaperCorrectionUrl(), passage.getPaperCorrectionFilename(), null));
    }

    private void setPages(QcmPassage passage, List<Page> pages) {
        try {
            passage.setPaperCopyPages(pages.isEmpty() ? null : objectMapper.writeValueAsString(pages));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        passage.setPaperCorrectionUrl(pages.isEmpty() ? null : pages.get(0).url());
        passage.setPaperCorrectionFilename(pages.isEmpty() ? null : pages.get(0).filename());
    }

    /** Ajoute des pages à la copie de l'étudiant puis relit la copie entière. */
    public Outcome addPages(Long qcmId, Long passageId, String studentEmail, List<Upload> uploads) {
        if (uploads.isEmpty()) throw new RefusedException(400, "Sélectionnez une copie à envoyer.");
        for (Upload upload : uploads) {
            if (!QcmCaseOcrGradingService.isSupported(upload.contentType(), upload.filename())) {
                throw new RefusedException(400, "Format non pris en charge (" + upload.filename()
                    + ") : envoyez une photo JPG, PNG ou WEBP, ou un PDF.");
            }
            if (isPdf(upload) && upload.data().length > MAX_PDF_BYTES) {
                throw new RefusedException(400, "PDF trop volumineux (20 Mo maximum) : envoyez plutôt des photos des pages.");
            }
        }
        Snapshot snapshot = tx.execute(status -> {
            QcmPassage passage = openPassage(qcmId, passageId, studentEmail);
            List<Page> pages = new ArrayList<>(pages(passage));
            if (pages.size() + uploads.size() > MAX_PAGES) {
                throw new RefusedException(400, "Une copie compte au plus " + MAX_PAGES + " pages.");
            }
            for (Upload upload : uploads) {
                try {
                    String url = fileStorage.storeBytes(upload.data(), "devoirs/copies/" + passage.getStudent().getId(), extensionOf(upload));
                    pages.add(new Page(url, upload.filename(), upload.contentType()));
                } catch (IOException e) {
                    throw new RefusedException(500, "Impossible d'enregistrer la copie.");
                }
            }
            setPages(passage, pages);
            passageRepo.save(passage);
            return snapshot(passage);
        });
        return read(snapshot);
    }

    /** Retire une page de la copie puis relit les pages restantes. */
    public Outcome removePage(Long qcmId, Long passageId, String studentEmail, int index) {
        Snapshot snapshot = tx.execute(status -> {
            QcmPassage passage = openPassage(qcmId, passageId, studentEmail);
            List<Page> pages = new ArrayList<>(pages(passage));
            if (index < 0 || index >= pages.size()) throw new RefusedException(404, "Page introuvable.");
            pages.remove(index);
            setPages(passage, pages);
            passageRepo.save(passage);
            return snapshot(passage);
        });
        return read(snapshot);
    }

    private QcmPassage openPassage(Long qcmId, Long passageId, String studentEmail) {
        QcmPassage passage = passageRepo.findById(passageId)
            .orElseThrow(() -> new RefusedException(404, "Devoir introuvable."));
        if (!passage.getQcm().getId().equals(qcmId) || !passage.getStudent().getEmail().equalsIgnoreCase(studentEmail)) {
            throw new RefusedException(403, "Accès interdit.");
        }
        if (Boolean.TRUE.equals(passage.getIsSubmitted())) {
            throw new RefusedException(400, "Ce devoir a déjà été soumis : la copie ne peut plus être modifiée.");
        }
        return passage;
    }

    private Snapshot snapshot(QcmPassage passage) {
        Qcm qcm = passage.getQcm();
        // Lignes de grille à lire sur la copie (clé « idQuestion:idLigne »), sans les chiffres de la correction
        List<QcmCaseOcrGradingService.RowToRead> rows = new ArrayList<>();
        for (QcmQuestion q : qcm.getQuestions()) {
            if (!submissionService.isPaperCase(q)) continue;
            for (CorrectionGridService.GridRow row : gridService.gridFor(q, qcm)) {
                rows.add(new QcmCaseOcrGradingService.RowToRead(q.getId() + ":" + row.id(), row.label(),
                    CorrectionGridService.withoutNumbers(row.question())));
            }
        }
        boolean hasPaperCase = submissionService.hasPaperCase(qcm);
        return new Snapshot(passage.getId(), pages(passage), rows, hasPaperCase,
            hasPaperCase && rows.isEmpty() ? ocr.prepareGrading(qcm) : null);
    }

    // ── Lecture de la copie ────────────────────────────────────────────────

    private Outcome read(Snapshot snapshot) {
        List<QcmCaseOcrGradingService.ScannedFile> files = new ArrayList<>();
        boolean filesRead = true;
        for (Page page : snapshot.pages()) {
            try {
                files.add(new QcmCaseOcrGradingService.ScannedFile(fileStorage.read(page.url()), page.contentType(), page.filename()));
            } catch (IOException e) {
                log.warn("Page {} de la copie du passage {} introuvable : {}", page.url(), snapshot.passageId(), e.getMessage());
                filesRead = false;
            }
        }

        // Appel à l'IA hors transaction
        Map<String, String> answers = Map.of();
        String transcription = null;
        QcmCaseOcrGradingService.GradeResult grade = null;
        boolean read = files.isEmpty() || filesRead;
        if (!files.isEmpty()) {
            if (!snapshot.rows().isEmpty()) {
                QcmCaseOcrGradingService.Extraction extraction = ocr.extractAnswers(snapshot.rows(), files);
                answers = extraction.answers();
                transcription = extraction.transcription();
                read &= extraction.read();
            } else if (!snapshot.hasPaperCase()) {
                transcription = ocr.transcribe(files);
                read &= !transcription.isBlank();
            } else {
                grade = ocr.grade(snapshot.grading(), files);
                transcription = grade.extractedText();
                read &= !transcription.isBlank();
            }
        }

        final Map<String, String> readAnswers = answers;
        final String readText = transcription;
        final QcmCaseOcrGradingService.GradeResult readGrade = grade;
        List<Page> current = tx.execute(status -> {
            QcmPassage passage = passageRepo.findById(snapshot.passageId()).orElseThrow();
            List<Page> pages = pages(passage);
            // Pages modifiées entre-temps (autre envoi en cours) : c'est la lecture la plus récente qui s'enregistre
            if (!Objects.equals(pages, snapshot.pages())) return pages;
            try {
                passage.setExtractedAnswers(readAnswers.isEmpty() ? null : objectMapper.writeValueAsString(readAnswers));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            passage.setOcrExtractedText(readText == null || readText.isBlank() ? null : readText);
            passage.setOcrScore(readGrade == null ? null : readGrade.score());
            passage.setOcrCorrectionNote(readGrade == null ? null : readGrade.comment());
            passageRepo.save(passage);
            // Copie soumise pendant la lecture (fin du temps) : recorrigée avec les valeurs lues
            submissionService.regrade(passage);
            return pages;
        });

        Map<String, Map<String, String>> readValues = new LinkedHashMap<>();
        readAnswers.forEach((key, value) -> {
            int sep = key.indexOf(':');
            if (sep > 0) readValues.computeIfAbsent(key.substring(0, sep), k -> new LinkedHashMap<>())
                .put(key.substring(sep + 1), value);
        });
        String warning = read ? null : "Votre copie est bien jointe, mais elle n'a pas pu être lue automatiquement : "
            + "saisissez vos résultats, ou envoyez des photos plus nettes.";
        return new Outcome(current, readValues, warning);
    }

    private static boolean isPdf(Upload upload) {
        return (upload.contentType() != null && upload.contentType().toLowerCase().contains("pdf"))
            || (upload.filename() != null && upload.filename().toLowerCase().endsWith(".pdf"));
    }

    private static String extensionOf(Upload upload) {
        String name = upload.filename() == null ? "" : upload.filename();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && name.length() - dot <= 6) return name.substring(dot).toLowerCase();
        return isPdf(upload) ? ".pdf" : ".jpg";
    }
}
