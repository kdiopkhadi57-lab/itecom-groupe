package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchoolServiceTest {

    private final SchoolFeeRepository feeRepo = mock(SchoolFeeRepository.class);
    private final SchoolEnrollmentRepository enrollmentRepo = mock(SchoolEnrollmentRepository.class);
    private final SchoolPaymentRepository paymentRepo = mock(SchoolPaymentRepository.class);
    private final SchoolGradeRepository gradeRepo = mock(SchoolGradeRepository.class);
    private final SchoolCertificateRepository certificateRepo = mock(SchoolCertificateRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private SchoolService service;

    private User student;
    private SchoolEnrollment enrollment;

    @BeforeEach
    void setUp() {
        service = new SchoolService(feeRepo, enrollmentRepo, paymentRepo, gradeRepo, certificateRepo, userRepo, notifications);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://itecom.test");
        student = User.builder().id(7L).firstName("Awa").lastName("Diop").email("awa@test.com")
            .role(Role.ROLE_STUDENT).birthDate(LocalDate.of(2004, 3, 12)).birthPlace("Dakar").build();
        enrollment = SchoolEnrollment.builder().id(3L).student(student).academicYear("2026-2027").level("L2")
            .specialization("comptabilite").matricule("ITC26-L2-0001").registrationFee(50_000).tuitionFee(450_000)
            .discount(0).installments(9).status("PENDING").createdAt(LocalDateTime.of(2026, 9, 15, 10, 0)).build();
        when(enrollmentRepo.findById(3L)).thenReturn(Optional.of(enrollment));
        when(paymentRepo.save(any(SchoolPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepo.saveAndFlush(any(SchoolPayment.class))).thenAnswer(inv -> {
            SchoolPayment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(42L);
            return p;
        });
        when(paymentRepo.findByVerificationCode(any())).thenReturn(Optional.empty());
        when(certificateRepo.findByVerificationCode(any())).thenReturn(Optional.empty());
    }

    @Test
    void academicYearStartsInSeptember() {
        assertEquals("2026-2027", SchoolService.currentAcademicYear(LocalDate.of(2026, 10, 4)));
        assertEquals("2025-2026", SchoolService.currentAcademicYear(LocalDate.of(2026, 8, 31)));
        assertEquals("2026-2027", SchoolService.normalizeYear("2026/2027"));
        assertThrows(IllegalArgumentException.class, () -> SchoolService.normalizeYear("2026-2028"));
    }

    @Test
    void scheduleAllocatesPaymentsToInstallmentsInOrder() {
        // 50 000 d'inscription + 9 mensualités de 50 000 ; 80 000 payés
        List<SchoolService.ScheduleItem> items = SchoolService.schedule(enrollment, 80_000, LocalDate.of(2026, 11, 10));
        assertEquals(10, items.size());
        assertEquals("PAID", items.get(0).status());
        assertEquals(30_000, items.get(1).paid());
        assertEquals("OVERDUE", items.get(1).status());          // 5 octobre dépassé, payée partiellement
        assertEquals("OVERDUE", items.get(2).status());          // 5 novembre dépassé
        assertEquals("DUE", items.get(3).status());              // 5 décembre à venir
        assertEquals(LocalDate.of(2027, 6, 5), items.get(9).dueDate());
        assertEquals(500_000, items.stream().mapToLong(SchoolService.ScheduleItem::amount).sum());
    }

    @Test
    void scheduleDeductsDiscountFromTuitionAndKeepsTotal() {
        enrollment.setDiscount(100_000);
        enrollment.setInstallments(3);
        List<SchoolService.ScheduleItem> items = SchoolService.schedule(enrollment, 0, LocalDate.of(2026, 9, 20));
        assertEquals(List.of(50_000L, 116_666L, 116_666L, 116_668L), items.stream().map(SchoolService.ScheduleItem::amount).toList());
        assertEquals(enrollment.totalDue(), items.stream().mapToLong(SchoolService.ScheduleItem::amount).sum());
    }

    @Test
    void transcriptKeepsBestSessionAndWeightsByCoefficient() {
        List<SchoolGrade> grades = List.of(
            grade("S1", "Comptabilité générale", 3, 8, "NORMALE"),
            grade("S1", "Comptabilité générale", 3, 12, "RATTRAPAGE"),
            grade("S1", "Droit", 1, 16, "NORMALE"),
            grade("S2", "Fiscalité", 2, 11, "NORMALE"));
        SchoolService.Transcript t = SchoolService.buildTranscript(grades);
        assertEquals(2, t.semesters().size());
        SchoolService.GradeLine compta = t.semesters().get(0).lines().get(0);
        assertEquals(8.0, compta.normale());
        assertEquals(12.0, compta.rattrapage());
        assertEquals(12.0, compta.effective());
        assertEquals(13.0, t.semesters().get(0).average());              // (12*3 + 16) / 4
        assertEquals(12.33, t.annualAverage());                           // (36 + 16 + 22) / 6
        assertEquals("Admis(e)", t.decision());
        assertEquals("Assez bien", t.mention());
    }

    @Test
    void amountsUsePlainSpaces() {
        assertEquals("1 250 000 FCFA", SchoolService.formatAmount(1_250_000));
        assertEquals("500 FCFA", SchoolService.formatAmount(500));
    }

    @Test
    void studentMobilePaymentIsPendingAndAdminsAreNotified() {
        when(paymentRepo.findByEnrollmentOrderBySubmittedAtDesc(enrollment)).thenReturn(List.of());
        when(paymentRepo.sumValidated(enrollment)).thenReturn(0L);
        SchoolPayment p = service.submitMobilePayment(student, 3L, 50_000, "wave", "77 123 45 67", "WAVE-TXN-001");
        assertEquals("PENDING", p.getStatus());
        assertEquals("INSCRIPTION", p.getPurpose());
        assertEquals("+221 77 123 45 67", p.getPhone());
        assertNull(p.getReceiptNumber());
        verify(notifications).notifyAdmins(eq("PAIEMENT"), anyString(), contains("WAVE-TXN-001"), anyString());
    }

    @Test
    void studentPaymentRefusesReusedReferenceOverpaymentAndOtherStudents() {
        when(paymentRepo.findByEnrollmentOrderBySubmittedAtDesc(enrollment)).thenReturn(List.of());
        when(paymentRepo.sumValidated(enrollment)).thenReturn(0L);
        when(paymentRepo.existsByTransactionRefIgnoreCaseAndStatusNot("USED-REF", "REJECTED")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
            () -> service.submitMobilePayment(student, 3L, 10_000, "WAVE", "771234567", "USED-REF"));
        assertThrows(IllegalArgumentException.class,
            () -> service.submitMobilePayment(student, 3L, 600_000, "WAVE", "771234567", "NEW-REF"));
        assertThrows(IllegalArgumentException.class,
            () -> service.submitMobilePayment(student, 3L, 10_000, "ESPECES", "771234567", "NEW-REF"));
        User other = User.builder().id(99L).firstName("X").lastName("Y").build();
        assertThrows(IllegalArgumentException.class,
            () -> service.submitMobilePayment(other, 3L, 10_000, "WAVE", "771234567", "NEW-REF"));
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void validatingRegistrationFeeActivatesEnrollmentAndIssuesReceipt() {
        SchoolPayment pending = SchoolPayment.builder().id(42L).enrollment(enrollment).amount(50_000).purpose("INSCRIPTION")
            .method("ORANGE_MONEY").transactionRef("OM-1").status("PENDING").submittedAt(LocalDateTime.now()).build();
        when(paymentRepo.findById(42L)).thenReturn(Optional.of(pending));
        when(paymentRepo.sumValidated(enrollment)).thenReturn(0L, 50_000L, 50_000L);
        SchoolPayment p = service.validatePayment(42L, "Admin ITECOM");
        assertEquals("VALIDATED", p.getStatus());
        assertEquals("REC-2026-000042", p.getReceiptNumber());
        assertEquals(12, p.getVerificationCode().length());
        assertEquals("ACTIVE", enrollment.getStatus());
        verify(notifications).notify(eq(student), eq("PAIEMENT"), eq("Paiement validé"), contains("REC-2026-000042"), eq("/scolarite"), eq(true));
        assertThrows(IllegalArgumentException.class, () -> service.validatePayment(42L, "Admin ITECOM"));
    }

    @Test
    void successCertificateRequiresPassingPublishedAverage() {
        when(gradeRepo.findByEnrollmentAndPublishedTrueOrderBySemesterAscSubjectAsc(enrollment))
            .thenReturn(List.of(grade("S1", "Droit", 1, 8, "NORMALE")));
        assertThrows(IllegalArgumentException.class, () -> service.issueCertificate(3L, "REUSSITE", "Admin"));
        // Attestation d'inscription : l'inscription doit être active
        assertThrows(IllegalArgumentException.class, () -> service.issueCertificate(3L, "INSCRIPTION", "Admin"));
        verify(certificateRepo, never()).saveAndFlush(any());
    }

    @Test
    void certificateAndReceiptPdfsContainStudentAndVerificationLink() throws Exception {
        enrollment.setStatus("ACTIVE");
        when(gradeRepo.findByEnrollmentAndPublishedTrueOrderBySemesterAscSubjectAsc(enrollment))
            .thenReturn(List.of(grade("S1", "Comptabilité générale", 3, 14.5, "NORMALE"), grade("S2", "Fiscalité", 2, 15, "NORMALE")));
        when(paymentRepo.findByEnrollmentOrderBySubmittedAtDesc(enrollment)).thenReturn(List.of());
        when(paymentRepo.sumValidated(enrollment)).thenReturn(50_000L);
        SchoolPdfService pdf = new SchoolPdfService(service);
        ReflectionTestUtils.setField(pdf, "schoolName", "ITECOM");
        ReflectionTestUtils.setField(pdf, "schoolCity", "Dakar");

        SchoolCertificate cert = SchoolCertificate.builder().id(5L).enrollment(enrollment).type("RELEVE_NOTES")
            .verificationCode("ABCDEFGH2345").reference("RLN-2026-000005").average(14.7).mention("Bien")
            .issuedAt(LocalDateTime.of(2027, 7, 1, 9, 0)).issuedBy("Admin ITECOM").build();
        String text = textOf(pdf.certificatePdf(cert));
        assertTrue(text.contains("RELEVÉ DE NOTES"));
        assertTrue(text.contains("DIOP Awa"));
        assertTrue(text.contains("ITC26-L2-0001"));
        assertTrue(text.contains("Comptabilité générale"));
        assertTrue(text.contains("https://itecom.test/verification/ABCDEFGH2345"));

        SchoolPayment p = SchoolPayment.builder().id(42L).enrollment(enrollment).amount(50_000).purpose("INSCRIPTION")
            .method("WAVE").phone("771234567").transactionRef("WAVE-1").status("VALIDATED").receiptNumber("REC-2026-000042")
            .verificationCode("ZZZZYYYYXXXX").processedAt(LocalDateTime.now()).submittedAt(LocalDateTime.now()).build();
        String receipt = textOf(pdf.receiptPdf(p));
        assertTrue(receipt.contains("REÇU DE PAIEMENT"));
        assertTrue(receipt.contains("50 000 FCFA"));
        assertTrue(receipt.contains("450 000 FCFA"));   // reste à payer
    }

    private static String textOf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private SchoolGrade grade(String semester, String subject, double coef, double value, String session) {
        return SchoolGrade.builder().enrollment(enrollment).semester(semester).subject(subject).coefficient(coef)
            .grade(value).session(session).published(true).build();
    }
}
