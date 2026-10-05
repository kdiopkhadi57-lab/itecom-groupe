package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.VirtualClassAttendanceRepository;
import com.elearning.repository.VirtualClassRollCallAnswerRepository;
import com.elearning.repository.VirtualClassRollCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VirtualClassRollCallServiceTest {

    private final VirtualClassRollCallRepository calls = mock(VirtualClassRollCallRepository.class);
    private final VirtualClassRollCallAnswerRepository answers = mock(VirtualClassRollCallAnswerRepository.class);
    private final VirtualClassAttendanceRepository attendances = mock(VirtualClassAttendanceRepository.class);
    private final StudentAudienceService audience = mock(StudentAudienceService.class);
    private VirtualClassRollCallService service;
    private VirtualClass vc;

    @BeforeEach
    void setUp() {
        service = new VirtualClassRollCallService(calls, answers, attendances, audience);
        vc = VirtualClass.builder().id(9L).title("Comptabilité").build();
        when(calls.save(any(VirtualClassRollCall.class))).thenAnswer(inv -> { VirtualClassRollCall c = inv.getArgument(0); c.setId(1L); return c; });
        LinkedHashMap<String, String> expected = new LinkedHashMap<>();
        expected.put("awa@test.com", "Awa Diop");
        expected.put("moussa@test.com", "Moussa Fall");
        expected.put("binta@test.com", "Binta Sarr");
        when(audience.virtualClassAudience(vc)).thenAnswer(inv -> new LinkedHashMap<>(expected));
    }

    private VirtualClassAttendance online(String email, String name) {
        return VirtualClassAttendance.builder().virtualClass(vc).email(email).fullName(name).role(Role.ROLE_STUDENT)
            .joinedAt(LocalDateTime.now().minusMinutes(20)).lastSeenAt(LocalDateTime.now().minusSeconds(10)).build();
    }

    @Test
    void startClosesPreviousCallAndOpensAThreeMinuteWindow() {
        VirtualClassRollCall previous = VirtualClassRollCall.builder().id(0L).virtualClass(vc)
            .startedAt(LocalDateTime.now().minusMinutes(1)).expiresAt(LocalDateTime.now().plusMinutes(2)).build();
        when(calls.findByVirtualClassOrderByStartedAtAsc(vc)).thenReturn(List.of(previous));
        when(attendances.findByVirtualClassOrderByJoinedAtAsc(vc)).thenReturn(List.of());
        var view = service.start(vc, "prof@test.com");
        assertFalse(previous.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(view.open());
        assertEquals(3, java.time.Duration.between(view.startedAt(), view.expiresAt()).toMinutes());
    }

    @Test
    void viewSeparatesPresentConnectedWithoutAnswerAndNotConnected() {
        VirtualClassRollCall call = VirtualClassRollCall.builder().id(1L).virtualClass(vc)
            .startedAt(LocalDateTime.now().minusMinutes(1)).expiresAt(LocalDateTime.now().plusMinutes(2)).build();
        when(attendances.findByVirtualClassOrderByJoinedAtAsc(vc)).thenReturn(List.of(
            online("awa@test.com", "Awa Diop"), online("moussa@test.com", "Moussa Fall")));
        when(answers.findByRollCall(call)).thenReturn(List.of(VirtualClassRollCallAnswer.builder().rollCall(call)
            .email("awa@test.com").fullName("Awa Diop").answeredAt(LocalDateTime.now()).build()));

        var view = service.view(call);
        assertEquals(3, view.expected());
        assertEquals(1, view.present());
        assertEquals(1, view.noAnswer());
        assertEquals(1, view.notConnected());
        // Ceux qui n'ont pas répondu en premier, pour que le professeur les voie
        assertEquals("NO_ANSWER", view.students().get(0).status());
        assertEquals("Moussa Fall", view.students().get(0).name());
    }

    @Test
    void lateAnswerIsRefusedAndAnsweredCallIsNoLongerActive() {
        VirtualClassRollCall closed = VirtualClassRollCall.builder().id(1L).virtualClass(vc)
            .startedAt(LocalDateTime.now().minusMinutes(5)).expiresAt(LocalDateTime.now().minusMinutes(2)).build();
        User awa = User.builder().email("awa@test.com").firstName("Awa").lastName("Diop").build();
        assertThrows(IllegalArgumentException.class, () -> service.answer(closed, awa));
        verify(answers, never()).save(any());

        VirtualClassRollCall open = VirtualClassRollCall.builder().id(2L).virtualClass(vc)
            .startedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(3)).build();
        when(calls.findByVirtualClassOrderByStartedAtAsc(vc)).thenReturn(List.of(closed, open));
        assertEquals(2L, service.activeFor(vc, "awa@test.com").orElseThrow().id());
        when(answers.existsByRollCallAndEmailIgnoreCase(open, "awa@test.com")).thenReturn(true);
        assertTrue(service.activeFor(vc, "awa@test.com").isEmpty());
    }
}
