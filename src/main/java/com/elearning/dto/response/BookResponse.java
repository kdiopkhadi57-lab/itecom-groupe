package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class BookResponse {
    private Long id;
    private String title;
    private String author;
    private String description;
    private String coverUrl;
    private String fileUrl;
    private String category;
    private String genre;
    private String language;
    private Integer year;
    private Integer pages;
    private String publisher;
    private String isbn;
    private String externalReadUrl;
    private boolean available;
    private boolean hasFile;
    private boolean hasExternalRead;
    private LocalDateTime addedAt;
}
