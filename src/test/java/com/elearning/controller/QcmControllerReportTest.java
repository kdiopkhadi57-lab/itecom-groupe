package com.elearning.controller;

import com.elearning.entity.*;
import com.elearning.repository.QcmPassageRepository;
import com.elearning.repository.QcmRepository;
import com.elearning.repository.UserRepository;
import com.elearning.service.DocumentTextExtractorService;
import com.elearning.service.FileStorageService;
import com.elearning.service.StudentListParserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QcmControllerReportTest {

    @Test
    void reportContainsStudentIdentityColumns() throws Exception {
        QcmRepository qcmRepo = mock(QcmRepository.class);
        QcmPassageRepository passageRepo = mock(QcmPassageRepository.class);
        QcmController controller = new QcmController(qcmRepo, passageRepo, mock(UserRepository.class),
            mock(StudentListParserService.class), mock(DocumentTextExtractorService.class),
            mock(FileStorageService.class), new BCryptPasswordEncoder(4), mock(com.elearning.service.QcmSubmissionService.class));

        Qcm qcm = Qcm.builder().id(1L).title("Devoir test").build();
        qcm.getAssignedStudents().add(QcmStudent.builder().qcm(qcm).studentName("Awa Diop")
            .studentEmail("awa@test.com").firstName("Awa").lastName("Diop").level("L2").build());
        qcm.getAssignedStudents().add(QcmStudent.builder().qcm(qcm).studentName("Fatou Ndiaye")
            .studentEmail("fatou@test.com").level("L3").build());
        User awa = User.builder().email("awa@test.com").firstName("Awa").lastName("Diop").password("x").build();
        QcmPassage passage = QcmPassage.builder().qcm(qcm).student(awa).isSubmitted(true)
            .score(15).maxScore(20).submittedAt(LocalDateTime.of(2026, 9, 26, 10, 30))
            .declaredLastName("DIOP").declaredFirstName("Awa").birthDate(LocalDate.of(2004, 3, 12)).declaredLevel("L3")
            .build();
        when(qcmRepo.findById(1L)).thenReturn(Optional.of(qcm));
        when(passageRepo.findByQcmAndIsSubmittedTrue(qcm)).thenReturn(List.of(passage));

        byte[] bytes = controller.downloadReport(1L).getBody();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("Nom", header.getCell(0).getStringCellValue());
            assertEquals("Prénom", header.getCell(1).getStringCellValue());
            assertEquals("Date de naissance", header.getCell(2).getStringCellValue());
            assertEquals("Niveau", header.getCell(3).getStringCellValue());

            Row submitted = sheet.getRow(1);
            assertEquals("DIOP", submitted.getCell(0).getStringCellValue());
            assertEquals("Awa", submitted.getCell(1).getStringCellValue());
            assertEquals("12/03/2004", submitted.getCell(2).getStringCellValue());
            assertEquals("L3", submitted.getCell(3).getStringCellValue()); // niveau saisi par l'étudiant
            assertEquals(15, submitted.getCell(5).getNumericCellValue());
            assertEquals("Soumis", submitted.getCell(9).getStringCellValue());

            Row notSubmitted = sheet.getRow(2);
            assertEquals("Ndiaye", notSubmitted.getCell(0).getStringCellValue());
            assertEquals("Fatou", notSubmitted.getCell(1).getStringCellValue());
            assertEquals("L3", notSubmitted.getCell(3).getStringCellValue());
            assertEquals("Non soumis", notSubmitted.getCell(9).getStringCellValue());
        }
    }
}
