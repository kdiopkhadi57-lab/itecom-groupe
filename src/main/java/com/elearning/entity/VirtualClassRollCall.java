package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Appel lancé par le professeur pendant une classe virtuelle : les étudiants confirment leur présence. */
@Entity @Table(name = "virtual_class_roll_calls")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirtualClassRollCall {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "virtual_class_id")
    private VirtualClass virtualClass;
    @Column(nullable = false) private LocalDateTime startedAt;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private String startedBy;

    @OneToMany(mappedBy = "rollCall", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<VirtualClassRollCallAnswer> answers = new java.util.ArrayList<>();
}
