package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "references_bib")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reference {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Type: book, article, website, journal, thesis, conference
    private String refType;

    // Common fields
    private String title;
    private String authors;       // comma-separated: "Bâ, M., Kane, C.H."
    @Column(name = "publication_year")
    private String year;
    private String publisher;
    private String place;

    // Book-specific
    private String isbn;
    private String edition;
    private String pages;

    // Article/Journal-specific
    private String journal;
    private String volume;
    private String issue;
    private String doi;
    private String startPage;
    private String endPage;

    // Web-specific
    private String url;
    private String accessDate;

    // Thesis-specific
    private String university;
    private String thesisType;   // PhD, Master, Licence

    // Organization
    private String collection;   // folder/collection name
    private String tags;         // comma-separated tags

    @Column(columnDefinition = "TEXT")
    private String abstract_;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
