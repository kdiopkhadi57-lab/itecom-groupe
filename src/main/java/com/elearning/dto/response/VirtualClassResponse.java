package com.elearning.dto.response;

import com.elearning.entity.VirtualClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualClassResponse {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private String roomName;
    private String recordingUrl;
    private String status;
    private String teacherName;
    private Long teacherId;
    private String courseTitle;
    private LocalDateTime createdAt;
    private String thumbnailData;
    private boolean hasRecording;
    private int studentCount;

    public static VirtualClassResponse fromEntity(VirtualClass vc) {
        return VirtualClassResponse.builder()
            .id(vc.getId())
            .title(vc.getTitle())
            .description(vc.getDescription())
            .scheduledAt(vc.getScheduledAt())
            .durationMinutes(vc.getDurationMinutes())
            .roomName(vc.getRoomName())
            .recordingUrl(vc.getRecordingUrl())
            .status(vc.getStatus())
            .teacherName(vc.getTeacher() != null ? vc.getTeacher().getFirstName() + " " + vc.getTeacher().getLastName() : null)
            .teacherId(vc.getTeacher() != null ? vc.getTeacher().getId() : null)
            .courseTitle(vc.getCourse() != null ? vc.getCourse().getTitle() : null)
            .createdAt(vc.getCreatedAt())
            .thumbnailData(vc.getThumbnailData())
            .hasRecording(vc.getRecordingData() != null && !vc.getRecordingData().isEmpty())
            .studentCount(vc.getStudents() != null ? vc.getStudents().size() : 0)
            .build();
    }
}
