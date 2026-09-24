package com.elearning.dto.response;

import com.elearning.entity.Lesson;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class LessonResponse {
    private Long id;
    private Long courseId;
    private String title;
    private String description;
    private String content;
    private String videoUrl;
    private String pdfUrl;
    private Integer duration;
    private Integer orderIndex;
    private Lesson.LessonType type;
    private boolean completed; // for current user
    private Double progressPercentage;
    private String starterCode;
    private String language;
    private boolean exercise;
}
