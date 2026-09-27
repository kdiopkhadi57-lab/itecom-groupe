package com.elearning.controller;

import com.elearning.entity.Qcm;
import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.QcmPassageRepository;
import com.elearning.repository.QcmRepository;
import com.elearning.repository.UserRepository;
import com.elearning.service.DocumentTextExtractorService;
import com.elearning.service.FileStorageService;
import com.elearning.service.StudentListParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QcmControllerStudentAccountsTest {

    private final QcmRepository qcmRepo = mock(QcmRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("prof@test.com", null, List.of());
    private QcmController controller;

    @BeforeEach
    void setUp() {
        controller = new QcmController(qcmRepo, mock(QcmPassageRepository.class), userRepo,
            mock(StudentListParserService.class), mock(DocumentTextExtractorService.class),
            mock(FileStorageService.class), encoder);
        User prof = User.builder().email("prof@test.com").firstName("P").lastName("Prof")
            .password("x").role(Role.ROLE_TEACHER).build();
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepo.findByEmail("prof@test.com")).thenReturn(Optional.of(prof));
        when(qcmRepo.save(any(Qcm.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private QcmController.QcmInput input(String email, String password) {
        QcmController.StudentInput s = new QcmController.StudentInput();
        s.setEmail(email); s.setFirstName("Awa"); s.setLastName("Diop"); s.setLevel("L3"); s.setPassword(password);
        QcmController.QcmInput in = new QcmController.QcmInput();
        in.setTitle("Devoir"); in.setStudents(List.of(s));
        return in;
    }

    @Test
    void createsEnabledStudentAccountWithListPassword() {
        controller.create(input("new@test.com", "Test-Pass-123"), auth);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        User u = saved.getValue();
        assertEquals("new@test.com", u.getEmail());
        assertEquals(Role.ROLE_STUDENT, u.getRole());
        assertTrue(u.isEnabled());
        assertEquals("APPROVED", u.getRegistrationStatus());
        assertTrue(encoder.matches("Test-Pass-123", u.getPassword()));
    }

    @Test
    void replacesPasswordOfExistingStudent() {
        User existing = User.builder().email("etudiant.existant@test.com").firstName("Awa").lastName("Diop")
            .password(encoder.encode("Ancien-Pass-1")).role(Role.ROLE_STUDENT)
            .enabled(true).registrationStatus("APPROVED").build();
        when(userRepo.findByEmail("etudiant.existant@test.com")).thenReturn(Optional.of(existing));

        controller.create(input("etudiant.existant@test.com", "Test-Pass-123"), auth);

        assertTrue(encoder.matches("Test-Pass-123", existing.getPassword()));
        verify(userRepo).save(existing);
    }

    @Test
    void refusesTeacherOrAdminEmailInStudentList() {
        User admin = User.builder().email("admin@test.com").firstName("A").lastName("B")
            .password(encoder.encode("Admin-Pass-1")).role(Role.ROLE_ADMIN).build();
        when(userRepo.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        var response = controller.create(input("admin@test.com", "Test-Pass-123"), auth);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(encoder.matches("Admin-Pass-1", admin.getPassword()));
        verify(userRepo, never()).save(any());
    }
}
