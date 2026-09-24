package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StudentExamResponse {
    private Long examId;
    private String examTitle;
    private String examDescription;
    private String professorName;
    private String examStatus;
    private String studentStatus;
    private String accessToken;
    private Double totalScore;
    private Double maxScore;
    private Double percentage;
    private LocalDateTime createdAt;
}
