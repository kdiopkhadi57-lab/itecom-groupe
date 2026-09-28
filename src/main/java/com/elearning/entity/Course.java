package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name = "courses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    private String thumbnailUrl;
    private String category;
    private String level; // Niveau LMD : L1, L2, L3, M1, M2

    public static final java.util.List<String> LEVELS = java.util.List.of("L1", "L2", "L3", "M1", "M2");

    /** Niveau LMD normalisé ; les anciens niveaux sont convertis (Débutant → L1, Intermédiaire → L2, Avancé → L3). */
    public static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) return "L1";
        String l = level.trim().toUpperCase();
        return switch (l) {
            case "BEGINNER" -> "L1";
            case "INTERMEDIATE" -> "L2";
            case "ADVANCED" -> "L3";
            default -> LEVELS.contains(l) ? l : null;
        };
    }
    @Column(nullable = false) private boolean published = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL,orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
