package com.elearning.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExamCreateRequest {

    @NotBlank
    private String title;

    private String description;

    /**
     * Durée estimée de l'examen en minutes (utilisée pour calculer quand un étudiant
     * exclu pour violations anti-triche pourra se reconnecter et voir son résultat).
     */
    private Integer estimatedDurationMinutes;

    /**
     * Questions saisies manuellement. Si vide, un sujet d'examen et un corrigé
     * doivent être fournis (examFile / correctionFile) pour extraction automatique par l'IA.
     */
    private List<ExamQuestionRequest> questions = new ArrayList<>();
}
