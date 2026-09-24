package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamTakeResponse {
    private Long examId;
    private String examTitle;
    private String examDescription;
    private String studentName;
    private String studentEmail;
    private String status;
    private List<ExamQuestionResponse> questions;
    private long gradingDelayMinutes;

    // Présent uniquement si l'étudiant a été exclu pour violations anti-triche
    // et que le temps estimé de l'examen n'est pas encore écoulé.
    @Builder.Default
    private boolean resultsLocked = false;
    private LocalDateTime resultsAvailableAt;
}
