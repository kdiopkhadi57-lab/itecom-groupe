package com.elearning.controller;

import com.elearning.entity.Qcm;
import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.QcmPassageRepository;
import com.elearning.repository.QcmRepository;
import com.elearning.repository.UserRepository;
import com.elearning.service.DocumentTextExtractorService;
import com.elearning.service.FileStorageService;
import com.elearning.service.QcmAudienceService;
import com.elearning.service.StudentAudienceService;
import com.elearning.service.StudentListParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QcmControllerStudentAccountsTest {

    private final QcmRepository qcmRepo = mock(QcmRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("prof@test.com", null, List.of());
    private QcmController controller;

    @BeforeEach
    void setUp() {
        controller = new QcmController(qcmRepo, mock(QcmPassageRepository.class), userRepo,
            mock(StudentListParserService.class), mock(DocumentTextExtractorService.class),
            mock(FileStorageService.class), mock(com.elearning.service.QcmSubmissionService.class),
            new com.fasterxml.jackson.databind.ObjectMapper(),
            mock(com.elearning.service.PaperCopyService.class),
            new QcmAudienceService(new StudentAudienceService(userRepo)));
        User prof = User.builder().email("prof@test.com").firstName("P").lastName("Prof")
            .password("x").role(Role.ROLE_TEACHER).build();
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepo.findByEmail("prof@test.com")).thenReturn(Optional.of(prof));
        when(qcmRepo.save(any(Qcm.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private QcmController.QcmInput input(String email) {
        QcmController.StudentInput s = new QcmController.StudentInput();
        s.setEmail(email); s.setFirstName("Awa"); s.setLastName("Diop"); s.setLevel("L3");
        QcmController.QcmInput in = new QcmController.QcmInput();
        in.setTitle("Devoir"); in.setStudents(List.of(s));
        return in;
    }

    @Test
    void refusesEmailWithoutStudentAccount() {
        var response = controller.create(input("inconnu@test.com"), auth);

        assertEquals(400, response.getStatusCode().value());
        verify(userRepo, never()).save(any());
        verify(qcmRepo, never()).save(any());
    }

    @Test
    void keepsPasswordOfExistingStudent() {
        User existing = User.builder().email("etudiant.existant@test.com").firstName("Awa").lastName("Diop")
            .password("hash-du-compte").role(Role.ROLE_STUDENT)
            .enabled(true).registrationStatus("APPROVED").build();
        when(userRepo.findByEmail("etudiant.existant@test.com")).thenReturn(Optional.of(existing));

        var response = controller.create(input("etudiant.existant@test.com"), auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hash-du-compte", existing.getPassword());
        verify(userRepo, never()).save(any());
        ArgumentCaptor<Qcm> saved = ArgumentCaptor.forClass(Qcm.class);
        verify(qcmRepo).save(saved.capture());
        assertEquals(1, saved.getValue().getAssignedStudents().size());
        assertNull(saved.getValue().getAssignedStudents().get(0).getAccessPassword());
    }

    @Test
    void refusesTeacherOrAdminEmailInStudentList() {
        User admin = User.builder().email("admin@test.com").firstName("A").lastName("B")
            .password("hash-admin").role(Role.ROLE_ADMIN).build();
        when(userRepo.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        var response = controller.create(input("admin@test.com"), auth);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("hash-admin", admin.getPassword());
        verify(userRepo, never()).save(any());
    }

    @Test
    void storesTargetLevelsInCurriculumOrder() {
        QcmController.QcmInput in = new QcmController.QcmInput();
        in.setTitle("Devoir"); in.setTargetLevels(List.of("m1", "L1", "X9", "L1"));

        controller.create(in, auth);

        ArgumentCaptor<Qcm> saved = ArgumentCaptor.forClass(Qcm.class);
        verify(qcmRepo).save(saved.capture());
        assertEquals("L1,M1", saved.getValue().getTargetLevels());
        assertFalse(saved.getValue().isOpenToAll());
    }
}
