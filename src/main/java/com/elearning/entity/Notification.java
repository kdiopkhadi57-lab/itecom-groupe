package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/** Notification affichée dans la cloche de l'en-tête (et envoyée par email si demandé). */
@Entity @Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 1000) private String message;
    private String link;
    @Column(nullable = false, length = 20) @Builder.Default private String category = "INFO";
    @Column(name = "is_read", nullable = false) @Builder.Default private boolean read = false;
    @CreationTimestamp private LocalDateTime createdAt;
}
