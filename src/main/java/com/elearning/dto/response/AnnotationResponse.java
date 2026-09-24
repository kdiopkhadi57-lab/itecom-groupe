package com.elearning.dto.response;

import com.elearning.entity.BookAnnotation;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class AnnotationResponse {
    private Long id;
    private Long bookId;
    private Integer pageNumber;
    private String selectedText;
    private String color;
    private String note;
    private BookAnnotation.AnnotationType type;
    private LocalDateTime createdAt;
}
