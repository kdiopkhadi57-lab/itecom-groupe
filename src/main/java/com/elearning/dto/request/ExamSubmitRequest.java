package com.elearning.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class ExamSubmitRequest {

    @NotEmpty
    private List<StudentAnswerRequest> answers;
}
