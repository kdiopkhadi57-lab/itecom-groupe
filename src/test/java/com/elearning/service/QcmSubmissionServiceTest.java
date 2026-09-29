package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.QcmPassageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QcmSubmissionServiceTest {

    private final QcmPassageRepository passageRepo = mock(QcmPassageRepository.class);
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    private QcmSubmissionService service;
    private Qcm qcm;
    private QcmChoice goodChoice;

    @BeforeEach
    void setUp() {
        service = new QcmSubmissionService(passageRepo, new ObjectMapper(), new CorrectionComparisonService(), events);
        when(passageRepo.save(any(QcmPassage.class))).thenAnswer(inv -> inv.getArgument(0));

        qcm = Qcm.builder().id(1L).title("Devoir").estimatedDurationMinutes(30).build();
        QcmQuestion qcmQuestion = QcmQuestion.builder().id(10L).qcm(qcm).questionText("2 + 2 ?")
            .points(2).questionType("QCM").orderIndex(0).choices(new ArrayList<>()).build();
        goodChoice = QcmChoice.builder().id(100L).question(qcmQuestion).choiceText("4").isCorrect(true).orderIndex(0).build();
        qcmQuestion.getChoices().add(goodChoice);
        qcmQuestion.getChoices().add(QcmChoice.builder().id(101L).question(qcmQuestion).choiceText("5").isCorrect(false).orderIndex(1).build());
        QcmQuestion caseQuestion = QcmQuestion.builder().id(20L).qcm(qcm).questionText("Calculez le coût")
            .points(3).questionType("CASE").orderIndex(1).expectedAnswer("Coût = 5 280 000 FCFA").choices(new ArrayList<>()).build();
        qcm.getQuestions().add(qcmQuestion);
        qcm.getQuestions().add(caseQuestion);
    }

    private QcmPassage passage(LocalDateTime startedAt) {
        return QcmPassage.builder().id(5L).qcm(qcm)
            .student(User.builder().email("e@test.com").firstName("E").lastName("T").password("x").build())
            .startedAt(startedAt).build();
    }

    private QcmSubmissionService.Submission answers(String caseAnswer, Boolean forced) {
        return new QcmSubmissionService.Submission(List.of(
            new QcmSubmissionService.Answer(10L, 100L, Map.of()),
            new QcmSubmissionService.Answer(20L, null, caseAnswer == null ? Map.of() : Map.of("answer", caseAnswer))
        ), null, forced);
    }

    @Test
    void refusesWithMessageWhenCaseIsNeitherTypedNorScanned() {
        QcmPassage p = passage(LocalDateTime.now());
        var e = assertThrows(QcmSubmissionService.SubmissionRefusedException.class, () -> service.submit(p, answers(null, false)));
        assertTrue(e.getMessage().contains("cas pratique"));
        assertFalse(Boolean.TRUE.equals(p.getIsSubmitted()));
    }

    @Test
    void forcedSubmissionIsAlwaysAcceptedAndGraded() {
        QcmPassage p = passage(LocalDateTime.now());
        service.submit(p, answers(null, true));
        assertTrue(p.getIsSubmitted());
        assertEquals(2, p.getScore());      // QCM juste, cas pratique vide
        assertEquals(5, p.getMaxScore());
    }

    @Test
    void typedCaseAnswerIsComparedWithCorrection() {
        QcmPassage p = passage(LocalDateTime.now());
        service.submit(p, answers("24 000 x 220 = 5 280 000 FCFA", false));
        assertEquals(5, p.getScore());
        assertEquals("24 000 x 220 = 5 280 000 FCFA", p.getReponses().get(1).getTextAnswer());
    }

    @Test
    void expiredPassageIsSubmittedFromDraft() {
        QcmPassage p = passage(LocalDateTime.now().minusMinutes(40));
        service.saveDraft(p, answers("5 280 000", false));
        assertNotNull(p.getDraftAnswers());

        assertTrue(service.autoSubmitIfExpired(p));
        assertTrue(p.getIsSubmitted());
        assertEquals(5, p.getScore());
    }

    @Test
    void passageStillRunningIsNotAutoSubmitted() {
        QcmPassage p = passage(LocalDateTime.now().minusMinutes(10));
        assertFalse(service.autoSubmitIfExpired(p));
        assertNotEquals(Boolean.TRUE, p.getIsSubmitted());
    }

    @Test
    void aiGradeIsUsedForTheCaseAndCombinesTypedAnswerAndCopy() {
        QcmPassage p = passage(LocalDateTime.now());
        p.setPaperCorrectionUrl("/uploads/devoirs/copies/1/p1.jpg");
        p.setOcrExtractedText("Coût = 5 280 000 FCFA");
        var request = service.caseRequest(p, answers("Voir ma copie", false));
        assertEquals(1, request.questions().size());
        assertEquals("Voir ma copie", request.questions().get(0).typedAnswer());
        assertEquals("Coût = 5 280 000 FCFA", request.copyTranscription());
        assertEquals("Coût = 5 280 000 FCFA", request.questions().get(0).correction());

        var grade = new QcmCaseOcrGradingService.CaseGrade(
            Map.of(20L, new QcmCaseOcrGradingService.QuestionGrade(2.5, "Résultat juste, méthode incomplète")), "Bonne copie");
        service.submit(p, answers("Voir ma copie", false), grade);
        assertEquals(2 + 3, p.getScore());          // 2,5 arrondi à 3
        assertEquals(3, p.getOcrScore());
        assertTrue(p.getOcrCorrectionNote().contains("Résultat juste, méthode incomplète"));
        assertTrue(p.getOcrCorrectionNote().contains("Chiffres du corrigé retrouvés : 1/1"));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void withoutAiGradeTheCaseGetsAProvisionalScoreAndIsQueuedForGrading() {
        QcmPassage p = passage(LocalDateTime.now());
        service.submit(p, answers("5 280 000", false));
        assertEquals(5, p.getScore());
        assertNull(p.getOcrScore());
        assertTrue(p.getOcrCorrectionNote().contains("note provisoire"));
        verify(events).publishEvent(any(QcmSubmissionService.CaseGradingRequested.class));
    }

    @Test
    void typedAnswerIncludesTheSubjectTableCells() {
        var answer = new QcmSubmissionService.Answer(20L, null, Map.of("answer", "Calculs ci-dessous", "Q2", "4 680 000", "Q1", "5 280 000"));
        assertEquals("Calculs ci-dessous\nQ1 : 5 280 000\nQ2 : 4 680 000", QcmSubmissionService.typedAnswerOf(answer));
    }

    @Test
    void paperCopyAloneIsEnoughToSubmit() {
        QcmPassage p = passage(LocalDateTime.now());
        p.setPaperCorrectionUrl("/uploads/devoirs/copies/1/p1.jpg");
        assertNull(service.refusal(p, answers(null, false)));
    }
}
