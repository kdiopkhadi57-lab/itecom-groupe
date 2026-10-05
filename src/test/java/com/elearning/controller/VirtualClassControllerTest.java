package com.elearning.controller;

import com.elearning.dto.response.VirtualClassResponse;
import com.elearning.entity.VirtualClass;
import com.elearning.repository.CourseRepository;
import com.elearning.repository.UserRepository;
import com.elearning.repository.VirtualClassRepository;
import com.elearning.service.JitsiTokenService;
import com.elearning.service.StudentAudienceService;
import com.elearning.service.StudentListParserService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VirtualClassControllerTest {

    private final VirtualClassRepository repo = mock(VirtualClassRepository.class);
    private final StudentAudienceService audience = mock(StudentAudienceService.class);
    private final VirtualClassController controller = new VirtualClassController(repo, mock(UserRepository.class),
        mock(CourseRepository.class), mock(StudentListParserService.class), mock(JitsiTokenService.class), audience,
        mock(com.elearning.service.RecordingStorageService.class));

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

        VirtualClassResponse r = controller.getById(4L).getBody();
        assertEquals("Comptabilité analytique", r.getTitle());
        assertEquals(2, r.getStudentCount());
        assertEquals(1, controller.getAll().getBody().size());
    }
}
