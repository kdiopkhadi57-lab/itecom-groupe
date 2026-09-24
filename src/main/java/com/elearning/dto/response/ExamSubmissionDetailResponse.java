package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExamSubmissionDetailResponse {
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private String status;
    private String submissionType;
    private List<String> scannedFileUrls;
    private Double totalScore;
    private Double maxScore;
    private String aiReport;
    private List<ExamAnswerDetailResponse> answers;
    private List<FullscreenViolationResponse> fullscreenViolations;
}
