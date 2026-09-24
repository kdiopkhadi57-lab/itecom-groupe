package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class ReferenceResponse {
    private Long id;
    private String refType;
    private String title;
    private String authors;
    private String year;
    private String publisher;
    private String place;
    private String isbn;
    private String edition;
    private String pages;
    private String journal;
    private String volume;
    private String issue;
    private String doi;
    private String startPage;
    private String endPage;
    private String url;
    private String accessDate;
    private String university;
    private String thesisType;
    private String collection;
    private String tags;
    private String abstract_;
    private String note;
    private LocalDateTime createdAt;
}
