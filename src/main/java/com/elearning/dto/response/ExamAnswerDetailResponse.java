package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamAnswerDetailResponse {
    private Long questionId;
    private int orderIndex;
    private String questionText;
    private String referenceAnswer;
    private Integer maxScore;
    private String studentAnswer;
    private Double obtainedScore;
    private String aiComment;
}
