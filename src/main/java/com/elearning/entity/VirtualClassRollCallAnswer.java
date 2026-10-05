package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Réponse « Je suis présent » d'un étudiant à un appel. */
@Entity
@Table(name = "virtual_class_roll_call_answers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"roll_call_id", "email"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirtualClassRollCallAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "roll_call_id")
    private VirtualClassRollCall rollCall;
    @Column(nullable = false) private String email;
    private String fullName;
    @Column(nullable = false) private LocalDateTime answeredAt;
}
