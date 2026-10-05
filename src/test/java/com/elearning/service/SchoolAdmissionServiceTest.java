package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.SchoolDocumentRepository;
import com.elearning.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchoolAdmissionServiceTest {

    @TempDir Path dir;

    private final SchoolService school = mock(SchoolService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SchoolDocumentRepository documents = mock(SchoolDocumentRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final ExamService exams = mock(ExamService.class);
    private SchoolAdmissionService service;

    @BeforeEach
    void setUp() {
        EmailDomainChecker domains = new EmailDomainChecker();
        ReflectionTestUtils.setField(domains, "enabled", false);   // pas d'appel DNS en test
        service = new SchoolAdmissionService(school, users, documents, new BCryptPasswordEncoder(4), email, exams, domains);
        ReflectionTestUtils.setField(service, "documentsDir", dir.toString());
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.findByBirthDateAndLastNameIgnoreCaseAndFirstNameIgnoreCase(any(), any(), any())).thenReturn(List.of());
        when(users.save(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(12L); return u; });
        when(school.enroll(eq(12L), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> SchoolEnrollment.builder()
            .id(4L).academicYear("2026-2027").level("L1").matricule("ITC26-L1-0004").status("PENDING").build());
        when(documents.save(any(SchoolDocument.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SchoolAdmissionService.NewStudent student(String profile, String birthDate) {
        return new SchoolAdmissionService.NewStudent("Awa", "Diop", birthDate, "Thiès", "Awa.Diop@Test.com", "77 123 45 67",
            "2026-2027", "l1", "comptabilite", null, profile);
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("f", name, "application/pdf", "%PDF-1.7 contenu".getBytes());
    }

    @Test
    void newBachelorGetsAccountEnrollmentAndBothBacDocuments() throws Exception {
        var result = service.admit(student("NEW_BACHELOR", "1958-02-01"), pdf("bac.pdf"), pdf("releve-bac.pdf"), null, null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User u = saved.getValue();
        assertEquals("awa.diop@test.com", u.getEmail());
        assertEquals(LocalDate.of(1958, 2, 1), u.getBirthDate());   // aucune limite d'âge
        assertEquals("Thiès", u.getBirthPlace());
        assertEquals("+221 77 123 45 67", u.getPhone());
        assertEquals(Role.ROLE_STUDENT, u.getRole());
        assertTrue(u.isEnabled());
        verify(school).enroll(12L, "2026-2027", "L1", "comptabilite", null, null, null);

        ArgumentCaptor<SchoolDocument> docs = ArgumentCaptor.forClass(SchoolDocument.class);
        verify(documents, times(2)).save(docs.capture());
        assertEquals(List.of("BAC_ATTESTATION", "BAC_TRANSCRIPT"), docs.getAllValues().stream().map(SchoolDocument::getType).toList());
        for (SchoolDocument d : docs.getAllValues()) assertTrue(Files.exists(dir.resolve(d.getStoredName())));
        verify(email).sendAccountCreated(eq("awa.diop@test.com"), eq("Awa"), eq("étudiant"), anyString());
        assertTrue(result.emailSent());
    }

    @Test
    void transferStudentGivesLastYearTranscriptsAndSuccessAttestationButNoBac() throws Exception {
        // Sans relevés, ou sans attestation de réussite : refusé, même avec les pièces du bac
        assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("TRANSFER", "2004-03-12"), pdf("bac.pdf"), pdf("releve-bac.pdf"), List.of(), pdf("reussite.pdf")));
        assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("TRANSFER", "2004-03-12"), null, null, List.of(pdf("L1-S1.pdf")), null));
        verify(users, never()).save(any());

        service.admit(student("TRANSFER", "2004-03-12"), null, null, List.of(pdf("L1-S1.pdf"), pdf("L1-S2.pdf")), pdf("reussite.pdf"));
        ArgumentCaptor<SchoolDocument> docs = ArgumentCaptor.forClass(SchoolDocument.class);
        verify(documents, times(3)).save(docs.capture());
        assertEquals(List.of("PREVIOUS_TRANSCRIPT", "PREVIOUS_TRANSCRIPT", "SUCCESS_ATTESTATION"),
            docs.getAllValues().stream().map(SchoolDocument::getType).toList());
    }

    @Test
    void refusesFancifulEmailAndPhone() {
        var badEmail = new SchoolAdmissionService.NewStudent("Awa", "Diop", "2004-03-12", "Thiès", "awa@gmial.com", null,
            "2026-2027", "L1", null, null, "NEW_BACHELOR");
        var e = assertThrows(IllegalArgumentException.class, () -> service.admit(badEmail, pdf("a.pdf"), pdf("b.pdf"), null, null));
        assertTrue(e.getMessage().contains("awa@gmail.com"));
        var badPhone = new SchoolAdmissionService.NewStudent("Awa", "Diop", "2004-03-12", "Thiès", "awa@test.com", "12345",
            "2026-2027", "L1", null, null, "NEW_BACHELOR");
        assertThrows(IllegalArgumentException.class, () -> service.admit(badPhone, pdf("a.pdf"), pdf("b.pdf"), null, null));
        verify(users, never()).save(any());
    }

    @Test
    void refusesExistingStudentsMissingOrFakePdfsAndFutureBirthDate() {
        when(users.findByEmail("awa.diop@test.com")).thenReturn(Optional.of(new User()));
        assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("NEW_BACHELOR", "2004-03-12"), pdf("a.pdf"), pdf("b.pdf"), null, null));
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        when(users.findByBirthDateAndLastNameIgnoreCaseAndFirstNameIgnoreCase(LocalDate.of(2004, 3, 12), "Diop", "Awa"))
            .thenReturn(List.of(User.builder().email("ancien@test.com").build()));
        var homonym = assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("NEW_BACHELOR", "2004-03-12"), pdf("a.pdf"), pdf("b.pdf"), null, null));
        assertTrue(homonym.getMessage().contains("existe déjà"));

        assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("NEW_BACHELOR", "2005-01-01"), pdf("a.pdf"), null, null, null));
        var fake = new MockMultipartFile("f", "bac.pdf", "application/pdf", "pas un pdf".getBytes());
        assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("NEW_BACHELOR", "2005-01-01"), fake, pdf("b.pdf"), null, null));
        assertThrows(IllegalArgumentException.class,
            () -> service.admit(student("NEW_BACHELOR", LocalDate.now().plusDays(1).toString()), pdf("a.pdf"), pdf("b.pdf"), null, null));
        verify(users, never()).save(any());
    }
}
