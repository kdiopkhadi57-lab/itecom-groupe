package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/** Barème des frais d'une année universitaire pour un niveau (et éventuellement une filière). */
@Entity @Table(name = "school_fees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolFee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 9) private String academicYear;   // 2026-2027
    @Column(nullable = false, length = 5) private String level;          // L1 … M2
    private String specialization;                                         // null : toutes les filières
    @Column(nullable = false) private long registrationFee;               // FCFA
    @Column(nullable = false) private long tuitionFee;                    // FCFA, pour l'année
    @Column(nullable = false) @Builder.Default private int installments = 1;
    @CreationTimestamp private LocalDateTime createdAt;
}
