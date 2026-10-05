package com.elearning.controller;

import com.elearning.entity.SchoolCertificate;
import com.elearning.entity.SchoolDocument;
import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.SchoolPayment;
import com.elearning.repository.UserRepository;
import com.elearning.service.SchoolAdmissionService;
import com.elearning.service.SchoolPdfService;
import com.elearning.service.SchoolService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/** Gestion de la scolarité par l'administration. */
@RestController
@RequestMapping("/api/admin/scolarite")
@RequiredArgsConstructor
public class AdminSchoolController {

    private final SchoolService schoolService;
    private final SchoolPdfService pdfService;
    private final SchoolAdmissionService admissionService;
    private final UserRepository userRepository;

    private String adminName(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .map(u -> u.getFirstName() + " " + u.getLastName()).orElse(principal.getUsername());
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("currentYear", SchoolService.currentAcademicYear(LocalDate.now()));
        r.put("levels", SchoolService.LEVELS);
        r.put("methods", SchoolService.ALL_METHODS);
        r.put("certificateTypes", SchoolService.CERTIFICATE_TYPES);
        return r;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) String year) {
        return schoolService.stats(year);
    }

    // ── Frais ──
    @Data
    public static class FeeInput {
        private String academicYear;
        private String level;
        private Long registrationFee;
        private Long monthlyFee;
    }

    @GetMapping("/fees")
    public Map<String, Object> fees(@RequestParam(required = false) String year) {
        return Map.of("months", schoolService.months(), "fees", schoolService.listFees(year));
    }

    /** Montant de l'inscription et mensualité d'un niveau (créé ou remplacé). */
    @PutMapping("/fees")
    public SchoolService.FeeView saveFee(@RequestBody FeeInput in) {
        return schoolService.saveFee(in.getAcademicYear(), in.getLevel(), in.getRegistrationFee(), in.getMonthlyFee());
    }

    @DeleteMapping("/fees/{id}")
    public ResponseEntity<Void> deleteFee(@PathVariable Long id) {
        schoolService.deleteFee(id);
        return ResponseEntity.noContent().build();
    }

    // ── Inscriptions ──
    @Data
    public static class EnrollInput {
        private String academicYear;
        private String level;
        private Long discount;
        private String status;
        private Integer installments;
    }

    @GetMapping("/enrollments")
    public List<SchoolService.EnrollmentView> enrollments(@RequestParam(required = false) String year) {
        return schoolService.listEnrollments(year);
    }

    @GetMapping("/enrollments/{id}")
    public Map<String, Object> enrollment(@PathVariable Long id) {
        SchoolEnrollment e = schoolService.getEnrollment(id);
        SchoolService.EnrollmentView v = schoolService.view(e);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enrollment", v);
        r.put("schedule", SchoolService.schedule(e, v.paid(), LocalDate.now()));
        r.put("payments", schoolService.paymentsOf(e));
        r.put("transcript", schoolService.transcript(e, false));
        r.put("certificates", schoolService.certificatesOf(e));
        r.put("documents", admissionService.documents(e));
        return r;
    }

    /** Nouvel étudiant : compte, inscription et pièces scannées en PDF (formulaire multipart). */
    @PostMapping(value = "/enrollments/new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SchoolAdmissionService.Admission admit(
            @RequestParam String firstName, @RequestParam String lastName,
            @RequestParam String birthDate, @RequestParam String birthPlace,
            @RequestParam String email, @RequestParam(required = false) String phone,
            @RequestParam String academicYear, @RequestParam String level,
            @RequestParam(required = false) String specialization, @RequestParam(required = false) Long discount,
            @RequestParam(defaultValue = SchoolAdmissionService.NEW_BACHELOR) String profile,
            @RequestParam(required = false) MultipartFile bacAttestation,
            @RequestParam(required = false) MultipartFile bacTranscript,
            @RequestParam(required = false) List<MultipartFile> previousTranscripts,
            @RequestParam(required = false) MultipartFile successAttestation) throws IOException {
        return admissionService.admit(new SchoolAdmissionService.NewStudent(firstName, lastName, birthDate, birthPlace, email,
                phone, academicYear, level, specialization, discount, profile),
            bacAttestation, bacTranscript, previousTranscripts, successAttestation);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> document(@PathVariable Long id) throws IOException {
        SchoolDocument d = admissionService.getDocument(id);
        return pdf(admissionService.read(d), d.getOriginalName().replaceAll("(?i)\\.pdf$", ""));
    }

    @PostMapping("/enrollments/level")
    public Map<String, Object> enrollLevel(@RequestBody EnrollInput in) {
        return schoolService.enrollLevel(in.getAcademicYear(), in.getLevel());
    }

    @PutMapping("/enrollments/{id}")
    public SchoolService.EnrollmentView updateEnrollment(@PathVariable Long id, @RequestBody EnrollInput in) {
        return schoolService.view(schoolService.updateEnrollment(id, in.getStatus(), in.getDiscount(), in.getInstallments(), in.getLevel()));
    }

    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable Long id) throws IOException {
        admissionService.deleteEnrollment(id);
        return ResponseEntity.noContent().build();
    }

    // ── Paiements ──
    @Data
    public static class PaymentInput {
        private Long enrollmentId;
        private Long amount;
        private String method;
        private String transactionRef;
        private String reason;
    }

    @GetMapping("/payments")
    public List<SchoolService.PaymentView> payments(@RequestParam(required = false) String status) {
        return schoolService.listPayments(status);
    }

    @PostMapping("/payments")
    public SchoolService.PaymentView recordPayment(@RequestBody PaymentInput in, @AuthenticationPrincipal UserDetails principal) {
        if (in.getEnrollmentId() == null || in.getAmount() == null) throw new IllegalArgumentException("Inscription et montant requis.");
        return SchoolService.paymentView(schoolService.recordPayment(adminName(principal), in.getEnrollmentId(),
            in.getAmount(), in.getMethod(), in.getTransactionRef()));
    }

    @PostMapping("/payments/{id}/validate")
    public SchoolService.PaymentView validate(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return SchoolService.paymentView(schoolService.validatePayment(id, adminName(principal)));
    }

    @PostMapping("/payments/{id}/reject")
    public SchoolService.PaymentView reject(@PathVariable Long id, @RequestBody(required = false) PaymentInput in,
                                            @AuthenticationPrincipal UserDetails principal) {
        return SchoolService.paymentView(schoolService.rejectPayment(id, in == null ? null : in.getReason(), adminName(principal)));
    }

    @GetMapping("/payments/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) throws IOException {
        SchoolPayment p = schoolService.getPayment(id);
        if (!"VALIDATED".equals(p.getStatus())) throw new IllegalArgumentException("Seul un paiement validé a un reçu.");
        return pdf(pdfService.receiptPdf(p), p.getReceiptNumber());
    }

    // ── Notes ──
    @Data
    public static class GradeSheetInput {
        private String academicYear;
        private String level;
        private String semester;
        private String subject;
        private String session;
        private Double coefficient;
        private List<SchoolService.GradeEntry> entries;
    }

    @GetMapping("/grades/subjects")
    public List<String> subjects(@RequestParam String year, @RequestParam String level) {
        return schoolService.subjects(year, level);
    }

    @GetMapping("/grades")
    public Map<String, Object> gradeSheet(@RequestParam String year, @RequestParam String level, @RequestParam String semester,
                                          @RequestParam String subject, @RequestParam(required = false) String session) {
        return schoolService.gradeSheet(year, level, semester, subject, session);
    }

    @PutMapping("/grades")
    public Map<String, Object> saveGrades(@RequestBody GradeSheetInput in) {
        int saved = schoolService.saveGradeSheet(in.getAcademicYear(), in.getLevel(), in.getSemester(), in.getSubject(),
            in.getSession(), in.getCoefficient() == null ? 1 : in.getCoefficient(), in.getEntries());
        return Map.of("saved", saved);
    }

    @PostMapping("/grades/publish")
    public Map<String, Object> publish(@RequestBody GradeSheetInput in) {
        return Map.of("published", schoolService.publishGrades(in.getAcademicYear(), in.getLevel(), in.getSemester()));
    }

    // ── Attestations ──
    @Data
    public static class CertificateInput {
        private Long enrollmentId;
        private String type;
    }

    @GetMapping("/certificates")
    public List<SchoolService.CertificateView> certificates() { return schoolService.listCertificates(); }

    @PostMapping("/certificates")
    public SchoolService.CertificateView issue(@RequestBody CertificateInput in, @AuthenticationPrincipal UserDetails principal) {
        if (in.getEnrollmentId() == null) throw new IllegalArgumentException("Choisissez une inscription.");
        return schoolService.view(schoolService.issueCertificate(in.getEnrollmentId(), in.getType(), adminName(principal)));
    }

    @PostMapping("/certificates/{id}/revoke")
    public SchoolService.CertificateView revoke(@PathVariable Long id) {
        return schoolService.view(schoolService.revokeCertificate(id));
    }

    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id) throws IOException {
        SchoolCertificate c = schoolService.getCertificate(id);
        return pdf(pdfService.certificatePdf(c), c.getReference());
    }

    // ── Relances et annonces ──
    @Data
    public static class BroadcastInput {
        private String title;
        private String message;
        private String academicYear;
        private String level;
        private String target;   // ALL | UNPAID
        private boolean email = true;
    }

    @PostMapping("/reminders")
    public Map<String, Object> reminders(@RequestBody(required = false) BroadcastInput in) {
        return Map.of("sent", schoolService.sendPaymentReminders(in == null ? null : in.getAcademicYear()));
    }

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody BroadcastInput in) {
        return Map.of("sent", schoolService.broadcast(in.getTitle(), in.getMessage(), in.getAcademicYear(), in.getLevel(),
            in.getTarget(), in.isEmail()));
    }

    static ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.inline()
                .filename(name + ".pdf", java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }
}
