package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class CourseResponse {
    private Long id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String category;
    private String level;
    private boolean published;
    private UserResponse teacher;
    private List<LessonResponse> lessons;
    private Integer totalLessons;
    private Integer totalDurationMinutes;
    private LocalDateTime createdAt;
}
