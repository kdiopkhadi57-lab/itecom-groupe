package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qcm_students",
       uniqueConstraints = @UniqueConstraint(columnNames = {"qcm_id", "student_email"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QcmStudent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qcm_id", nullable = false)
    @ToString.Exclude
    private Qcm qcm;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String studentEmail;

    private String firstName;

    private String lastName;

    @Column(name = "student_level")
    private String level;

    // Mot de passe d'accès au devoir, propre à chaque étudiant (unique dans un même devoir)
    private String accessPassword;
}
