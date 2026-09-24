package com.elearning.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * Stores the file under uploadDir/subDirectory with a generated unique name
     * and returns the public URL (served via /uploads/**).
     */
    public String store(MultipartFile file, String subDirectory) throws IOException {
        Path targetDir = Paths.get(uploadDir, subDirectory).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }
        String filename = UUID.randomUUID() + extension;

        Path targetPath = targetDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + subDirectory + "/" + filename;
    }

    /** Stores raw bytes (e.g. a generated PDF) under uploadDir/subDirectory and returns the public URL. */
    public String storeBytes(byte[] data, String subDirectory, String extension) throws IOException {
        Path targetDir = Paths.get(uploadDir, subDirectory).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);
        String filename = UUID.randomUUID() + extension;
        Files.write(targetDir.resolve(filename), data);
        return "/uploads/" + subDirectory + "/" + filename;
    }
}
