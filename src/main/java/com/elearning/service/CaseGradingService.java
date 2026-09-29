package com.elearning.service;

import com.elearning.entity.QcmPassage;
import com.elearning.repository.QcmPassageRepository;
import com.elearning.repository.QcmRepository;
import com.elearning.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Correction du cas pratique par l'IA, sans retenir de connexion à la base pendant l'appel :
 * une transaction courte prépare la demande (corrigé, réponse saisie, texte lu sur la copie),
 * l'IA corrige hors transaction, puis une seconde transaction courte enregistre la note.
 */
@Service
@Slf4j
public class CaseGradingService {

    /** Issue d'une soumission : le résultat pour l'étudiant, ou le motif du refus. */
    public record Outcome<T>(T result, String refusal, int status) {}

    private record Prepared(Long passageId, QcmCaseOcrGradingService.CaseRequest request, String refusal,
                            boolean alreadySubmitted) {}

    private final QcmPassageRepository passageRepo;
    private final QcmRepository qcmRepo;
    private final UserRepository userRepo;
    private final QcmSubmissionService submissionService;
    private final QcmCaseOcrGradingService ai;
    private final AiTaskExecutor executor;
    private final TransactionTemplate tx;

    public CaseGradingService(QcmPassageRepository passageRepo, QcmRepository qcmRepo, UserRepository userRepo,
                              QcmSubmissionService submissionService, QcmCaseOcrGradingService ai,
                              AiTaskExecutor executor, PlatformTransactionManager transactionManager) {
        this.passageRepo = passageRepo;
        this.qcmRepo = qcmRepo;
        this.userRepo = userRepo;
        this.submissionService = submissionService;
        this.ai = ai;
        this.executor = executor;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Soumission par l'étudiant, traitée hors du thread de la requête : réponses conservées, correction
     * du cas pratique par l'IA, puis note enregistrée. {@code result} construit la réponse en transaction.
     */
    public <T> CompletableFuture<Outcome<T>> submit(Long qcmId, String studentEmail, QcmSubmissionService.Submission input,
                                                   Function<QcmPassage, T> result) {
        return executor.run(() -> {
            Prepared prepared = tx.execute(status -> {
                var qcm = qcmRepo.findById(qcmId).orElse(null);
                var student = userRepo.findByEmail(studentEmail).orElse(null);
                QcmPassage passage = qcm == null || student == null ? null
                    : passageRepo.findByQcmAndStudent(qcm, student).orElse(null);
                if (passage == null) return new Prepared(null, null, "Commencez le devoir avant de le soumettre.", false);
                // Copie déjà soumise (par exemple automatiquement) : on renvoie le résultat enregistré
                if (Boolean.TRUE.equals(passage.getIsSubmitted())) return new Prepared(passage.getId(), null, null, true);
                // Les réponses sont d'abord conservées : rien n'est perdu si la soumission est refusée
                submissionService.saveDraft(passage, input);
                String refusal = submissionService.refusal(passage, input);
                return new Prepared(passage.getId(), refusal == null ? submissionService.caseRequest(passage, input) : null,
                    refusal, false);
            });
            if (prepared.refusal() != null) return new Outcome<T>(null, prepared.refusal(), 400);

            QcmCaseOcrGradingService.CaseGrade grade = prepared.alreadySubmitted() || prepared.request() == null
                ? null : ai.gradeCase(prepared.request());

            return tx.execute(status -> {
                QcmPassage passage = passageRepo.findById(prepared.passageId()).orElseThrow();
                if (!Boolean.TRUE.equals(passage.getIsSubmitted())) {
                    try {
                        submissionService.submit(passage, input, grade);
                    } catch (QcmSubmissionService.SubmissionRefusedException e) {
                        status.setRollbackOnly();
                        return new Outcome<T>(null, e.getMessage(), 400);
                    }
                }
                return new Outcome<>(result.apply(passage), null, 200);
            });
        });
    }

    /** Copie soumise avec une note provisoire (soumission automatique, IA indisponible) : correction en arrière-plan. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCaseGradingRequested(QcmSubmissionService.CaseGradingRequested event) {
        if (!ai.available()) return;
        executor.run(() -> {
            regradeNow(event.passageId());
            return null;
        });
    }

    /**
     * Corrige par l'IA une copie déjà soumise (réponses enregistrées et texte lu sur la copie), puis met à
     * jour sa note. À appeler hors transaction et hors du thread d'une requête.
     */
    public void regradeNow(Long passageId) {
        try {
            QcmCaseOcrGradingService.CaseRequest request = tx.execute(status -> {
                QcmPassage passage = passageRepo.findById(passageId).orElse(null);
                if (passage == null || !Boolean.TRUE.equals(passage.getIsSubmitted()) || passage.getManualScore() != null) return null;
                return submissionService.caseRequest(passage, submissionService.readDraft(passage));
            });
            if (request == null) return;
            QcmCaseOcrGradingService.CaseGrade grade = ai.gradeCase(request);
            if (grade == null) {
                log.warn("Passage {} : correction par l'IA impossible, la note provisoire est conservée", passageId);
                return;
            }
            tx.executeWithoutResult(status -> passageRepo.findById(passageId)
                .ifPresent(passage -> submissionService.regrade(passage, grade)));
            log.info("Passage {} : cas pratique corrigé par l'IA", passageId);
        } catch (Exception e) {
            log.error("Passage {} : correction en arrière-plan impossible : {}", passageId, e.getMessage(), e);
        }
    }
}
