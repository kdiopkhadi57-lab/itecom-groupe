package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamStudentResponse {
    private Long id;
    private String studentName;
    private String studentEmail;
    private String status;
    private Double totalScore;
    private Double maxScore;
    private Double percentage;
    private String aiReport;
    private String submissionType;
}
