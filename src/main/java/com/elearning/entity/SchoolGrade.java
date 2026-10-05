package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/** Note sur 20 d'un étudiant dans une matière, pour un semestre et une session. */
@Entity
@Table(name = "school_grades",
       uniqueConstraints = @UniqueConstraint(columnNames = {"enrollment_id", "semester", "subject", "exam_session"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolGrade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "enrollment_id")
    private SchoolEnrollment enrollment;
    @Column(nullable = false, length = 5) private String semester;        // S1 | S2
    @Column(nullable = false) private String subject;
    @Column(nullable = false) @Builder.Default private double coefficient = 1;
    @Column(nullable = false) private double grade;
    @Column(name = "exam_session", nullable = false, length = 12) @Builder.Default
    private String session = "NORMALE";                                    // NORMALE | RATTRAPAGE
    @Column(length = 500) private String comment;
    @Column(nullable = false) @Builder.Default private boolean published = false;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
