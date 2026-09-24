package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "virtual_classes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirtualClass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private String roomName;
    private String recordingUrl;
    private String status; // SCHEDULED, ONGOING, COMPLETED, CANCELLED

    @Column(columnDefinition = "TEXT") private String recordingData;
    @Column(columnDefinition = "TEXT") private String thumbnailData;
    private String recordingMimeType;
    private String recordingFilename;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "teacher_id") private User teacher;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "course_id") private Course course;
    @CreationTimestamp private LocalDateTime createdAt;

    @OneToMany(mappedBy = "virtualClass", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VirtualClassStudent> students = new ArrayList<>();
}
