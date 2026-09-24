package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamChoiceResponse {
    private Long id;
    private String choiceText;
    private Boolean isCorrect;
    private Integer orderIndex;
}
