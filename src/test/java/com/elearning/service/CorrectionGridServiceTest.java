package com.elearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorrectionGridServiceTest {

    private final CorrectionGridService service =
        new CorrectionGridService(new CorrectionComparisonService(), new ObjectMapper());

    private static final String SUBJECT = """
        N°\tQuestion pratique\tRéponse de l’étudiant
        1\tUne entreprise achète 24 000 kg de mil à 210 FCFA/kg et supporte 10 FCFA de frais par kg. Calculez le coût d’achat.\t
        2\tLa production utilise 26 000 kg de farine à 180 FCFA/kg. Calculez le coût de la farine consommée.\t
        3\tLe chiffre d’affaires est de 24 500 000 FCFA et le coût de revient de 19 800 000 FCFA. Calculez le résultat.\t
        """;

    private static final String CORRECTION = """
        N°\tQuestion pratique\tRéponse attendue\tTolérance
        1\tUne entreprise achète 24 000 kg de mil à 210 FCFA/kg et supporte 10 FCFA de frais par kg. Calculez le coût d’achat.\t5 280 000 FCFA\tTolérance 0,1 %
        2\tLa production utilise 26 000 kg de farine à 180 FCFA/kg. Calculez le coût de la farine consommée.\t4 680 000 FCFA\tTolérance 0,1 %
        3\tLe chiffre d’affaires est de 24 500 000 FCFA et le coût de revient de 19 800 000 FCFA. Calculez le résultat.\t4 700 000 FCFA\tTolérance 0,1 %
        """;

    @Test
    void buildsOneRowPerExpectedAnswerWithQuestionTolerancePoints() {
        List<CorrectionGridService.GridRow> grid = service.buildGrid(CORRECTION, SUBJECT, 9);
        assertEquals(List.of("Q1", "Q2", "Q3"), grid.stream().map(CorrectionGridService.GridRow::id).toList());
        assertEquals(5_280_000, grid.get(0).expected());
        assertTrue(grid.get(0).question().startsWith("Une entreprise achète"));
        assertEquals(0.001, grid.get(0).tolerance(), 1e-12);
        assertEquals(3.0, grid.get(0).points()); // 9 points répartis sur 3 lignes
    }

    @Test
    void usesPointsColumnWhenPresent() {
        String correction = """
            N°\tRéponse attendue\tPoints
            1\t1 200\t2
            2\t350\t6
            """;
        var grid = service.buildGrid(correction, null, 10);
        assertEquals(2.0, grid.get(0).points());
        assertEquals(6.0, grid.get(1).points());
    }

    @Test
    void comparesStrictlyRowByRow() {
        var grid = service.buildGrid(CORRECTION, SUBJECT, 9);
        // Q2 reçoit la valeur de Q1 : elle est fausse pour Q2 même si le nombre existe ailleurs
        var result = service.compare(grid, Map.of("Q1", "5 280 000", "Q2", "5 280 000", "Q3", "4700000"), Map.of(), "");
        assertTrue(result.rows().get(0).correct());
        assertFalse(result.rows().get(1).correct());
        assertTrue(result.rows().get(2).correct());
        assertEquals(6.0, result.earned());
        assertEquals(9.0, result.total());
    }

    @Test
    void typedValueWinsOverScannedValueThenFreeText() {
        var grid = service.buildGrid(CORRECTION, SUBJECT, 9);
        var result = service.compare(grid,
            Map.of("Q1", "5 280 000"),                          // saisi
            Map.of("Q1", "9 999", "Q2", "4.680.000"),           // lu sur la copie
            "Résultat analytique = 4 700 000 FCFA");             // texte libre
        assertEquals(CorrectionGridService.SOURCE_TYPED, result.rows().get(0).source());
        assertTrue(result.rows().get(0).correct());
        assertEquals(CorrectionGridService.SOURCE_SCAN, result.rows().get(1).source());
        assertTrue(result.rows().get(1).correct());
        assertEquals(CorrectionGridService.SOURCE_TEXT, result.rows().get(2).source());
        assertTrue(result.rows().get(2).correct());
    }

    @Test
    void typedValueDifferentFromTheCopyIsFlaggedForTheTeacher() {
        var grid = service.buildGrid(CORRECTION, SUBJECT, 9);
        var result = service.compare(grid,
            Map.of("Q1", "5 280 000", "Q2", "4 680 000"),       // saisi
            Map.of("Q1", "5 820 000", "Q2", "4.680.000"),       // lu sur la copie
            null);
        // Q1 : la saisie est notée, mais la copie indique autre chose
        assertTrue(result.rows().get(0).correct());
        assertTrue(result.rows().get(0).conflict());
        assertEquals("5 820 000", result.rows().get(0).scannedValue());
        // Q2 : même valeur, écrite autrement : pas de désaccord
        assertFalse(result.rows().get(1).conflict());
        assertEquals(1, result.conflicts());
        assertTrue(result.report().contains("copie : 5 820 000"));
    }

    @Test
    void rowHintSentToTheOcrHasNoNumbers() {
        assertEquals("Coût de revient = … + … = … FCFA",
            CorrectionGridService.withoutNumbers("Coût de revient = 5 280 000 + 12,5 = 5 280 012,5 FCFA"));
    }

    @Test
    void wrongScannedValueIsNotRescuedByFreeText() {
        var grid = service.buildGrid(CORRECTION, SUBJECT, 9);
        // La copie donne 4 800 000 pour Q3 : faux, même si 4 700 000 apparaît dans un calcul intermédiaire
        var result = service.compare(grid, Map.of(), Map.of("Q3", "4 800 000"), "... 4 700 000 ...");
        assertFalse(result.rows().get(2).correct());
        assertTrue(result.report().contains("✗ Q3"));
    }

    @Test
    void rowWithSeveralResultsAcceptsTheAnswerTypedInTheSubjectRow() {
        String correction = """
            N°\tQuestion\tRéponse attendue
            1\tCalculez le coût et la marge\t1 200 FCFA ; 350 FCFA
            """;
        var grid = service.buildGrid(correction, null, 4);
        assertEquals(List.of("Q1a", "Q1b"), grid.stream().map(CorrectionGridService.GridRow::id).toList());
        var result = service.compare(grid, Map.of("Q1", "coût 1 200 et marge 350"), Map.of(), "");
        assertEquals(4.0, result.earned());
    }

    @Test
    void gridSurvivesJsonRoundTrip() {
        var grid = service.buildGrid(CORRECTION, SUBJECT, 9);
        assertEquals(grid, service.parseGrid(service.toJson(grid)));
    }
}
