package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExamQuestionResponse {
    private Long id;
    private String questionText;
    private Integer maxScore;
    private Integer orderIndex;
    private String questionType;
    private String correctionData;
    private List<ExamChoiceResponse> choices;
}
