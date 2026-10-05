package com.elearning.controller;

import com.elearning.dto.response.VirtualClassResponse;
import com.elearning.entity.Course;
import com.elearning.entity.Role;
import com.elearning.entity.VirtualClass;
import com.elearning.entity.User;
import com.elearning.entity.VirtualClassStudent;
import com.elearning.repository.CourseRepository;
import com.elearning.repository.VirtualClassRepository;
import com.elearning.repository.UserRepository;
import com.elearning.service.StudentListParserService;
import com.elearning.service.JitsiTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VirtualClassController {

    private final VirtualClassRepository virtualClassRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final StudentListParserService studentListParserService;
    private final JitsiTokenService jitsiTokenService;
    private final com.elearning.service.StudentAudienceService audienceService;
    private final com.elearning.service.RecordingStorageService recordingStorage;

    /** Nombre d'étudiants conviés : liste et niveaux ciblés réunis, sans doublon. */
    private VirtualClassResponse toResponse(VirtualClass vc) {
        VirtualClassResponse r = VirtualClassResponse.fromEntity(vc);
        r.setStudentCount(audienceService.virtualClassAudience(vc).size());
        r.setHasRecording(recordingStorage.hasRecording(vc));
        r.setRecordingUrl(recordingStorage.playbackUrl(vc));
        r.setRecordingLightUrl(recordingStorage.lightUrl(vc));
        return r;
    }

    @GetMapping("/api/virtual-classes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<VirtualClassResponse>> getAll() {
        return ResponseEntity.ok(virtualClassRepository.findAll().stream()
            .map(this::toResponse).toList());
    }

    @GetMapping("/api/virtual-classes/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<VirtualClassResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(
            virtualClassRepository.findById(id).orElseThrow()));
    }

    @GetMapping("/api/virtual-classes/{id}/jitsi-token")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> getJitsiToken(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        VirtualClass virtualClass = virtualClassRepository.findById(id).orElseThrow();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        boolean admin = user.getRole() == Role.ROLE_ADMIN;
        boolean teacher = user.getRole() == Role.ROLE_TEACHER;
        boolean host = admin || teacher || (virtualClass.getTeacher() != null
            && virtualClass.getTeacher().getId().equals(user.getId()));
        boolean assignedStudent = com.elearning.service.StudentAudienceService.isInvited(virtualClass, user);

        if (!host && !assignedStudent) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String displayName = user.getFirstName() + " " + user.getLastName();
        String token = jitsiTokenService.createToken(
            virtualClass.getRoomName(), user.getId(), displayName, user.getEmail(), host);
        return ResponseEntity.ok(Map.of(
            "token", token,
            "domain", jitsiTokenService.getDomain(),
            "roomName", jitsiTokenService.getRoomName(virtualClass.getRoomName())));
    }

    @GetMapping("/api/virtual-classes/{id}/recording")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getRecording(@PathVariable Long id) {
        VirtualClass vc = virtualClassRepository.findById(id).orElseThrow();
        // Enregistrement rangé en fichier : lecture en streaming depuis /uploads
        if (recordingStorage.fileAvailable(vc)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, recordingStorage.playbackUrl(vc)).build();
        }
        if (vc.getRecordingData() == null || vc.getRecordingData().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] videoBytes = Base64.getDecoder().decode(vc.getRecordingData());
        String mimeType = vc.getRecordingMimeType() != null ? vc.getRecordingMimeType() : "video/webm";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, mimeType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + (vc.getRecordingFilename() != null ? vc.getRecordingFilename() : "recording.webm") + "\"")
            .body(videoBytes);
    }

    @PostMapping(value = "/api/teacher/virtual-classes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<VirtualClassResponse> create(
            @RequestPart("class") VirtualClass data,
            @RequestPart(value = "studentList", required = false) MultipartFile studentListFile,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Étudiants de la liste (comptes existants) et/ou niveaux entiers
        final List<StudentListParserService.StudentInfo> studentInfos;
        try {
            studentInfos = studentListFile == null || studentListFile.isEmpty()
                ? List.of() : studentListParserService.parseFile(studentListFile);
            audienceService.requireStudentAccounts(studentInfos.stream().map(StudentListParserService.StudentInfo::email).toList());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        data.setTargetLevels(com.elearning.service.StudentAudienceService.normalizeLevels(
            com.elearning.service.StudentAudienceService.levelList(data.getTargetLevels())));
        if (studentInfos.isEmpty() && data.getTargetLevels() == null) {
            throw new IllegalArgumentException("Choisissez au moins un niveau ou un étudiant.");
        }
        User teacher = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        data.setTeacher(teacher);
        data.setStatus("SCHEDULED");
        data.setRoomName("elearning-" + UUID.randomUUID());
        data.setRecordingUrl(null);
        if (data.getCourse() != null && data.getCourse().getId() != null) {
            Course course = courseRepository.findById(data.getCourse().getId()).orElse(null);
            data.setCourse(course);
        } else {
            data.setCourse(null);
        }
        if (data.getStudents() == null) {
            data.setStudents(new ArrayList<>());
        } else {
            data.getStudents().clear();
        }
        studentInfos.stream().map(info -> userRepository.findByEmail(info.email().trim().toLowerCase()).orElseThrow())
            .distinct()
            .forEach(account -> data.getStudents().add(VirtualClassStudent.builder()
                .virtualClass(data)
                .studentName(com.elearning.service.StudentAudienceService.fullName(account))
                .studentEmail(account.getEmail())
                .build()));
        VirtualClass saved = virtualClassRepository.save(data);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/api/teacher/virtual-classes/{id}/status")
    @Transactional
    public ResponseEntity<VirtualClassResponse> updateStatus(
            @PathVariable Long id, @RequestParam String status) {
        VirtualClass vc = virtualClassRepository.findById(id).orElseThrow();
        vc.setStatus(status);
        return ResponseEntity.ok(toResponse(virtualClassRepository.save(vc)));
    }

    /** Enregistrement envoyé en fichier (multipart) : plus de base64, plus de limite de 45 Mo. */
    @PostMapping(value = "/api/teacher/virtual-classes/{id}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<VirtualClassResponse> uploadRecording(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) String thumbnailBase64) throws java.io.IOException {
        VirtualClass vc = virtualClassRepository.findById(id).orElseThrow();
        recordingStorage.store(vc, file, thumbnailBase64);
        vc.setStatus("COMPLETED");
        return ResponseEntity.ok(toResponse(virtualClassRepository.save(vc)));
    }

    @DeleteMapping("/api/teacher/virtual-classes/{id}/recording")
    @Transactional
    public ResponseEntity<VirtualClassResponse> deleteRecording(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        VirtualClass vc = virtualClassRepository.findById(id).orElseThrow();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        boolean isOwner = vc.getTeacher() != null && vc.getTeacher().getId().equals(user.getId());
        boolean isAdmin = userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        recordingStorage.delete(vc);
        vc.setStatus("COMPLETED");
        return ResponseEntity.ok(toResponse(virtualClassRepository.save(vc)));
    }

    @DeleteMapping("/api/teacher/virtual-classes/{id}")
    @Transactional
    public ResponseEntity<Void> deleteClass(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        VirtualClass vc = virtualClassRepository.findById(id).orElseThrow();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        boolean isOwner = vc.getTeacher() != null && vc.getTeacher().getId().equals(user.getId());
        boolean isAdmin = userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        recordingStorage.deleteFiles(vc.getRecordingUrl());
        virtualClassRepository.delete(vc);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/admin/virtual-classes/{id}")
    @Transactional
    public ResponseEntity<Void> adminDeleteClass(@PathVariable Long id) {
        virtualClassRepository.findById(id).ifPresent(vc -> recordingStorage.deleteFiles(vc.getRecordingUrl()));
        virtualClassRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
