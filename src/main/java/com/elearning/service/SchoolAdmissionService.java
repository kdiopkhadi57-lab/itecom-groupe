package com.elearning.service;

import com.elearning.entity.Role;
import com.elearning.entity.SchoolDocument;
import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.User;
import com.elearning.repository.SchoolDocumentRepository;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Inscription d'un nouvel étudiant par la scolarité : création du compte, inscription administrative
 * et pièces du dossier scannées en PDF (bac, et relevés de l'année passée pour un étudiant déjà inscrit ailleurs).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchoolAdmissionService {

    public static final String NEW_BACHELOR = "NEW_BACHELOR";
    public static final String ALREADY_STUDENT = "ALREADY_STUDENT";
    static final long MAX_PDF_SIZE = 10L * 1024 * 1024;

    private final SchoolService schoolService;
    private final UserRepository userRepository;
    private final SchoolDocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ExamService examService;

    @Value("${app.school.documents-dir:school-documents/}")
    private String documentsDir;

    public record NewStudent(String firstName, String lastName, String birthDate, String birthPlace, String email,
                             String phone, String academicYear, String level, String specialization, Long discount,
                             String profile) {}

    public record DocumentView(Long id, String type, String typeLabel, String originalName, long size, LocalDateTime uploadedAt) {}

    public record Admission(SchoolService.EnrollmentView enrollment, boolean emailSent, String message, String password) {}

    @Transactional
    public Admission admit(NewStudent in, MultipartFile bacAttestation, MultipartFile bacTranscript,
                           List<MultipartFile> previousTranscripts) throws IOException {
        String firstName = required(in.firstName(), "le prénom");
        String lastName = required(in.lastName(), "le nom");
        String birthPlace = required(in.birthPlace(), "le lieu de naissance");
        LocalDate birthDate = parseBirthDate(in.birthDate());
        String email = required(in.email(), "l'email").toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("Adresse email invalide.");
        String profile = ALREADY_STUDENT.equalsIgnoreCase(in.profile()) ? ALREADY_STUDENT : NEW_BACHELOR;

        // Un nouvel étudiant ne doit pas déjà exister sur la plateforme
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Un compte existe déjà avec l'email " + email + " : ce n'est pas un nouvel étudiant.");
        }
        List<User> homonyms = userRepository.findByBirthDateAndLastNameIgnoreCaseAndFirstNameIgnoreCase(birthDate, lastName, firstName);
        if (!homonyms.isEmpty()) {
            throw new IllegalArgumentException(firstName + " " + lastName + ", né(e) le " + birthDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ", existe déjà (compte " + homonyms.get(0).getEmail() + ") : utilisez l'inscription « Tout un niveau » pour les étudiants existants.");
        }

        List<MultipartFile> previous = previousTranscripts == null ? List.of()
            : previousTranscripts.stream().filter(f -> f != null && !f.isEmpty()).toList();
        checkPdf(bacAttestation, "l'attestation du bac");
        checkPdf(bacTranscript, "le relevé de notes du bac");
        if (ALREADY_STUDENT.equals(profile) && previous.isEmpty()) {
            throw new IllegalArgumentException("Pour un étudiant déjà inscrit dans le supérieur, joignez ses relevés de notes de l'année passée (PDF).");
        }
        for (MultipartFile f : previous) checkPdf(f, "le relevé « " + f.getOriginalFilename() + " »");

        String password = PasswordGenerator.generate();
        String level = SchoolService.normalizeLevel(in.level());
        String specialization = in.specialization() == null || in.specialization().isBlank() ? null : in.specialization().trim();
        User student = userRepository.save(User.builder()
            .firstName(firstName).lastName(lastName).email(email)
            .password(passwordEncoder.encode(password))
            .role(Role.ROLE_STUDENT)
            .birthDate(birthDate).birthPlace(birthPlace)
            .phone(in.phone() == null || in.phone().isBlank() ? null : in.phone().trim())
            .specialization(specialization).level(level)
            .enabled(true).registrationStatus("APPROVED")
            .build());

        SchoolEnrollment enrollment = schoolService.enroll(student.getId(), in.academicYear(), level, specialization,
            in.discount(), null, null);

        List<Path> written = new ArrayList<>();
        try {
            store(enrollment, "BAC_ATTESTATION", bacAttestation, written);
            store(enrollment, "BAC_TRANSCRIPT", bacTranscript, written);
            for (MultipartFile f : previous) store(enrollment, "PREVIOUS_TRANSCRIPT", f, written);
        } catch (IOException | RuntimeException ex) {
            // La base est annulée : on ne laisse pas de fichiers orphelins
            for (Path p : written) Files.deleteIfExists(p);
            throw ex;
        }

        examService.enrollNewStudent(student);
        String emailError = emailService.sendAccountCreated(email, firstName, "étudiant", password);
        boolean sent = emailError == null;
        return new Admission(schoolService.view(enrollment), sent,
            sent ? "Étudiant inscrit (matricule " + enrollment.getMatricule() + "). Ses identifiants ont été envoyés à " + email + "."
                 : "Étudiant inscrit (matricule " + enrollment.getMatricule() + "), mais l'email n'a pas pu être envoyé : " + emailError
                   + ". Communiquez-lui ses identifiants.",
            sent ? "" : password);
    }

    public List<DocumentView> documents(SchoolEnrollment e) {
        return documentRepository.findByEnrollmentOrderByTypeAscUploadedAtAsc(e).stream()
            .map(d -> new DocumentView(d.getId(), d.getType(), documentLabel(d.getType()), d.getOriginalName(), d.getSize(), d.getUploadedAt()))
            .toList();
    }

    public SchoolDocument getDocument(Long id) {
        return documentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Pièce introuvable."));
    }

    public byte[] read(SchoolDocument d) throws IOException {
        return Files.readAllBytes(resolve(d.getStoredName()));
    }

    /** Supprime l'inscription et les pièces de son dossier (refusé s'il y a des paiements ou des attestations). */
    @Transactional
    public void deleteEnrollment(Long id) throws IOException {
        SchoolEnrollment e = schoolService.getEnrollment(id);
        schoolService.assertDeletable(e);
        List<SchoolDocument> docs = documentRepository.findByEnrollmentOrderByTypeAscUploadedAtAsc(e);
        documentRepository.deleteAll(docs);
        schoolService.deleteEnrollment(id);
        for (SchoolDocument d : docs) Files.deleteIfExists(resolve(d.getStoredName()));
    }

    public static String documentLabel(String type) {
        return switch (type) {
            case "BAC_ATTESTATION" -> "Attestation du bac";
            case "BAC_TRANSCRIPT" -> "Relevé de notes du bac";
            case "PREVIOUS_TRANSCRIPT" -> "Relevé de notes de l'année passée";
            default -> type;
        };
    }

    // ── Outils ─────────────────────────────────────────────────────────────────

    private void store(SchoolEnrollment e, String type, MultipartFile file, List<Path> written) throws IOException {
        String storedName = e.getMatricule() + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolve(storedName);
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        written.add(target);
        String original = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            ? documentLabel(type) + ".pdf" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        documentRepository.save(SchoolDocument.builder().enrollment(e).type(type)
            .originalName(original.length() > 255 ? original.substring(0, 255) : original)
            .storedName(storedName).size(file.getSize()).uploadedAt(LocalDateTime.now()).build());
    }

    private Path resolve(String storedName) throws IOException {
        Path root = Paths.get(documentsDir).toAbsolutePath().normalize();
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) throw new IOException("Chemin de pièce invalide.");
        return path;
    }

    /** Fichier obligatoire, PDF (vérifié sur son contenu, pas seulement son nom), 10 Mo au plus. */
    static void checkPdf(MultipartFile file, String what) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Joignez " + what + " scanné(e) en PDF.");
        if (file.getSize() > MAX_PDF_SIZE) throw new IllegalArgumentException("Fichier trop lourd pour " + what + " (10 Mo maximum).");
        byte[] head = new byte[5];
        try (InputStream in = file.getInputStream()) {
            if (in.readNBytes(head, 0, 5) < 5 || !new String(head, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new IllegalArgumentException("Le fichier fourni pour " + what + " n'est pas un PDF.");
            }
        }
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Indiquez " + what + ".");
        return value.trim();
    }

    /** Seule règle : la date de naissance ne peut pas être dans le futur (aucune limite d'âge). */
    static LocalDate parseBirthDate(String value) {
        LocalDate d;
        try {
            d = LocalDate.parse(required(value, "la date de naissance"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Date de naissance invalide.");
        }
        if (d.isAfter(LocalDate.now())) throw new IllegalArgumentException("La date de naissance ne peut pas être dans le futur.");
        return d;
    }
}
