package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "book_annotations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookAnnotation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    private Integer pageNumber;

    @Column(columnDefinition = "TEXT")
    private String selectedText;

    private String color;   // yellow, green, blue, pink, orange

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    private AnnotationType type;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum AnnotationType { HIGHLIGHT, BOOKMARK, NOTE }
}
