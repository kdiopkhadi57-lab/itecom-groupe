package com.elearning.controller;

import com.elearning.entity.SchoolCertificate;
import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.SchoolPayment;
import com.elearning.entity.User;
import com.elearning.repository.SchoolEnrollmentRepository;
import com.elearning.repository.UserRepository;
import com.elearning.service.SchoolPdfService;
import com.elearning.service.SchoolService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/** « Ma scolarité » : inscriptions, échéancier, paiement mobile, notes publiées et attestations de l'étudiant. */
@RestController
@RequestMapping("/api/scolarite")
@RequiredArgsConstructor
public class StudentSchoolController {

    private final SchoolService schoolService;
    private final SchoolPdfService pdfService;
    private final SchoolEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    @Value("${app.school.payment.wave-number}") private String waveNumber;
    @Value("${app.school.payment.orange-money-number}") private String orangeMoneyNumber;
    @Value("${app.school.payment.free-money-number}") private String freeMoneyNumber;

    private User me(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));
    }

    private SchoolEnrollment own(User me, Long enrollmentId) {
        SchoolEnrollment e = schoolService.getEnrollment(enrollmentId);
        if (!e.getStudent().getId().equals(me.getId())) throw new IllegalArgumentException("Inscription introuvable.");
        return e;
    }

    @GetMapping("/me")
    public Map<String, Object> mySchooling(@AuthenticationPrincipal UserDetails principal) {
        User me = me(principal);
        List<Map<String, Object>> enrollments = new ArrayList<>();
        for (SchoolEnrollment e : enrollmentRepository.findByStudentOrderByAcademicYearDesc(me)) {
            SchoolService.EnrollmentView v = schoolService.view(e);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollment", v);
            m.put("schedule", SchoolService.schedule(e, v.paid(), LocalDate.now()));
            m.put("payments", schoolService.paymentsOf(e));
            m.put("transcript", schoolService.transcript(e, true));
            m.put("certificates", schoolService.certificatesOf(e).stream().filter(c -> !c.revoked()).toList());
            enrollments.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enrollments", enrollments);
        r.put("paymentNumbers", Map.of("WAVE", waveNumber, "ORANGE_MONEY", orangeMoneyNumber, "FREE_MONEY", freeMoneyNumber));
        return r;
    }

    @Data
    public static class PayInput {
        private Long enrollmentId;
        private Long amount;
        private String method;
        private String phone;
        private String transactionRef;
    }

    @PostMapping("/payments")
    public SchoolService.PaymentView pay(@RequestBody PayInput in, @AuthenticationPrincipal UserDetails principal) {
        if (in.getEnrollmentId() == null || in.getAmount() == null) throw new IllegalArgumentException("Montant requis.");
        return SchoolService.paymentView(schoolService.submitMobilePayment(me(principal), in.getEnrollmentId(), in.getAmount(),
            in.getMethod(), in.getPhone(), in.getTransactionRef()));
    }

    @GetMapping("/payments/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) throws IOException {
        SchoolPayment p = schoolService.getPayment(id);
        own(me(principal), p.getEnrollment().getId());
        if (!"VALIDATED".equals(p.getStatus())) throw new IllegalArgumentException("Le reçu sera disponible après validation du paiement.");
        return AdminSchoolController.pdf(pdfService.receiptPdf(p), p.getReceiptNumber());
    }

    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificate(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) throws IOException {
        SchoolCertificate c = schoolService.getCertificate(id);
        own(me(principal), c.getEnrollment().getId());
        if (c.isRevoked()) throw new IllegalArgumentException("Cette attestation a été annulée.");
        return AdminSchoolController.pdf(pdfService.certificatePdf(c), c.getReference());
    }
}
