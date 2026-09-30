package com.elearning.controller;

import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import com.elearning.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminUsersControllerTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final com.elearning.service.ExamService examService = mock(com.elearning.service.ExamService.class);
    private AdminUsersController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUsersController(userRepo, mock(EmailService.class), new BCryptPasswordEncoder(4), examService);
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AdminUsersController.CreateUserInput student(String birthDate, String place, String level) {
        AdminUsersController.CreateUserInput in = new AdminUsersController.CreateUserInput();
        in.setRole("STUDENT"); in.setFirstName("Awa"); in.setLastName("Diop"); in.setEmail("awa@test.com");
        in.setBirthDate(birthDate); in.setBirthPlace(place); in.setLevel(level);
        return in;
    }

    @Test
    void createsStudentWithBirthInformationAndLevel() {
        var response = controller.create(student("2004-03-12", "Dakar", "l3"));
        assertEquals(200, response.getStatusCode().value());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        assertEquals(LocalDate.of(2004, 3, 12), saved.getValue().getBirthDate());
        assertEquals("Dakar", saved.getValue().getBirthPlace());
        assertEquals("L3", saved.getValue().getLevel());
        assertNull(saved.getValue().getSubjects());
        // Les examens ciblant le niveau L3 l'inscrivent aussitôt
        verify(examService).enrollNewStudent(saved.getValue());
    }

    @Test
    void refusesStudentWithoutBirthPlaceOrWithUnknownLevel() {
        assertEquals(400, controller.create(student("2004-03-12", "", "L3")).getStatusCode().value());
        assertEquals(400, controller.create(student("2004-03-12", "Dakar", "M3")).getStatusCode().value());
        verify(userRepo, never()).save(any());
    }

    @Test
    void teacherRequiresSubjects() {
        AdminUsersController.CreateUserInput in = new AdminUsersController.CreateUserInput();
        in.setRole("TEACHER"); in.setFirstName("Cheikh"); in.setLastName("Ndiaye"); in.setEmail("prof@test.com");
        assertEquals(400, controller.create(in).getStatusCode().value());

        in.setSubjects("Comptabilité, Fiscalité");
        assertEquals(200, controller.create(in).getStatusCode().value());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        assertEquals(Role.ROLE_TEACHER, saved.getValue().getRole());
        assertEquals("Comptabilité, Fiscalité", saved.getValue().getSubjects());
        assertNull(saved.getValue().getLevel());
    }
}
