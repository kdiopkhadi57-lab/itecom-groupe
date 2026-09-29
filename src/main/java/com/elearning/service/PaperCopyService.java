package com.elearning.service;

import com.elearning.entity.QcmPassage;
import com.elearning.repository.QcmPassageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Copie papier d'un devoir : une ou plusieurs pages (photos ou PDF), transcrites ensemble par l'OCR.
 * La transcription sert ensuite à la correction du cas pratique, avec la zone de saisie.
 *
 * La lecture par l'IA peut durer une minute : elle se fait hors transaction, entre deux accès
 * courts à la base (enregistrement des pages, puis du texte lu), pour ne pas monopoliser une
 * connexion pendant l'appel quand toute une classe envoie ses copies en même temps.
 */
@Service
@Slf4j
public class PaperCopyService {

    public static final int MAX_PAGES = 10;
    private static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

    public record Page(String url, String filename, String contentType) {}

    public record Upload(byte[] data, String contentType, String filename) {}

    /** Pages de la copie, texte lu sur la copie (à vérifier par l'étudiant), avertissement éventuel. */
    public record Outcome(List<Page> pages, String transcription, String warning) {}

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
    private record Snapshot(Long passageId, List<Page> pages) {}

    private final QcmPassageRepository passageRepo;
    private final FileStorageService fileStorage;
    private final QcmCaseOcrGradingService ocr;
    private final CaseGradingService caseGradingService;
    private final AiTaskExecutor executor;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public PaperCopyService(QcmPassageRepository passageRepo, FileStorageService fileStorage,
                            QcmCaseOcrGradingService ocr, CaseGradingService caseGradingService,
                            AiTaskExecutor executor, ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.passageRepo = passageRepo;
        this.fileStorage = fileStorage;
        this.ocr = ocr;
        this.caseGradingService = caseGradingService;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Exécute un traitement de copie hors du thread de la requête ; les erreurs deviennent un refus avec message. */
    public CompletableFuture<Result> async(Supplier<Outcome> action) {
        return executor.run(() -> {
            try {
                return new Result(action.get(), null);
            } catch (RefusedException e) {
                return new Result(null, e);
            } catch (Exception e) {
                log.error("Traitement de la copie papier impossible : {}", e.getMessage(), e);
                return new Result(null, new RefusedException(500, "Impossible d'enregistrer la copie."));
            }
        });
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
        return new Snapshot(passage.getId(), pages(passage));
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

        // Transcription de toutes les pages par l'IA, hors transaction
        String transcription = files.isEmpty() ? "" : ocr.transcribe(files);
        boolean read = files.isEmpty() || (filesRead && !transcription.isBlank());

        Boolean submitted = tx.execute(status -> {
            QcmPassage passage = passageRepo.findById(snapshot.passageId()).orElseThrow();
            // Pages modifiées entre-temps (autre envoi en cours) : c'est la lecture la plus récente qui s'enregistre
            if (!Objects.equals(pages(passage), snapshot.pages())) return null;
            passage.setOcrExtractedText(transcription.isBlank() ? null : transcription);
            passageRepo.save(passage);
            return Boolean.TRUE.equals(passage.getIsSubmitted());
        });
        // Copie soumise pendant la lecture (fin du temps) : recorrigée avec le texte lu
        if (Boolean.TRUE.equals(submitted)) caseGradingService.regradeNow(snapshot.passageId());

        String warning = read ? null : "Votre copie est bien jointe, mais elle n'a pas pu être lue automatiquement : "
            + "vérifiez qu'elle est nette, ou rédigez votre réponse dans la zone de saisie.";
        return new Outcome(snapshot.pages(), transcription.isBlank() ? null : transcription, warning);
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
