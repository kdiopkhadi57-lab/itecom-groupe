package com.elearning.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudentAnswerRequest {

    @NotNull
    private Long questionId;

    private String answer;
}
