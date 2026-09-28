package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Une connexion d'un participant à une séance de classe virtuelle :
 * heure d'arrivée, dernier signe de présence et heure de départ.
 * Un participant qui se reconnecte crée une nouvelle ligne.
 */
@Entity
@Table(name = "virtual_class_attendances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirtualClassAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_class_id", nullable = false)
    @ToString.Exclude
    private VirtualClass virtualClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @Column(nullable = false)
    private String email;

    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** Dernier battement reçu ; sert d'heure de départ si le départ n'a pas été signalé. */
    private LocalDateTime lastSeenAt;

    private LocalDateTime leftAt;
}
