package com.elearning.dto.request;

import com.elearning.entity.Lesson;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LessonRequest {
    @NotBlank private String title;
    private String description;
    private String content;
    private String videoUrl;
    private String pdfUrl;
    private Integer duration;
    private Integer orderIndex;
    private Lesson.LessonType type;
    private String starterCode;
    private String language;
    private Boolean exercise;
}
