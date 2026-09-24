package com.elearning.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorServiceTest {

    @Test
    void shouldSplitFlattenedQuestionBlocksIntoDistinctLines() {
        String raw = "Question 1: Qu'est-ce qu'un objet en Java ? A) Un type primitif B) Une instance de classe C) Un tableau Bonne réponse: B Question 2: Quelle est la sortie de System.out.println(2 + 2); ? A) 4 B) 22 Bonne réponse: A";

        String normalized = new DocumentTextExtractorService().normalizeExamText(raw);

        assertTrue(normalized.contains("Question 1:"));
        assertTrue(normalized.contains("\nA) Un type primitif"));
        assertTrue(normalized.contains("\nB) Une instance de classe"));
        assertTrue(normalized.contains("\nC) Un tableau"));
        assertTrue(normalized.contains("\nBonne réponse: B"));
        assertTrue(normalized.contains("\nQuestion 2:"));
    }

    @Test
    void shouldNormalizeCompactWordQuestionFormatsWithQuestionNumberAndLetterOptions() {
        String raw = "Q1Objet de la comptabilité analytique1 pt AÉtablir uniquement les déclarations fiscales B. Calculer et analyser les coûts et les résultats des produits C. Enregistrer uniquement les opérations bancaires D. Calculer la TVA.";

        String normalized = new DocumentTextExtractorService().normalizeExamText(raw);

        assertTrue(normalized.contains("Question 1: Objet de la comptabilité analytique 1 pt"));
        assertTrue(normalized.contains("A) Établir uniquement les déclarations fiscales"));
        assertTrue(normalized.contains("B. Calculer et analyser les coûts et les résultats des produits"));
        assertTrue(normalized.contains("C. Enregistrer uniquement les opérations bancaires"));
        assertTrue(normalized.contains("D. Calculer la TVA."));
    }

    @Test
    void shouldNormalizeCompactQuestionWithMonetaryOptions() {
        String raw = "Q6 Résultat analytique 1 pt A 1 000 000 FCFA B. 2 000 000 FCFA C. 8 000 000 FCFA D. 18 000 000 FCFA.";

        String normalized = new DocumentTextExtractorService().normalizeExamText(raw);

        assertTrue(normalized.contains("Question 6: Résultat analytique 1 pt"));
        assertTrue(normalized.contains("A) 1 000 000 FCFA"));
        assertTrue(normalized.contains("B) 2 000 000 FCFA"));
        assertTrue(normalized.contains("C) 8 000 000 FCFA"));
        assertTrue(normalized.contains("D) 18 000 000 FCFA."));
    }
}
