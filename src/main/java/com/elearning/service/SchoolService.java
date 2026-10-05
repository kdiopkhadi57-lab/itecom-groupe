package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scolarité : barème des frais, inscriptions administratives, paiements mobiles validés par
 * l'administration, notes et bulletins, attestations vérifiables par QR code, relances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchoolService {

    public static final List<String> LEVELS = List.of("L1", "L2", "L3", "M1", "M2");
    public static final List<String> MOBILE_METHODS = List.of("WAVE", "ORANGE_MONEY", "FREE_MONEY");
    public static final List<String> ALL_METHODS = List.of("WAVE", "ORANGE_MONEY", "FREE_MONEY", "ESPECES", "VIREMENT", "CHEQUE");
    public static final List<String> ENROLLMENT_STATUSES = List.of("PENDING", "ACTIVE", "SUSPENDED", "CANCELLED");
    public static final List<String> CERTIFICATE_TYPES = List.of("INSCRIPTION", "SCOLARITE", "REUSSITE", "RELEVE_NOTES");
    public static final List<String> SEMESTERS = List.of("S1", "S2");
    public static final List<String> SESSIONS = List.of("NORMALE", "RATTRAPAGE");

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SchoolFeeRepository feeRepository;
    private final SchoolEnrollmentRepository enrollmentRepository;
    private final SchoolPaymentRepository paymentRepository;
    private final SchoolGradeRepository gradeRepository;
    private final SchoolCertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ── Vues renvoyées au frontend ─────────────────────────────────────────────

    public record EnrollmentView(Long id, Long studentId, String studentName, String email, String phone,
                                 String matricule, String academicYear, String level, String specialization,
                                 String status, long registrationFee, long tuitionFee, long discount, int installments,
                                 long totalDue, long paid, long balance, long pendingAmount, long overdue,
                                 LocalDateTime createdAt) {}

    public record ScheduleItem(String label, LocalDate dueDate, long amount, long paid, String status) {}

    public record PaymentView(Long id, Long enrollmentId, String studentName, String matricule, String level,
                              String academicYear, long amount, String purpose, String method, String phone,
                              String transactionRef, String status, String receiptNumber, String rejectionReason,
                              String processedBy, LocalDateTime submittedAt, LocalDateTime processedAt) {}

    public record GradeLine(String subject, double coefficient, Double normale, Double rattrapage, double effective,
                            boolean published, String comment) {}

    public record SemesterView(String semester, List<GradeLine> lines, Double average) {}

    public record Transcript(List<SemesterView> semesters, Double annualAverage, String decision, String mention) {}

    public record SheetRow(Long enrollmentId, String studentName, String matricule, Double grade, String comment,
                           boolean published) {}

    public record GradeEntry(Long enrollmentId, Double grade, String comment) {}

    public record CertificateView(Long id, Long enrollmentId, String studentName, String matricule, String level,
                                  String academicYear, String type, String typeLabel, String reference,
                                  String verificationCode, String verifyUrl, Double average, String mention,
                                  LocalDateTime issuedAt, String issuedBy, boolean revoked) {}

    // ── Années, niveaux, codes ─────────────────────────────────────────────────

    /** Année universitaire en cours : elle commence en septembre. */
    public static String currentAcademicYear(LocalDate today) {
        int start = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }

    public static String normalizeYear(String year) {
        String y = year == null ? "" : year.trim().replace('/', '-');
        if (!y.matches("\\d{4}-\\d{4}") || Integer.parseInt(y.substring(5)) != Integer.parseInt(y.substring(0, 4)) + 1) {
            throw new IllegalArgumentException("Année universitaire invalide (format attendu : 2026-2027).");
        }
        return y;
    }

    public static String normalizeLevel(String level) {
        String l = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(l)) throw new IllegalArgumentException("Niveau invalide : choisissez L1, L2, L3, M1 ou M2.");
        return l;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    private String uniqueVerificationCode() {
        for (int i = 0; i < 20; i++) {
            String code = randomCode();
            if (certificateRepository.findByVerificationCode(code).isEmpty()
                && paymentRepository.findByVerificationCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Impossible de générer un code de vérification unique.");
    }

    public String verifyUrl(String code) {
        return frontendUrl + "/verification/" + code;
    }

    // ── Barème des frais ───────────────────────────────────────────────────────

    public List<SchoolFee> listFees() {
        return feeRepository.findAllByOrderByAcademicYearDescLevelAsc();
    }

    @Transactional
    public SchoolFee saveFee(Long id, SchoolFee input) {
        SchoolFee fee = id == null ? new SchoolFee()
            : feeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Barème introuvable."));
        String year = normalizeYear(input.getAcademicYear());
        String level = normalizeLevel(input.getLevel());
        String spec = blankToNull(input.getSpecialization());
        if (input.getRegistrationFee() < 0 || input.getTuitionFee() < 0) {
            throw new IllegalArgumentException("Les montants ne peuvent pas être négatifs.");
        }
        if (input.getInstallments() < 1 || input.getInstallments() > 12) {
            throw new IllegalArgumentException("Le nombre de mensualités doit être compris entre 1 et 12.");
        }
        boolean duplicate = feeRepository.findByAcademicYearAndLevel(year, level).stream()
            .anyMatch(f -> !f.getId().equals(id) && Objects.equals(f.getSpecialization(), spec));
        if (duplicate) throw new IllegalArgumentException("Un barème existe déjà pour " + level + " " + year
            + (spec == null ? " (toutes filières)." : " (" + spec + ")."));
        fee.setAcademicYear(year);
        fee.setLevel(level);
        fee.setSpecialization(spec);
        fee.setRegistrationFee(input.getRegistrationFee());
        fee.setTuitionFee(input.getTuitionFee());
        fee.setInstallments(input.getInstallments());
        return feeRepository.save(fee);
    }

    @Transactional
    public void deleteFee(Long id) {
        feeRepository.deleteById(id);
    }

    /** Barème de la filière s'il existe, sinon celui commun à toutes les filières du niveau. */
    public Optional<SchoolFee> findFee(String year, String level, String specialization) {
        List<SchoolFee> fees = feeRepository.findByAcademicYearAndLevel(year, level);
        return fees.stream().filter(f -> specialization != null && specialization.equals(f.getSpecialization())).findFirst()
            .or(() -> fees.stream().filter(f -> f.getSpecialization() == null).findFirst());
    }

    // ── Inscriptions administratives ───────────────────────────────────────────

    public List<EnrollmentView> listEnrollments(String year) {
        return enrollmentRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(e -> year == null || year.isBlank() || year.equals(e.getAcademicYear()))
            .map(this::view).toList();
    }

    public SchoolEnrollment getEnrollment(Long id) {
        return enrollmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Inscription introuvable."));
    }

    @Transactional
    public SchoolEnrollment enroll(Long studentId, String yearInput, String levelInput, String specializationInput,
                                   Long discount, Long registrationFeeOverride, Long tuitionFeeOverride) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Étudiant introuvable."));
        if (student.getRole() != Role.ROLE_STUDENT) throw new IllegalArgumentException("Seul un étudiant peut être inscrit.");
        String year = normalizeYear(yearInput);
        String level = normalizeLevel(levelInput);
        String spec = blankToNull(specializationInput) != null ? blankToNull(specializationInput) : student.getSpecialization();
        if (enrollmentRepository.findByStudentAndAcademicYear(student, year).isPresent()) {
            throw new IllegalArgumentException(student.getFirstName() + " " + student.getLastName()
                + " est déjà inscrit(e) pour " + year + ".");
        }
        Optional<SchoolFee> fee = findFee(year, level, spec);
        long registrationFee = registrationFeeOverride != null ? registrationFeeOverride
            : fee.map(SchoolFee::getRegistrationFee).orElse(-1L);
        long tuitionFee = tuitionFeeOverride != null ? tuitionFeeOverride : fee.map(SchoolFee::getTuitionFee).orElse(-1L);
        if (registrationFee < 0 || tuitionFee < 0) {
            throw new IllegalArgumentException("Aucun barème de frais pour " + level + " " + year
                + " : créez-le dans l'onglet « Frais » ou saisissez les montants.");
        }
        long reduction = discount == null ? 0 : discount;
        if (reduction < 0 || reduction > tuitionFee) {
            throw new IllegalArgumentException("La réduction doit être comprise entre 0 et les frais de scolarité.");
        }
        SchoolEnrollment e = enrollmentRepository.save(SchoolEnrollment.builder()
            .student(student).academicYear(year).level(level).specialization(spec)
            .matricule(nextMatricule(year, level))
            .registrationFee(registrationFee).tuitionFee(tuitionFee).discount(reduction)
            .installments(fee.map(SchoolFee::getInstallments).orElse(1))
            .status(registrationFee == 0 ? "ACTIVE" : "PENDING")
            .build());
        // Le niveau du compte suit l'inscription : examens, devoirs et classes virtuelles ciblent ce niveau
        if (year.equals(currentAcademicYear(LocalDate.now()))) {
            student.setLevel(level);
            userRepository.save(student);
        }
        notificationService.notify(student, "INSCRIPTION", "Inscription " + year + " enregistrée",
            "Vous êtes inscrit(e) en " + level + " pour l'année " + year + " (matricule " + e.getMatricule() + "). "
                + "Montant total : " + formatAmount(e.totalDue()) + ". Réglez vos frais depuis « Ma scolarité ».",
            "/scolarite", true);
        return e;
    }

    /** Inscrit d'un coup tous les étudiants validés d'un niveau qui ne le sont pas encore pour l'année. */
    @Transactional
    public Map<String, Object> enrollLevel(String yearInput, String levelInput) {
        String year = normalizeYear(yearInput);
        String level = normalizeLevel(levelInput);
        List<User> students = userRepository.findByRoleAndRegistrationStatusAndLevelIn(Role.ROLE_STUDENT, "APPROVED", List.of(level));
        int created = 0;
        List<String> errors = new ArrayList<>();
        for (User s : students) {
            if (enrollmentRepository.findByStudentAndAcademicYear(s, year).isPresent()) continue;
            try {
                enroll(s.getId(), year, level, null, null, null, null);
                created++;
            } catch (IllegalArgumentException ex) {
                errors.add(s.getFirstName() + " " + s.getLastName() + " : " + ex.getMessage());
            }
        }
        return Map.of("created", created, "errors", errors);
    }

    @Transactional
    public SchoolEnrollment updateEnrollment(Long id, String status, Long discount, Integer installments, String level) {
        SchoolEnrollment e = getEnrollment(id);
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            if (!ENROLLMENT_STATUSES.contains(s)) throw new IllegalArgumentException("Statut invalide.");
            if (!s.equals(e.getStatus())) {
                e.setStatus(s);
                notificationService.notify(e.getStudent(), "INSCRIPTION", "Votre inscription " + e.getAcademicYear(),
                    "Le statut de votre inscription est maintenant : " + statusLabel(s) + ".", "/scolarite", true);
            }
        }
        if (discount != null) {
            if (discount < 0 || discount > e.getTuitionFee()) {
                throw new IllegalArgumentException("La réduction doit être comprise entre 0 et les frais de scolarité.");
            }
            e.setDiscount(discount);
        }
        if (installments != null) {
            if (installments < 1 || installments > 12) throw new IllegalArgumentException("Entre 1 et 12 mensualités.");
            e.setInstallments(installments);
        }
        if (level != null && !level.isBlank()) e.setLevel(normalizeLevel(level));
        refreshStatus(e);
        return enrollmentRepository.save(e);
    }

    @Transactional
    public void deleteEnrollment(Long id) {
        SchoolEnrollment e = getEnrollment(id);
        assertDeletable(e);
        gradeRepository.deleteByEnrollment(e);
        enrollmentRepository.delete(e);
    }

    /** Une inscription qui a des paiements ou des attestations se passe au statut « Annulée », sans être supprimée. */
    public void assertDeletable(SchoolEnrollment e) {
        if (paymentRepository.existsByEnrollment(e)) {
            throw new IllegalArgumentException("Cette inscription a des paiements : passez-la plutôt au statut « Annulée ».");
        }
        if (!certificateRepository.findByEnrollmentOrderByIssuedAtDesc(e).isEmpty()) {
            throw new IllegalArgumentException("Cette inscription a des attestations délivrées : passez-la plutôt au statut « Annulée ».");
        }
    }

    private String nextMatricule(String year, String level) {
        long seq = enrollmentRepository.countByAcademicYear(year) + 1;
        String prefix = "ITC" + year.substring(2, 4) + "-" + level + "-";
        String m;
        do { m = prefix + String.format("%04d", seq++); } while (enrollmentRepository.existsByMatricule(m));
        return m;
    }

    /** Frais d'inscription réglés : l'inscription en attente devient active. */
    private void refreshStatus(SchoolEnrollment e) {
        if ("PENDING".equals(e.getStatus()) && paymentRepository.sumValidated(e) >= e.getRegistrationFee()) {
            e.setStatus("ACTIVE");
        }
    }

    public EnrollmentView view(SchoolEnrollment e) {
        long paid = paymentRepository.sumValidated(e);
        long pending = paymentRepository.findByEnrollmentOrderBySubmittedAtDesc(e).stream()
            .filter(p -> "PENDING".equals(p.getStatus())).mapToLong(SchoolPayment::getAmount).sum();
        long overdue = schedule(e, paid, LocalDate.now()).stream()
            .filter(i -> "OVERDUE".equals(i.status())).mapToLong(i -> i.amount() - i.paid()).sum();
        User s = e.getStudent();
        return new EnrollmentView(e.getId(), s.getId(), s.getFirstName() + " " + s.getLastName(), s.getEmail(), s.getPhone(),
            e.getMatricule(), e.getAcademicYear(), e.getLevel(), e.getSpecialization(), e.getStatus(),
            e.getRegistrationFee(), e.getTuitionFee(), e.getDiscount(), e.getInstallments(),
            e.totalDue(), paid, Math.max(0, e.totalDue() - paid), pending, overdue, e.getCreatedAt());
    }

    /**
     * Échéancier : frais d'inscription à l'inscription, puis la scolarité (réduction déduite) en mensualités
     * le 5 de chaque mois à partir d'octobre. Les paiements validés couvrent les échéances dans l'ordre.
     */
    public static List<ScheduleItem> schedule(SchoolEnrollment e, long paid, LocalDate today) {
        LocalDate enrolledOn = e.getCreatedAt() != null ? e.getCreatedAt().toLocalDate() : today;
        int startYear = Integer.parseInt(e.getAcademicYear().substring(0, 4));
        List<Object[]> items = new ArrayList<>();
        if (e.getRegistrationFee() > 0) items.add(new Object[]{"Frais d'inscription", enrolledOn, e.getRegistrationFee()});
        long tuition = Math.max(0, e.getTuitionFee() - e.getDiscount());
        int n = Math.max(1, e.getInstallments());
        if (tuition > 0) {
            long base = tuition / n;
            for (int k = 0; k < n; k++) {
                long amount = k == n - 1 ? tuition - base * (n - 1) : base;
                LocalDate due = LocalDate.of(startYear, 10, 5).plusMonths(k);
                if (due.isBefore(enrolledOn)) due = enrolledOn;
                items.add(new Object[]{n == 1 ? "Frais de scolarité" : "Mensualité " + (k + 1) + "/" + n, due, amount});
            }
        }
        List<ScheduleItem> result = new ArrayList<>();
        long remaining = paid;
        for (Object[] it : items) {
            long amount = (long) it[2];
            long covered = Math.min(amount, Math.max(0, remaining));
            remaining -= covered;
            LocalDate due = (LocalDate) it[1];
            String status = covered >= amount ? "PAID" : due.isBefore(today) ? "OVERDUE" : covered > 0 ? "PARTIAL" : "DUE";
            result.add(new ScheduleItem((String) it[0], due, amount, covered, status));
        }
        return result;
    }

    // ── Paiements ──────────────────────────────────────────────────────────────

    public List<PaymentView> listPayments(String status) {
        List<SchoolPayment> list = status == null || status.isBlank()
            ? paymentRepository.findAllByOrderBySubmittedAtDesc()
            : paymentRepository.findByStatusOrderBySubmittedAtDesc(status.toUpperCase(Locale.ROOT));
        return list.stream().map(SchoolService::paymentView).toList();
    }

    public List<PaymentView> paymentsOf(SchoolEnrollment e) {
        return paymentRepository.findByEnrollmentOrderBySubmittedAtDesc(e).stream().map(SchoolService::paymentView).toList();
    }

    public SchoolPayment getPayment(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Paiement introuvable."));
    }

    /** Paiement mobile déclaré par l'étudiant : à vérifier par l'administration. */
    @Transactional
    public SchoolPayment submitMobilePayment(User student, Long enrollmentId, long amount, String methodInput,
                                             String phoneInput, String refInput) {
        SchoolEnrollment e = getEnrollment(enrollmentId);
        if (!e.getStudent().getId().equals(student.getId())) throw new IllegalArgumentException("Inscription introuvable.");
        if ("CANCELLED".equals(e.getStatus())) throw new IllegalArgumentException("Cette inscription est annulée.");
        String method = methodInput == null ? "" : methodInput.trim().toUpperCase(Locale.ROOT);
        if (!MOBILE_METHODS.contains(method)) throw new IllegalArgumentException("Choisissez Wave, Orange Money ou Free Money.");
        String phone = ContactValidator.phone(phoneInput, ContactValidator.usageFor(method));
        String ref = refInput == null ? "" : refInput.trim();
        if (ref.length() < 4) throw new IllegalArgumentException("Saisissez la référence de transaction reçue par SMS.");
        if (paymentRepository.existsByTransactionRefIgnoreCaseAndStatusNot(ref, "REJECTED")) {
            throw new IllegalArgumentException("Cette référence de transaction a déjà été déclarée.");
        }
        long pending = paymentRepository.findByEnrollmentOrderBySubmittedAtDesc(e).stream()
            .filter(p -> "PENDING".equals(p.getStatus())).mapToLong(SchoolPayment::getAmount).sum();
        long balance = e.totalDue() - paymentRepository.sumValidated(e) - pending;
        if (amount <= 0) throw new IllegalArgumentException("Le montant doit être positif.");
        if (amount > balance) {
            throw new IllegalArgumentException(balance <= 0 ? "Aucun montant restant à payer (paiements en cours de vérification compris)."
                : "Le montant dépasse le reste à payer (" + formatAmount(balance) + ").");
        }
        SchoolPayment p = paymentRepository.save(SchoolPayment.builder()
            .enrollment(e).amount(amount).purpose(purposeFor(e)).method(method).phone(phone).transactionRef(ref)
            .status("PENDING").submittedAt(LocalDateTime.now()).build());
        notificationService.notifyAdmins("PAIEMENT", "Paiement à vérifier",
            student.getFirstName() + " " + student.getLastName() + " (" + e.getMatricule() + ") a déclaré "
                + formatAmount(amount) + " par " + methodLabel(method) + ", réf. " + ref + ".",
            "/admin/scolarite?tab=payments");
        return p;
    }

    /** Paiement encaissé directement par l'administration (espèces, virement, chèque ou mobile vérifié). */
    @Transactional
    public SchoolPayment recordPayment(String adminName, Long enrollmentId, long amount, String methodInput, String ref) {
        SchoolEnrollment e = getEnrollment(enrollmentId);
        String method = methodInput == null ? "" : methodInput.trim().toUpperCase(Locale.ROOT);
        if (!ALL_METHODS.contains(method)) throw new IllegalArgumentException("Mode de paiement invalide.");
        if (amount <= 0) throw new IllegalArgumentException("Le montant doit être positif.");
        long balance = e.totalDue() - paymentRepository.sumValidated(e);
        if (amount > balance) throw new IllegalArgumentException("Le montant dépasse le reste à payer (" + formatAmount(balance) + ").");
        String reference = blankToNull(ref);
        if (reference != null && paymentRepository.existsByTransactionRefIgnoreCaseAndStatusNot(reference, "REJECTED")) {
            throw new IllegalArgumentException("Cette référence de transaction a déjà été enregistrée.");
        }
        SchoolPayment p = paymentRepository.save(SchoolPayment.builder()
            .enrollment(e).amount(amount).purpose(purposeFor(e)).method(method).transactionRef(reference)
            .status("PENDING").submittedAt(LocalDateTime.now()).build());
        return confirm(p, adminName);
    }

    @Transactional
    public SchoolPayment validatePayment(Long id, String adminName) {
        SchoolPayment p = getPayment(id);
        if (!"PENDING".equals(p.getStatus())) throw new IllegalArgumentException("Ce paiement a déjà été traité.");
        long balance = p.getEnrollment().totalDue() - paymentRepository.sumValidated(p.getEnrollment());
        if (p.getAmount() > balance) {
            throw new IllegalArgumentException("Ce paiement dépasse le reste à payer (" + formatAmount(balance) + ") : rejetez-le.");
        }
        return confirm(p, adminName);
    }

    private SchoolPayment confirm(SchoolPayment p, String adminName) {
        p.setStatus("VALIDATED");
        p.setProcessedBy(adminName);
        p.setProcessedAt(LocalDateTime.now());
        p.setVerificationCode(uniqueVerificationCode());
        p = paymentRepository.saveAndFlush(p);
        p.setReceiptNumber("REC-" + p.getEnrollment().getAcademicYear().substring(0, 4) + "-" + String.format("%06d", p.getId()));
        p = paymentRepository.save(p);
        SchoolEnrollment e = p.getEnrollment();
        refreshStatus(e);
        enrollmentRepository.save(e);
        long balance = Math.max(0, e.totalDue() - paymentRepository.sumValidated(e));
        notificationService.notify(e.getStudent(), "PAIEMENT", "Paiement validé",
            "Votre paiement de " + formatAmount(p.getAmount()) + " (" + methodLabel(p.getMethod()) + ") est validé. "
                + "Reçu n° " + p.getReceiptNumber() + ". Reste à payer : " + formatAmount(balance) + ".",
            "/scolarite", true);
        return p;
    }

    @Transactional
    public SchoolPayment rejectPayment(Long id, String reason, String adminName) {
        SchoolPayment p = getPayment(id);
        if (!"PENDING".equals(p.getStatus())) throw new IllegalArgumentException("Ce paiement a déjà été traité.");
        p.setStatus("REJECTED");
        p.setRejectionReason(blankToNull(reason));
        p.setProcessedBy(adminName);
        p.setProcessedAt(LocalDateTime.now());
        paymentRepository.save(p);
        notificationService.notify(p.getEnrollment().getStudent(), "PAIEMENT", "Paiement refusé",
            "Votre paiement de " + formatAmount(p.getAmount()) + " (réf. " + p.getTransactionRef() + ") n'a pas pu être vérifié."
                + (p.getRejectionReason() != null ? " Motif : " + p.getRejectionReason() : "")
                + " Contactez la scolarité si vous avez bien effectué le transfert.",
            "/scolarite", true);
        return p;
    }

    private String purposeFor(SchoolEnrollment e) {
        return paymentRepository.sumValidated(e) < e.getRegistrationFee() ? "INSCRIPTION" : "SCOLARITE";
    }

    public static PaymentView paymentView(SchoolPayment p) {
        SchoolEnrollment e = p.getEnrollment();
        return new PaymentView(p.getId(), e.getId(), e.getStudent().getFirstName() + " " + e.getStudent().getLastName(),
            e.getMatricule(), e.getLevel(), e.getAcademicYear(), p.getAmount(), p.getPurpose(), p.getMethod(), p.getPhone(),
            p.getTransactionRef(), p.getStatus(), p.getReceiptNumber(), p.getRejectionReason(), p.getProcessedBy(),
            p.getSubmittedAt(), p.getProcessedAt());
    }

    // ── Notes ──────────────────────────────────────────────────────────────────

    public List<String> subjects(String year, String level) {
        return gradeRepository.findSubjects(normalizeYear(year), normalizeLevel(level));
    }

    /** Feuille de notes d'une matière pour toute une promotion. */
    public Map<String, Object> gradeSheet(String yearInput, String levelInput, String semesterInput, String subjectInput,
                                          String sessionInput) {
        String year = normalizeYear(yearInput), level = normalizeLevel(levelInput);
        String semester = normalizeSemester(semesterInput), session = normalizeSession(sessionInput);
        String subject = normalizeSubject(subjectInput);
        List<SchoolEnrollment> promo = promotion(year, level);
        Map<Long, SchoolGrade> byEnrollment = gradeRepository
            .findByEnrollmentInAndSemesterAndSubjectAndSession(promo, semester, subject, session).stream()
            .collect(Collectors.toMap(g -> g.getEnrollment().getId(), g -> g));
        Double coefficient = byEnrollment.values().stream().map(SchoolGrade::getCoefficient).findFirst().orElse(null);
        List<SheetRow> rows = promo.stream().map(e -> {
            SchoolGrade g = byEnrollment.get(e.getId());
            return new SheetRow(e.getId(), e.getStudent().getLastName() + " " + e.getStudent().getFirstName(), e.getMatricule(),
                g == null ? null : g.getGrade(), g == null ? null : g.getComment(), g != null && g.isPublished());
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coefficient", coefficient);
        result.put("rows", rows);
        return result;
    }

    @Transactional
    public int saveGradeSheet(String yearInput, String levelInput, String semesterInput, String subjectInput,
                              String sessionInput, double coefficient, List<GradeEntry> entries) {
        String year = normalizeYear(yearInput), level = normalizeLevel(levelInput);
        String semester = normalizeSemester(semesterInput), session = normalizeSession(sessionInput);
        String subject = normalizeSubject(subjectInput);
        if (coefficient <= 0 || coefficient > 20) throw new IllegalArgumentException("Le coefficient doit être compris entre 0 et 20.");
        Map<Long, SchoolEnrollment> promo = promotion(year, level).stream()
            .collect(Collectors.toMap(SchoolEnrollment::getId, e -> e));
        int saved = 0;
        for (GradeEntry entry : entries == null ? List.<GradeEntry>of() : entries) {
            SchoolEnrollment e = promo.get(entry.enrollmentId());
            if (e == null) continue;
            Optional<SchoolGrade> existing = gradeRepository.findByEnrollmentAndSemesterAndSubjectAndSession(e, semester, subject, session);
            if (entry.grade() == null) {
                existing.ifPresent(gradeRepository::delete);
                continue;
            }
            if (entry.grade() < 0 || entry.grade() > 20) {
                throw new IllegalArgumentException("Note invalide pour " + e.getStudent().getFirstName() + " "
                    + e.getStudent().getLastName() + " : elle doit être comprise entre 0 et 20.");
            }
            SchoolGrade g = existing.orElseGet(() -> SchoolGrade.builder().enrollment(e).semester(semester)
                .subject(subject).session(session).build());
            g.setGrade(Math.round(entry.grade() * 100) / 100.0);
            g.setCoefficient(coefficient);
            g.setComment(blankToNull(entry.comment()));
            gradeRepository.save(g);
            saved++;
        }
        return saved;
    }

    /** Publie les notes non publiées d'un semestre et prévient chaque étudiant concerné. */
    @Transactional
    public int publishGrades(String yearInput, String levelInput, String semesterInput) {
        String year = normalizeYear(yearInput), level = normalizeLevel(levelInput);
        String semester = normalizeSemester(semesterInput);
        List<SchoolGrade> grades = gradeRepository.findByEnrollmentInAndSemesterAndPublishedFalse(promotion(year, level), semester);
        Set<SchoolEnrollment> students = new LinkedHashSet<>();
        for (SchoolGrade g : grades) {
            g.setPublished(true);
            students.add(g.getEnrollment());
        }
        gradeRepository.saveAll(grades);
        for (SchoolEnrollment e : students) {
            notificationService.notify(e.getStudent(), "NOTES", "Notes du semestre " + semester + " publiées",
                "Vos notes du semestre " + semester + " (" + level + ", " + year + ") sont disponibles dans « Ma scolarité ».",
                "/scolarite?tab=notes", true);
        }
        return grades.size();
    }

    public Transcript transcript(SchoolEnrollment e, boolean publishedOnly) {
        List<SchoolGrade> grades = publishedOnly
            ? gradeRepository.findByEnrollmentAndPublishedTrueOrderBySemesterAscSubjectAsc(e)
            : gradeRepository.findByEnrollmentOrderBySemesterAscSubjectAsc(e);
        return buildTranscript(grades);
    }

    /** Pour chaque matière, la note retenue est la meilleure entre la session normale et le rattrapage. */
    public static Transcript buildTranscript(List<SchoolGrade> grades) {
        Map<String, Map<String, List<SchoolGrade>>> bySemester = new TreeMap<>();
        for (SchoolGrade g : grades) {
            bySemester.computeIfAbsent(g.getSemester(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                .computeIfAbsent(g.getSubject(), k -> new ArrayList<>()).add(g);
        }
        List<SemesterView> semesters = new ArrayList<>();
        double totalWeighted = 0, totalCoef = 0;
        for (var sem : bySemester.entrySet()) {
            List<GradeLine> lines = new ArrayList<>();
            double weighted = 0, coef = 0;
            for (var subj : sem.getValue().entrySet()) {
                Double normale = null, rattrapage = null;
                double c = 1;
                boolean published = true;
                String comment = null;
                for (SchoolGrade g : subj.getValue()) {
                    if ("RATTRAPAGE".equals(g.getSession())) rattrapage = g.getGrade(); else normale = g.getGrade();
                    c = g.getCoefficient();
                    published &= g.isPublished();
                    if (g.getComment() != null) comment = g.getComment();
                }
                double effective = Math.max(normale == null ? 0 : normale, rattrapage == null ? 0 : rattrapage);
                lines.add(new GradeLine(subj.getKey(), c, normale, rattrapage, effective, published, comment));
                weighted += effective * c;
                coef += c;
            }
            semesters.add(new SemesterView(sem.getKey(), lines, coef > 0 ? round2(weighted / coef) : null));
            totalWeighted += weighted;
            totalCoef += coef;
        }
        Double annual = totalCoef > 0 ? round2(totalWeighted / totalCoef) : null;
        return new Transcript(semesters, annual, annual == null ? null : annual >= 10 ? "Admis(e)" : "Ajourné(e)", mention(annual));
    }

    public static String mention(Double average) {
        if (average == null || average < 10) return null;
        if (average >= 16) return "Très bien";
        if (average >= 14) return "Bien";
        if (average >= 12) return "Assez bien";
        return "Passable";
    }

    private List<SchoolEnrollment> promotion(String year, String level) {
        return enrollmentRepository.findByAcademicYearAndLevel(year, level).stream()
            .filter(e -> !"CANCELLED".equals(e.getStatus()))
            .sorted(Comparator.comparing((SchoolEnrollment e) -> e.getStudent().getLastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.getStudent().getFirstName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static String normalizeSemester(String s) {
        String v = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        if (!SEMESTERS.contains(v)) throw new IllegalArgumentException("Semestre invalide (S1 ou S2).");
        return v;
    }

    private static String normalizeSession(String s) {
        String v = s == null || s.isBlank() ? "NORMALE" : s.trim().toUpperCase(Locale.ROOT);
        if (!SESSIONS.contains(v)) throw new IllegalArgumentException("Session invalide (normale ou rattrapage).");
        return v;
    }

    private static String normalizeSubject(String s) {
        String v = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (v.isEmpty()) throw new IllegalArgumentException("Indiquez la matière.");
        if (v.length() > 255) throw new IllegalArgumentException("Nom de matière trop long.");
        return v;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ── Attestations ───────────────────────────────────────────────────────────

    public List<CertificateView> listCertificates() {
        return certificateRepository.findAllByOrderByIssuedAtDesc().stream().map(this::view).toList();
    }

    public List<CertificateView> certificatesOf(SchoolEnrollment e) {
        return certificateRepository.findByEnrollmentOrderByIssuedAtDesc(e).stream().map(this::view).toList();
    }

    public SchoolCertificate getCertificate(Long id) {
        return certificateRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Attestation introuvable."));
    }

    @Transactional
    public SchoolCertificate issueCertificate(Long enrollmentId, String typeInput, String adminName) {
        SchoolEnrollment e = getEnrollment(enrollmentId);
        String type = typeInput == null ? "" : typeInput.trim().toUpperCase(Locale.ROOT);
        if (!CERTIFICATE_TYPES.contains(type)) throw new IllegalArgumentException("Type d'attestation invalide.");
        Double average = null;
        String mention = null;
        switch (type) {
            case "INSCRIPTION", "SCOLARITE" -> {
                if (!"ACTIVE".equals(e.getStatus())) {
                    throw new IllegalArgumentException("L'inscription doit être active (frais d'inscription réglés) pour délivrer cette attestation.");
                }
            }
            default -> {
                Transcript t = transcript(e, true);
                if (t.annualAverage() == null) throw new IllegalArgumentException("Aucune note publiée pour cet étudiant.");
                if ("REUSSITE".equals(type) && t.annualAverage() < 10) {
                    throw new IllegalArgumentException("Moyenne annuelle inférieure à 10/20 : attestation de réussite impossible.");
                }
                average = t.annualAverage();
                mention = t.mention();
            }
        }
        SchoolCertificate c = certificateRepository.saveAndFlush(SchoolCertificate.builder()
            .enrollment(e).type(type).verificationCode(uniqueVerificationCode())
            .reference("TMP-" + randomCode()).average(average).mention(mention)
            .issuedAt(LocalDateTime.now()).issuedBy(adminName).build());
        c.setReference(certificatePrefix(type) + "-" + e.getAcademicYear().substring(0, 4) + "-" + String.format("%06d", c.getId()));
        c = certificateRepository.save(c);
        notificationService.notify(e.getStudent(), "ATTESTATION", certificateLabel(type) + " disponible",
            "Votre " + certificateLabel(type).toLowerCase(Locale.ROOT) + " (" + e.getAcademicYear() + ") est disponible. "
                + "Téléchargez-la depuis « Ma scolarité ». Elle porte un QR code vérifiable en ligne.",
            "/scolarite?tab=attestations", true);
        return c;
    }

    @Transactional
    public SchoolCertificate revokeCertificate(Long id) {
        SchoolCertificate c = getCertificate(id);
        c.setRevoked(true);
        c.setRevokedAt(LocalDateTime.now());
        return certificateRepository.save(c);
    }

    public CertificateView view(SchoolCertificate c) {
        SchoolEnrollment e = c.getEnrollment();
        return new CertificateView(c.getId(), e.getId(), e.getStudent().getFirstName() + " " + e.getStudent().getLastName(),
            e.getMatricule(), e.getLevel(), e.getAcademicYear(), c.getType(), certificateLabel(c.getType()), c.getReference(),
            c.getVerificationCode(), verifyUrl(c.getVerificationCode()), c.getAverage(), c.getMention(),
            c.getIssuedAt(), c.getIssuedBy(), c.isRevoked());
    }

    public static String certificateLabel(String type) {
        return switch (type) {
            case "INSCRIPTION" -> "Attestation d'inscription";
            case "SCOLARITE" -> "Certificat de scolarité";
            case "REUSSITE" -> "Attestation de réussite";
            case "RELEVE_NOTES" -> "Relevé de notes";
            default -> type;
        };
    }

    private static String certificatePrefix(String type) {
        return switch (type) {
            case "INSCRIPTION" -> "ATI";
            case "SCOLARITE" -> "CSC";
            case "REUSSITE" -> "ATR";
            default -> "RLN";
        };
    }

    // ── Vérification publique (QR code) ────────────────────────────────────────

    public Map<String, Object> verify(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> r = new LinkedHashMap<>();
        Optional<SchoolCertificate> cert = certificateRepository.findByVerificationCode(code);
        if (cert.isPresent()) {
            SchoolCertificate c = cert.get();
            SchoolEnrollment e = c.getEnrollment();
            r.put("found", true);
            r.put("valid", !c.isRevoked());
            r.put("kind", "CERTIFICATE");
            r.put("documentLabel", certificateLabel(c.getType()));
            r.put("reference", c.getReference());
            r.put("issuedAt", c.getIssuedAt());
            r.put("revokedAt", c.getRevokedAt());
            putStudent(r, e);
            if (c.getAverage() != null) r.put("average", c.getAverage());
            if (c.getMention() != null) r.put("mention", c.getMention());
            return r;
        }
        Optional<SchoolPayment> pay = paymentRepository.findByVerificationCode(code);
        if (pay.isPresent()) {
            SchoolPayment p = pay.get();
            r.put("found", true);
            r.put("valid", "VALIDATED".equals(p.getStatus()));
            r.put("kind", "RECEIPT");
            r.put("documentLabel", "Reçu de paiement");
            r.put("reference", p.getReceiptNumber());
            r.put("issuedAt", p.getProcessedAt());
            r.put("amount", p.getAmount());
            r.put("method", methodLabel(p.getMethod()));
            putStudent(r, p.getEnrollment());
            return r;
        }
        r.put("found", false);
        r.put("valid", false);
        return r;
    }

    private static void putStudent(Map<String, Object> r, SchoolEnrollment e) {
        r.put("studentName", e.getStudent().getFirstName() + " " + e.getStudent().getLastName());
        r.put("birthDate", e.getStudent().getBirthDate());
        r.put("matricule", e.getMatricule());
        r.put("level", e.getLevel());
        r.put("specialization", e.getSpecialization());
        r.put("academicYear", e.getAcademicYear());
    }

    // ── Statistiques ───────────────────────────────────────────────────────────

    public Map<String, Object> stats(String yearInput) {
        String year = yearInput == null || yearInput.isBlank() ? currentAcademicYear(LocalDate.now()) : normalizeYear(yearInput);
        List<EnrollmentView> enrollments = listEnrollments(year).stream()
            .filter(e -> !"CANCELLED".equals(e.status())).toList();
        long expected = enrollments.stream().mapToLong(EnrollmentView::totalDue).sum();
        long collected = enrollments.stream().mapToLong(EnrollmentView::paid).sum();
        long overdue = enrollments.stream().mapToLong(EnrollmentView::overdue).sum();
        List<PaymentView> pending = listPayments("PENDING").stream().filter(p -> year.equals(p.academicYear())).toList();

        List<Map<String, Object>> byLevel = new ArrayList<>();
        for (String level : LEVELS) {
            List<EnrollmentView> l = enrollments.stream().filter(e -> level.equals(e.level())).toList();
            if (l.isEmpty()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", level);
            row.put("students", l.size());
            row.put("expected", l.stream().mapToLong(EnrollmentView::totalDue).sum());
            row.put("collected", l.stream().mapToLong(EnrollmentView::paid).sum());
            row.put("unpaidStudents", l.stream().filter(e -> e.balance() > 0).count());
            byLevel.add(row);
        }
        Map<String, Long> byMethod = new TreeMap<>();
        listPayments("VALIDATED").stream().filter(p -> year.equals(p.academicYear()))
            .forEach(p -> byMethod.merge(p.method(), p.amount(), Long::sum));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("academicYear", year);
        r.put("students", enrollments.size());
        r.put("active", enrollments.stream().filter(e -> "ACTIVE".equals(e.status())).count());
        r.put("pendingEnrollments", enrollments.stream().filter(e -> "PENDING".equals(e.status())).count());
        r.put("expected", expected);
        r.put("collected", collected);
        r.put("remaining", Math.max(0, expected - collected));
        r.put("overdue", overdue);
        r.put("overdueStudents", enrollments.stream().filter(e -> e.overdue() > 0).count());
        r.put("collectionRate", expected > 0 ? Math.round(collected * 1000.0 / expected) / 10.0 : 0);
        r.put("pendingPayments", pending.size());
        r.put("pendingAmount", pending.stream().mapToLong(PaymentView::amount).sum());
        r.put("certificates", certificateRepository.countByRevokedFalse());
        r.put("byLevel", byLevel);
        r.put("byMethod", byMethod);
        return r;
    }

    // ── Relances et annonces ───────────────────────────────────────────────────

    /** Relance par notification et email chaque étudiant ayant une échéance dépassée non réglée. */
    @Transactional
    public int sendPaymentReminders(String yearInput) {
        String year = yearInput == null || yearInput.isBlank() ? currentAcademicYear(LocalDate.now()) : normalizeYear(yearInput);
        int sent = 0;
        for (SchoolEnrollment e : enrollmentRepository.findAllByOrderByCreatedAtDesc()) {
            if (!year.equals(e.getAcademicYear()) || "CANCELLED".equals(e.getStatus())) continue;
            EnrollmentView v = view(e);
            if (v.overdue() <= 0) continue;
            notificationService.notify(e.getStudent(), "PAIEMENT", "Rappel : échéance de scolarité dépassée",
                "Il reste " + formatAmount(v.overdue()) + " à régler sur vos échéances échues (reste total : "
                    + formatAmount(v.balance()) + "). Payez par Wave, Orange Money ou Free Money depuis « Ma scolarité ».",
                "/scolarite", true);
            sent++;
        }
        return sent;
    }

    /** Relance automatique le 6 de chaque mois, lendemain de l'échéance mensuelle. */
    @Scheduled(cron = "0 0 9 6 * *", zone = "Africa/Dakar")
    public void monthlyReminders() {
        try {
            int sent = sendPaymentReminders(null);
            log.info("Scolarité : {} relance(s) de paiement envoyée(s)", sent);
        } catch (Exception ex) {
            log.warn("Scolarité : relances automatiques impossibles ({})", ex.getMessage());
        }
    }

    /** Annonce de l'administration : tous les étudiants inscrits de l'année, un niveau, ou ceux en retard de paiement. */
    @Transactional
    public int broadcast(String title, String message, String yearInput, String level, String target, boolean email) {
        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Saisissez un titre et un message.");
        }
        String year = yearInput == null || yearInput.isBlank() ? currentAcademicYear(LocalDate.now()) : normalizeYear(yearInput);
        String lvl = level == null || level.isBlank() ? null : normalizeLevel(level);
        boolean unpaidOnly = "UNPAID".equalsIgnoreCase(target);
        List<User> recipients = enrollmentRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(e -> year.equals(e.getAcademicYear()) && !"CANCELLED".equals(e.getStatus()))
            .filter(e -> lvl == null || lvl.equals(e.getLevel()))
            .filter(e -> !unpaidOnly || view(e).balance() > 0)
            .map(SchoolEnrollment::getStudent).distinct().toList();
        return notificationService.notifyAll(recipients, "INFO", title.trim(), message.trim(), "/scolarite", email);
    }

    // ── Libellés ───────────────────────────────────────────────────────────────

    /** 150000 → « 150 000 FCFA » (espaces simples, compatibles avec les polices PDF standard). */
    public static String formatAmount(long amount) {
        String digits = Long.toString(Math.abs(amount));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return (amount < 0 ? "-" : "") + sb + " FCFA";
    }

    public static String methodLabel(String method) {
        return switch (method == null ? "" : method) {
            case "WAVE" -> "Wave";
            case "ORANGE_MONEY" -> "Orange Money";
            case "FREE_MONEY" -> "Free Money";
            case "ESPECES" -> "Espèces";
            case "VIREMENT" -> "Virement bancaire";
            case "CHEQUE" -> "Chèque";
            default -> method;
        };
    }

    public static String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "en attente du paiement des frais d'inscription";
            case "ACTIVE" -> "active";
            case "SUSPENDED" -> "suspendue";
            case "CANCELLED" -> "annulée";
            default -> status;
        };
    }
}
