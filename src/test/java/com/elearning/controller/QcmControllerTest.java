package com.elearning.controller;

import com.elearning.entity.Qcm;
import com.elearning.entity.QcmQuestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QcmControllerTest {

    @Test
    void ensureDocumentCaseQuestionAddsCaseQuestionFromSubjectAndCorrection() {
        Qcm qcm = Qcm.builder()
            .title("Devoir mixte")
            .subjectText("Calculez le coût total de production et justifiez votre réponse.")
            .correctionText("Coût total = 1200 ; justification : marge nette.")
            .build();

        QcmController.ensureDocumentCaseQuestion(qcm);

        assertEquals(1, qcm.getQuestions().size());
        QcmQuestion question = qcm.getQuestions().get(0);
        assertEquals("CASE", question.getQuestionType());
        assertTrue(question.getQuestionText().contains("Calculez le coût total"));
        assertTrue(question.getCorrectionData().contains("1200"));
        assertTrue(question.getExpectedAnswer().contains("marge nette"));
    }
}
