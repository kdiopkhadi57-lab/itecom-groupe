package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "books")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Book {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String coverUrl;

    private String fileUrl;

    private String category;   // Littérature, Informatique, Sciences, Histoire…

    private String genre;      // Roman, Essai, Manuel, Nouvelle…

    private String language;   // Français, Anglais, Wolof…

    @Column(name = "publication_year")
    private Integer year;

    private Integer pages;

    private String publisher;

    private String isbn;

    /** Lien externe pour lire le livre (Internet Archive, OpenLibrary…) */
    private String externalReadUrl;

    @Builder.Default
    private boolean available = true;

    @CreationTimestamp
    private LocalDateTime addedAt;
}
