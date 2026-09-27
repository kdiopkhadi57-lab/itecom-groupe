package com.elearning.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrectionComparisonServiceTest {

    private final CorrectionComparisonService service = new CorrectionComparisonService();

    private static final String SUBJECT = """
        N°\tQuestion pratique\tRéponse de l’étudiant
        1\tUne entreprise achète 24 000 kg de mil à 210 FCFA/kg et supporte 10 FCFA de frais par kg acheté. Calculez le coût d’achat total du mil.
        2\tLa production utilise 26 000 kg de farine au coût unitaire de 180 FCFA/kg. Calculez le coût de la farine consommée.
        3\tLe chiffre d’affaires est de 24 500 000 FCFA et le coût de revient de 19 800 000 FCFA. Calculez le résultat analytique.
        """;

    private static final String CORRECTION = """
        N°\tQuestion pratique\tRéponse attendue\tTolérance
        1\tUne entreprise achète 24 000 kg de mil à 210 FCFA/kg et supporte 10 FCFA de frais par kg acheté. Calculez le coût d’achat total du mil.\t5 280 000 FCFA\tTolérance 0,1 %
        2\tLa production utilise 26 000 kg de farine au coût unitaire de 180 FCFA/kg. Calculez le coût de la farine consommée.\t4 680 000 FCFA\tTolérance 0,1 %
        3\tLe chiffre d’affaires est de 24 500 000 FCFA et le coût de revient de 19 800 000 FCFA. Calculez le résultat analytique.\t4 700 000 FCFA\tTolérance 0,1 %
        """;

    @Test
    void readsExpectedAnswersFromCorrectionTable() {
        List<CorrectionComparisonService.ExpectedValue> expected = service.expectedValues(CORRECTION, SUBJECT);
        assertEquals(List.of(5_280_000.0, 4_680_000.0, 4_700_000.0),
            expected.stream().map(CorrectionComparisonService.ExpectedValue::value).toList());
        assertEquals("Q1", expected.get(0).label());
        assertEquals(0.001, expected.get(0).relativeTolerance(), 1e-12);
    }

    @Test
    void comparesEveryExpectedNumberWithStudentAnswer() {
        var expected = service.expectedValues(CORRECTION, SUBJECT);
        String answer = """
            1) Coût d'achat = 24 000 x (210 + 10) = 5 280 000 FCFA
            2) 26000 * 180 = 4680000
            3) Résultat = 24 500 000 - 19 800 000 = 4 800 000 FCFA
            """;
        var result = service.compare(expected, answer);
        assertEquals(2, result.matched());
        assertEquals(3, result.total());
        assertEquals(4_700_000.0, result.missing().get(0).value());
        assertTrue(result.report().contains("✗ Q3"));
    }

    @Test
    void withoutTableIgnoresSubjectDataAndQuestionNumbers() {
        String subject = "Question 1 : un stock de 1 200 unités à 350 FCFA. Calculez la valeur du stock.";
        String correction = "Question 1 : valeur du stock = 1 200 x 350 = 420 000 FCFA";
        var expected = service.expectedValues(correction, subject);
        assertEquals(List.of(420_000.0), expected.stream().map(CorrectionComparisonService.ExpectedValue::value).toList());
        assertEquals(1, service.compare(expected, "La valeur est de 420.000 FCFA").matched());
    }

    @Test
    void parsesFrenchAndPlainNumberFormats() {
        assertEquals(1_250_000.5, service.parse("1.250.000,5"));
        assertEquals(12.5, service.parse("12,5"));
        assertEquals(12.5, service.parse("12.5"));
        assertEquals(5_280_000.0, service.parse("5 280 000"));
    }
}
