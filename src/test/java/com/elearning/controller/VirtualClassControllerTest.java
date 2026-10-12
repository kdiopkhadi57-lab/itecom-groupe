package com.elearning.controller;

import com.elearning.dto.response.VirtualClassResponse;
import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.entity.VirtualClass;
import com.elearning.repository.CourseRepository;
import com.elearning.repository.UserRepository;
import com.elearning.repository.VirtualClassRepository;
import com.elearning.service.JitsiTokenService;
import com.elearning.service.StudentAudienceService;
import com.elearning.service.StudentListParserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VirtualClassControllerTest {

    private final VirtualClassRepository repo = mock(VirtualClassRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentAudienceService audience = mock(StudentAudienceService.class);
    private final VirtualClassController controller = new VirtualClassController(repo, users,
        mock(CourseRepository.class), mock(StudentListParserService.class), mock(JitsiTokenService.class), audience,
        mock(com.elearning.service.RecordingStorageService.class));

    private UserDetails as(Long id, Role role, String level) {
        String email = "u" + id + "@test.com";
        User u = User.builder().id(id).email(email).role(role).level(level).build();
        when(users.findByEmail(email)).thenReturn(Optional.of(u));
        return org.springframework.security.core.userdetails.User.withUsername(email).password("x").roles(role.name().substring(5)).build();
    }

    private VirtualClass vc(Long id, Long teacherId, String levels) {
        User teacher = User.builder().id(teacherId).email("t" + teacherId + "@test.com").role(Role.ROLE_TEACHER).build();
        return VirtualClass.builder().id(id).title("Séance " + id).teacher(teacher).targetLevels(levels).students(new ArrayList<>()).build();
    }

    /** Ouvrir une séance renvoyait « Une erreur inattendue » : toResponse s'appelait lui-même à l'infini. */
    @Test
    void openingAClassReturnsItWithItsAudienceSize() {
        VirtualClass vc = VirtualClass.builder().id(4L).title("Comptabilité analytique").targetLevels("L2").build();
        when(repo.findById(4L)).thenReturn(Optional.of(vc));
        when(repo.findAll()).thenReturn(List.of(vc));
        LinkedHashMap<String, String> students = new LinkedHashMap<>();
        students.put("awa@test.com", "Awa Diop");
        students.put("moussa@test.com", "Moussa Fall");
        when(audience.virtualClassAudience(vc)).thenReturn(students);

        UserDetails admin = as(1L, Role.ROLE_ADMIN, null);
        VirtualClassResponse r = controller.getById(4L, admin).getBody();
        assertEquals("Comptabilité analytique", r.getTitle());
        assertEquals(2, r.getStudentCount());
        assertEquals(1, controller.getAll(admin).getBody().size());
    }

    /** Professeur : ses séances seulement ; étudiant : celles de son niveau. */
    @Test
    void eachOneSeesOnlyTheirClasses() {
        when(repo.findAll()).thenReturn(List.of(vc(1L, 10L, "L1"), vc(2L, 11L, "L2"), vc(3L, 10L, "L2")));
        when(audience.virtualClassAudience(any())).thenReturn(new LinkedHashMap<>());

        assertEquals(List.of(1L, 3L), controller.getAll(as(10L, Role.ROLE_TEACHER, null)).getBody().stream().map(VirtualClassResponse::getId).toList());
        assertEquals(List.of(2L, 3L), controller.getAll(as(20L, Role.ROLE_STUDENT, "L2")).getBody().stream().map(VirtualClassResponse::getId).toList());
        assertEquals(3, controller.getAll(as(1L, Role.ROLE_ADMIN, null)).getBody().size());
    }
}
