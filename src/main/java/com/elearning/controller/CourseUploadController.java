package com.elearning.controller;

import com.elearning.dto.response.ApiResponse;
import com.elearning.service.FileStorageService;
import com.elearning.service.WordToPdfConverterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Upload des fichiers (vidéos, PDF, documents) attachés aux leçons d'un cours, en un seul ou plusieurs lots. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CourseUploadController {

    private final FileStorageService fileStorageService;
    private final WordToPdfConverterService wordToPdfConverterService;

    public record UploadedFile(String url, String originalName, String type, long size) {}

    /** Upload d'un seul fichier (vidéo ou document) pour une leçon. */
    @PostMapping("/api/teacher/courses/upload")
    public ResponseEntity<ApiResponse<UploadedFile>> uploadOne(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Fichier téléversé", store(file)));
    }

    /** Upload de plusieurs fichiers (vidéos et/ou documents) en une seule requête. */
    @PostMapping("/api/teacher/courses/upload-batch")
    public ResponseEntity<ApiResponse<List<UploadedFile>>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        List<UploadedFile> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(store(file));
        }
        return ResponseEntity.ok(ApiResponse.success(results.size() + " fichier(s) téléversé(s)", results));
    }

    private UploadedFile store(MultipartFile file) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        boolean isVideo = contentType.startsWith("video/");
        boolean isWord = originalName.toLowerCase().endsWith(".doc") || originalName.toLowerCase().endsWith(".docx");

        if (isVideo) {
            String url = fileStorageService.store(file, "courses/videos");
            return new UploadedFile(url, originalName, "VIDEO", file.getSize());
        }

        if (isWord) {
            // Les fichiers Word ne s'affichent pas dans un navigateur : on les convertit en PDF
            // pour permettre une lecture directe (avec défilement) dans l'application.
            try {
                byte[] pdfBytes = wordToPdfConverterService.convertDocxToPdf(file.getInputStream());
                String url = fileStorageService.storeBytes(pdfBytes, "courses/documents", ".pdf");
                return new UploadedFile(url, originalName, "PDF", pdfBytes.length);
            } catch (Exception e) {
                log.error("Échec de la conversion Word→PDF pour {} : {}", originalName, e.getMessage());
                String url = fileStorageService.store(file, "courses/documents");
                return new UploadedFile(url, originalName, "PDF", file.getSize());
            }
        }

        String url = fileStorageService.store(file, "courses/documents");
        return new UploadedFile(url, originalName, "PDF", file.getSize());
    }
}
