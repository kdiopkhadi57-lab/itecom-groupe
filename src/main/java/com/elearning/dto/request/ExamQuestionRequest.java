package com.elearning.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExamQuestionRequest {

    @NotBlank
    private String questionText;

    private String referenceAnswer;

    @NotNull
    @Min(1)
    private Integer maxScore;

    private String questionType = "QCM";

    private String correctionData;

    private List<ExamChoiceRequest> choices = new ArrayList<>();
}
