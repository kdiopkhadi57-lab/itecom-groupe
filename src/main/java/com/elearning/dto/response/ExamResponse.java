package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamResponse {
    private Long id;
    private String title;
    private String description;
    private String professorName;
    private Integer estimatedDurationMinutes;
    private String status;
    private int questionCount;
    private int studentCount;
    private LocalDateTime createdAt;
    private List<ExamQuestionResponse> questions;
    private List<ExamStudentResponse> students;
}
