package com.elearning.service;

import com.elearning.entity.Qcm;
import com.elearning.entity.QcmStudent;
import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QcmAudienceServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final QcmAudienceService service = new QcmAudienceService(new StudentAudienceService(userRepo));

    private static User student(String email, String level) {
        return User.builder().email(email).firstName("Prénom").lastName("Nom").password("x")
            .role(Role.ROLE_STUDENT).level(level).registrationStatus("APPROVED").enabled(true).build();
    }

    @Test
    void levelStudentsJoinTheListWithoutDuplicates() {
        Qcm qcm = Qcm.builder().title("Devoir").targetLevels("L1").build();
        qcm.getAssignedStudents().add(QcmStudent.builder().qcm(qcm).studentName("Awa")
            .studentEmail("awa@test.com").level("L1").build());
        when(userRepo.findByRoleAndRegistrationStatusAndLevelIn(eq(Role.ROLE_STUDENT), eq("APPROVED"), any()))
            .thenReturn(List.of(student("awa@test.com", "L1"), student("moussa@test.com", "L1")));

        List<QcmStudent> audience = service.audience(qcm);

        assertEquals(List.of("awa@test.com", "moussa@test.com"), audience.stream().map(QcmStudent::getStudentEmail).toList());
    }

    @Test
    void studentOfTargetedLevelHasAccessEvenIfCreatedLater() {
        Qcm qcm = Qcm.builder().title("Devoir").targetLevels("L1,L2").build();

        assertTrue(service.canAccess(qcm, student("nouveau@test.com", "L2")));
        assertFalse(service.canAccess(qcm, student("autre@test.com", "M1")));
    }

    @Test
    void devoirWithoutListNorLevelIsOpenToAll() {
        Qcm qcm = Qcm.builder().title("Devoir").build();

        assertTrue(service.canAccess(qcm, student("x@test.com", null)));
    }
}
