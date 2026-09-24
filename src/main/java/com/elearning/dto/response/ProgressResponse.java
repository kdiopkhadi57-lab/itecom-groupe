package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ProgressResponse {
    private Long courseId;
    private String courseTitle;
    private Integer totalLessons;
    private Integer completedLessons;
    private Double overallPercentage;
}
