package com.elearning.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamChoiceRequest {
    private String choiceText;
    private Boolean isCorrect = false;
}
