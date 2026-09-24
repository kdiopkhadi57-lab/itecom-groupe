package com.elearning.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExamQuestionRequestTest {

    @Test
    void shouldSupportHybridQuestionTypesForExams() {
        ExamQuestionRequest request = new ExamQuestionRequest();
        request.setQuestionText("Un cas pratique");
        request.setReferenceAnswer("Réponse attendue");
        request.setMaxScore(10);
        request.setQuestionType("PRACTICAL");
        request.setCorrectionData("{\"montant\": 2000}");
        request.setChoices(List.of(
            new ExamChoiceRequest("Option A", true),
            new ExamChoiceRequest("Option B", false)
        ));

        assertEquals("PRACTICAL", request.getQuestionType());
        assertEquals("{\"montant\": 2000}", request.getCorrectionData());
        assertEquals(2, request.getChoices().size());
        assertTrue(request.getChoices().get(0).getIsCorrect());
    }
}
