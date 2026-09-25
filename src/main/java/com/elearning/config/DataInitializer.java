package com.elearning.config;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.elearning.entity.Book;
import com.elearning.entity.Course;
import com.elearning.entity.Exam;
import com.elearning.entity.ExamQuestion;
import com.elearning.entity.ExamStatus;
import com.elearning.entity.ExamStudent;
import com.elearning.entity.Lesson;
import com.elearning.entity.Qcm;
import com.elearning.entity.QcmChoice;
import com.elearning.entity.QcmQuestion;
import com.elearning.entity.QcmStudent;
import com.elearning.entity.Role;
import com.elearning.entity.StudentExamStatus;
import com.elearning.entity.User;
import com.elearning.repository.BookRepository;
import com.elearning.repository.CourseRepository;
import com.elearning.repository.ExamRepository;
import com.elearning.repository.ExamStudentRepository;
import com.elearning.repository.QcmPassageRepository;
import com.elearning.repository.QcmRepository;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final BookRepository bookRepository;
    private final ExamRepository examRepository;
    private final ExamStudentRepository examStudentRepository;
    private final QcmRepository qcmRepository;
    private final QcmPassageRepository qcmPassageRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureQcmSchema();
        ensureVirtualClassesTable();

        // ── Comptes administrateur ────────────────────────────────────────────
        User admin = ensureAccount("admin@elearning.com", "Admin", "ELearning",
            "Admin@2024", Role.ROLE_ADMIN, null, null);
        ensureAccount("moussampthioune@gmail.com", "Moussa", "Thioune",
            "Admin@2024", Role.ROLE_ADMIN, null, null);

        // ── Comptes professeur (partenaires) ─────────────────────────────────
        User teacher = ensureAccount("teacher@elearning.com", "Moussa", "Diallo",
            "Teacher@2024", Role.ROLE_TEACHER, null,
            "Développeur senior passionné par l'enseignement avec 10 ans d'expérience en Java et Spring Boot.");
        ensureAccount("prof.sall@elearning.com", "Aminata", "Sall",
            "Teacher@2024", Role.ROLE_TEACHER, null,
            "Ingénieure en réseaux et télécommunications, 8 ans d'expérience en administration systèmes et cybersécurité.");
        ensureAccount("prof.ndiaye@elearning.com", "Cheikh", "Ndiaye",
            "Teacher@2024", Role.ROLE_TEACHER, null,
            "Expert en bases de données et systèmes d'information, ancien consultant pour plusieurs entreprises sénégalaises.");
        ensureAccount("prof.ba@elearning.com", "Fatou", "Ba",
            "Teacher@2024", Role.ROLE_TEACHER, null,
            "Data scientist et formatrice en Python, spécialisée en machine learning et analyse de données.");
        ensureAccount("prof.kane@elearning.com", "Oumar", "Kane",
            "Teacher@2024", Role.ROLE_TEACHER, null,
            "Développeur fullstack Angular/Spring Boot avec 6 ans d'expérience dans des startups tech africaines.");

        // ── Comptes étudiant (apprenants) ─────────────────────────────────────
        ensureAccount("student@elearning.com", "Ibrahima", "Sow",
            "Student@2024", Role.ROLE_STUDENT, "genie-logiciel", null);
        ensureAccount("moussampthioune@gmail.com", "Moussa", "Thioune",
            "Student@2024", Role.ROLE_STUDENT, "genie-logiciel", null);
        ensureAccount("moussaa.thioune@gmail.com", "Moussa", "Thioune",
            "Student@2024", Role.ROLE_STUDENT, "comptabilite", null);
        ensureAccount("damesck2@gmail.com", "Dame", "Sck",
            "Student@2024", Role.ROLE_STUDENT, "comptabilite", null);
        ensureAccount("biramendoye440@gmail.com", "Biram", "Ndiaye",
            "Student@2024", Role.ROLE_STUDENT, "comptabilite", null);
        ensureAccount("oumythioune760@gmail.com", "Oumy", "Thioune",
            "Student@2024", Role.ROLE_STUDENT, "comptabilite", null);
        ensureAccount("etudiant.diallo@elearning.com", "Mariama", "Diallo",
            "Student@2024", Role.ROLE_STUDENT, "genie-logiciel", null);
        ensureAccount("etudiant.fall@elearning.com", "Abdoulaye", "Fall",
            "Student@2024", Role.ROLE_STUDENT, "reseau", null);
        ensureAccount("etudiant.sarr@elearning.com", "Rokhaya", "Sarr",
            "Student@2024", Role.ROLE_STUDENT, "comptabilite", null);
        ensureAccount("etudiant.gueye@elearning.com", "Mamadou", "Gueye",
            "Student@2024", Role.ROLE_STUDENT, "genie-logiciel", null);
        ensureAccount("etudiant.mbaye@elearning.com", "Aissatou", "Mbaye",
            "Student@2024", Role.ROLE_STUDENT, "sante", null);
        ensureAccount("etudiant.toure@elearning.com", "Ousmane", "Touré",
            "Student@2024", Role.ROLE_STUDENT, "reseau", null);
        ensureAccount("etudiant.cisse@elearning.com", "Khadija", "Cissé",
            "Student@2024", Role.ROLE_STUDENT, "comptabilite", null);
        ensureAccount("etudiant.diouf@elearning.com", "Lamine", "Diouf",
            "Student@2024", Role.ROLE_STUDENT, "genie-logiciel", null);
        ensureAccount("etudiant.ndoye@elearning.com", "Binta", "Ndoye",
            "Student@2024", Role.ROLE_STUDENT, "sante", null);

        // ── Demandes en attente de validation (PAYMENT_SUBMITTED) ────────────
        // Étudiants en attente
        ensurePending("pending.koita@elearning.com", "Seydou", "Koïta",
            Role.ROLE_STUDENT, "genie-logiciel", "WAVE", "77 412 33 21", "WAVE-TXN-20240701-8821");
        ensurePending("pending.dieng@elearning.com", "Ndéye", "Dieng",
            Role.ROLE_STUDENT, "comptabilite", "ORANGE_MONEY", "76 534 89 10", "OM-REF-20240702-4412");
        ensurePending("pending.wane@elearning.com", "Babacar", "Wane",
            Role.ROLE_STUDENT, "reseau", "WAVE", "70 218 45 67", "WAVE-TXN-20240703-3309");
        ensurePending("pending.lo@elearning.com", "Coumba", "Lo",
            Role.ROLE_STUDENT, "sante", "ORANGE_MONEY", "78 901 22 34", "OM-REF-20240704-7756");
        ensurePending("pending.traore@elearning.com", "Moustapha", "Traoré",
            Role.ROLE_STUDENT, "genie-logiciel", "WAVE", "77 654 11 98", "WAVE-TXN-20240705-1147");
        // Professeurs en attente
        ensurePending("pending.prof.sy@elearning.com", "Adja", "Sy",
            Role.ROLE_TEACHER, null, "WAVE", "77 300 44 55", "WAVE-TXN-20240706-5523");
        ensurePending("pending.prof.diop@elearning.com", "Ibou", "Diop",
            Role.ROLE_TEACHER, null, "ORANGE_MONEY", "76 412 77 88", "OM-REF-20240707-9934");

        if (courseRepository.count() == 0) {
            createCourse("Introduction à Java", "Apprenez les bases de la programmation orientée objet avec Java", "java", "BEGINNER", teacher);
            createCourse("Python pour la Data Science", "Maîtrisez Python pour l'analyse de données et le Machine Learning", "python", "INTERMEDIATE", teacher);
            createCourse("Angular 17 - Guide complet", "Construisez des applications web modernes avec Angular", "angular", "INTERMEDIATE", teacher);
            createCourse("Spring Boot & REST API", "Développez des APIs REST robustes avec Spring Boot", "springboot", "INTERMEDIATE", teacher);
            createCourse("SQL & Bases de données", "Maîtrisez SQL de zéro à avancé avec des exercices pratiques", "sql", "BEGINNER", teacher);
            createCourse("JavaScript Moderne ES2024", "Du JavaScript classique aux dernières fonctionnalités ES2024", "javascript", "BEGINNER", teacher);
            createCourse("Algorithmes & Structures de données", "Les fondamentaux de l'algorithmique pour tout développeur", "algorithms", "INTERMEDIATE", teacher);
        }

        if (bookRepository.count() == 0) {
            seedLibrary();
        }

        // ── Créer un examen d'exemple POO Java ────────────────────────────────
        createSampleExam(teacher);

        // ── Créer les trois épreuves en ligne de comptabilité ────────────────
        createAccountingExams(teacher);

        // ── Créer un QCM d'exemple ────────────────────────────────────────────
        createSampleQcm(teacher);

        // ── Créer un QCM sur les exceptions et les String ─────────────────────
        createExceptionsStringsQcm(teacher);

        // ── Créer un devoir mixte avec étude de cas et calculs ───────────────
        createAccountingCaseQcm(teacher);

        log.info("Demo accounts ready — password for all: *@2024");
        log.info("  Admin:    admin@elearning.com / moussampthioune@gmail.com");
        log.info("  Teachers: teacher@elearning.com | prof.sall | prof.ndiaye | prof.ba | prof.kane @elearning.com");
        log.info("  Students: student@elearning.com | etudiant.diallo/fall/sarr/gueye/mbaye/toure/cisse/diouf/ndoye @elearning.com");
    }

private boolean isMysqlDatabase() {
    try {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) con -> {
            String product = con.getMetaData().getDatabaseProductName();
            if (product == null) return false;
            String p = product.toLowerCase(Locale.ROOT);
            return p.contains("mysql") || p.contains("mariadb");
        }));
    } catch (Exception ex) {
        return false;
    }
}

    private boolean columnExists(String tableName, String columnName) {
        String sql = isMysqlDatabase()
            ? "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"
            : "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void ensureVirtualClassesTable() {
        if (isMysqlDatabase()) {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS virtual_classes (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    scheduled_at TIMESTAMP NULL,
                    duration_minutes INT,
                    room_name VARCHAR(255),
                    recording_url VARCHAR(255),
                    status VARCHAR(255),
                    recording_data LONGTEXT,
                    thumbnail_data LONGTEXT,
                    recording_mime_type VARCHAR(255),
                    recording_filename VARCHAR(255),
                    teacher_id BIGINT,
                    course_id BIGINT,
                    created_at TIMESTAMP NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS virtual_class_students (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    virtual_class_id BIGINT NOT NULL,
                    student_name VARCHAR(255) NOT NULL,
                    student_email VARCHAR(255) NOT NULL,
                    PRIMARY KEY (id),
                    CONSTRAINT fk_virtual_class_students_virtual_class
                        FOREIGN KEY (virtual_class_id) REFERENCES virtual_classes(id) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);
            // Les tables créées avant ce code avaient des colonnes TEXT (64 Ko max), trop petites pour une vidéo
            ensureMysqlColumnType("virtual_classes", "recording_data", "longtext", "LONGTEXT");
            ensureMysqlColumnType("virtual_classes", "thumbnail_data", "longtext", "LONGTEXT");
            return;
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS virtual_classes (
                id BIGSERIAL PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                description TEXT,
                scheduled_at TIMESTAMP,
                duration_minutes INTEGER,
                room_name VARCHAR(255),
                recording_url VARCHAR(255),
                status VARCHAR(255),
                recording_data TEXT,
                thumbnail_data TEXT,
                recording_mime_type VARCHAR(255),
                recording_filename VARCHAR(255),
                teacher_id BIGINT,
                course_id BIGINT,
                created_at TIMESTAMP
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS virtual_class_students (
                id BIGSERIAL PRIMARY KEY,
                virtual_class_id BIGINT NOT NULL REFERENCES virtual_classes(id) ON DELETE CASCADE,
                student_name VARCHAR(255) NOT NULL,
                student_email VARCHAR(255) NOT NULL
            )
            """);
    }

    private void ensureQcmSchema() {
        if (isMysqlDatabase()) {
            ensureMysqlColumn("qcm_questions", "question_type", "VARCHAR(255)");
            jdbcTemplate.execute("UPDATE qcm_questions SET question_type = 'QCM' WHERE question_type IS NULL");
            jdbcTemplate.execute("ALTER TABLE qcm_questions MODIFY COLUMN question_type VARCHAR(255) NOT NULL DEFAULT 'QCM'");

            ensureMysqlColumn("qcm_questions", "correction_data", "TEXT");
            ensureMysqlColumn("qcm_questions", "case_scenario", "TEXT");
            ensureMysqlColumn("qcm_questions", "expected_answer", "TEXT");

            ensureMysqlColumn("qcms", "paper_correction_required", "BOOLEAN NOT NULL DEFAULT FALSE");
            ensureMysqlColumn("qcm_passages", "paper_correction_url", "VARCHAR(255)");
            ensureMysqlColumn("qcm_passages", "paper_correction_filename", "VARCHAR(255)");
            ensureMysqlColumn("qcm_passages", "manual_score", "INTEGER");
            ensureMysqlColumn("qcm_passages", "manual_correction_note", "TEXT");
            ensureMysqlColumn("qcm_passages", "ocr_score", "INTEGER");
            ensureMysqlColumn("qcm_passages", "ocr_correction_note", "TEXT");

            ensureMysqlColumn("exam_questions", "question_type", "VARCHAR(255)");
            jdbcTemplate.execute("UPDATE exam_questions SET question_type = 'QCM' WHERE question_type IS NULL");
            jdbcTemplate.execute("ALTER TABLE exam_questions MODIFY COLUMN question_type VARCHAR(255) NOT NULL DEFAULT 'QCM'");
            return;
        }

        // Existing installations may predate the mixed-question and paper-copy fields.
        jdbcTemplate.execute("ALTER TABLE qcm_questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(255)");
        jdbcTemplate.execute("UPDATE qcm_questions SET question_type = 'QCM' WHERE question_type IS NULL");
        jdbcTemplate.execute("ALTER TABLE qcm_questions ALTER COLUMN question_type SET DEFAULT 'QCM'");
        jdbcTemplate.execute("ALTER TABLE qcm_questions ALTER COLUMN question_type SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE qcm_questions ADD COLUMN IF NOT EXISTS correction_data TEXT");
        jdbcTemplate.execute("ALTER TABLE qcm_questions ADD COLUMN IF NOT EXISTS case_scenario TEXT");
        jdbcTemplate.execute("ALTER TABLE qcm_questions ADD COLUMN IF NOT EXISTS expected_answer TEXT");
        jdbcTemplate.execute("ALTER TABLE qcms ADD COLUMN IF NOT EXISTS paper_correction_required BOOLEAN NOT NULL DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE qcm_passages ADD COLUMN IF NOT EXISTS paper_correction_url VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE qcm_passages ADD COLUMN IF NOT EXISTS paper_correction_filename VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE qcm_passages ADD COLUMN IF NOT EXISTS manual_score INTEGER");
        jdbcTemplate.execute("ALTER TABLE qcm_passages ADD COLUMN IF NOT EXISTS manual_correction_note TEXT");
        jdbcTemplate.execute("ALTER TABLE qcm_passages ADD COLUMN IF NOT EXISTS ocr_score INTEGER");
        jdbcTemplate.execute("ALTER TABLE qcm_passages ADD COLUMN IF NOT EXISTS ocr_correction_note TEXT");
        jdbcTemplate.execute("ALTER TABLE exam_questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(255)");
        jdbcTemplate.execute("UPDATE exam_questions SET question_type = 'QCM' WHERE question_type IS NULL");
        jdbcTemplate.execute("ALTER TABLE exam_questions ALTER COLUMN question_type SET DEFAULT 'QCM'");
        jdbcTemplate.execute("ALTER TABLE exam_questions ALTER COLUMN question_type SET NOT NULL");
    }

    private void ensureMysqlColumnType(String tableName, String columnName, String dataType, String columnDefinition) {
        String currentType = jdbcTemplate.query(
            "SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            rs -> rs.next() ? rs.getString(1) : null, tableName, columnName);
        if (currentType == null) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        } else if (!currentType.equalsIgnoreCase(dataType)) {
            log.info("Migration {}.{} : {} -> {}", tableName, columnName, currentType, columnDefinition);
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private void ensureMysqlColumn(String tableName, String columnName, String columnDefinition) {
        if (!columnExists(tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private User ensureAccount(String email, String firstName, String lastName,
String rawPassword, Role role, String specialization, String bio) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = User.builder()
                .firstName(firstName).lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .specialization(specialization)
                .bio(bio)
                .enabled(true)
                .registrationStatus("APPROVED")
                .build();
            log.info("Creating demo account: {}", email);
            return userRepository.save(u);
        });
    }

    private void ensurePending(String email, String firstName, String lastName,
                               Role role, String specialization,
                               String paymentMethod, String paymentPhone, String paymentReference) {
        userRepository.findByEmail(email).orElseGet(() -> {
            User u = User.builder()
                .firstName(firstName).lastName(lastName)
                .email(email)
                .password(passwordEncoder.encode("Student@2024"))
                .role(role)
                .specialization(specialization)
                .enabled(false)
                .registrationStatus("PAYMENT_SUBMITTED")
                .paymentMethod(paymentMethod)
                .paymentPhone(paymentPhone)
                .paymentReference(paymentReference)
                .paymentSubmittedAt(LocalDateTime.now().minusHours((long)(Math.random() * 48 + 1)))
                .build();
            log.info("Creating pending registration: {}", email);
            return userRepository.save(u);
        });
    }

    private void seedLibrary() {
        List<Book> books = new ArrayList<>();

        books.add(Book.builder()
            .title("Une si longue lettre").author("Mariama Bâ")
            .description("Roman épistolaire majeur de la littérature africaine francophone. " +
                "À travers une longue lettre adressée à son amie Aïssatou, Ramatoulaye, récemment veuve, " +
                "fait le bilan de sa vie de femme au Sénégal. Une réflexion profonde sur la condition féminine, " +
                "la polygamie, la liberté et la modernité dans la société sénégalaise.")
            .category("Littérature").genre("Roman épistolaire")
            .language("Français").year(1979).pages(131)
            .publisher("Les Nouvelles Éditions Africaines").isbn("978-2-7236-0009-0")
            .available(true).build());

        books.add(Book.builder()
            .title("L'aventure ambiguë").author("Cheikh Hamidou Kane")
            .description("Chef-d'œuvre de la littérature africaine francophone. Samba Diallo, " +
                "jeune Peul issu d'une famille noble et religieuse, est envoyé à l'école française, " +
                "puis en France. Ce roman explore le déchirement entre la culture africaine traditionnelle " +
                "et la modernité occidentale, la quête d'identité et la crise spirituelle.")
            .category("Littérature").genre("Roman")
            .language("Français").year(1961).pages(191)
            .publisher("Julliard").isbn("978-2-264-00386-5")
            .available(true).build());

        books.add(Book.builder()
            .title("Les bouts de bois de Dieu").author("Ousmane Sembène")
            .description("Roman historique retraçant la grande grève des cheminots du Dakar-Niger " +
                "de 1947-1948. À travers de nombreux personnages, Sembène décrit la lutte des travailleurs " +
                "africains contre la domination coloniale française, la prise de conscience politique " +
                "et le rôle des femmes dans ce mouvement social.")
            .category("Littérature").genre("Roman historique")
            .language("Français").year(1960).pages(382)
            .publisher("Le Livre Contemporain").isbn("978-2-266-03244-0")
            .available(true).build());

        books.add(Book.builder()
            .title("Sous l'orage").author("Seydou Badian")
            .description("Premier grand roman malien, Sous l'orage aborde le conflit entre tradition " +
                "et modernité à travers l'histoire d'amour de Kany et Sibiri.")
            .category("Littérature").genre("Roman")
            .language("Français").year(1957).pages(157)
            .publisher("Présence Africaine").isbn("978-2-7087-0024-8")
            .available(true).build());

        books.add(Book.builder()
            .title("Le monde s'effondre (Things Fall Apart)").author("Chinua Achebe")
            .description("Le roman le plus célèbre de la littérature africaine, traduit en plus de " +
                "50 langues. L'histoire d'Okonkwo, guerrier igbo dans le Nigeria colonial de la fin du XIXe siècle.")
            .category("Littérature").genre("Roman")
            .language("Français").year(1958).pages(241)
            .publisher("Présence Africaine").isbn("978-2-07-036062-6")
            .available(true).build());

        books.add(Book.builder()
            .title("Cahier d'un retour au pays natal").author("Aimé Césaire")
            .description("Poème fondateur de la littérature antillaise et africaine, " +
                "et manifeste de la Négritude.")
            .category("Littérature").genre("Poésie")
            .language("Français").year(1939).pages(126)
            .publisher("Présence Africaine").isbn("978-2-7087-0628-8")
            .available(true).build());

        books.add(Book.builder()
            .title("Xala").author("Ousmane Sembène")
            .description("Roman satirique d'Ousmane Sembène. Satire mordante de la bourgeoisie africaine post-coloniale.")
            .category("Littérature").genre("Roman satirique")
            .language("Français").year(1973).pages(170)
            .publisher("Présence Africaine").isbn("978-2-7087-0397-3")
            .available(true).build());

        books.add(Book.builder()
            .title("Introduction aux algorithmes")
            .author("Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest")
            .description("La référence mondiale en algorithmique. Couvre les algorithmes de tri, " +
                "structures de données, graphes, programmation dynamique, et bien plus.")
            .category("Informatique").genre("Manuel universitaire")
            .language("Français").year(2010).pages(1312)
            .publisher("Dunod").isbn("978-2-10-054526-1")
            .available(true).build());

        books.add(Book.builder()
            .title("UML 2 - De l'apprentissage à la pratique").author("Laurent Audibert")
            .description("Guide complet pour maîtriser UML 2. Couvre tous les diagrammes UML.")
            .category("Informatique").genre("Manuel universitaire")
            .language("Français").year(2009).pages(356)
            .publisher("Ellipses").isbn("978-2-7298-4106-0")
            .available(true).build());

        books.add(Book.builder()
            .title("Design Patterns — Catalogue des modèles de conception")
            .author("Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides")
            .description("Le livre des 'Gang of Four' — la référence absolue en conception orientée objet.")
            .category("Informatique").genre("Manuel professionnel")
            .language("Français").year(1999).pages(480)
            .publisher("Vuibert").isbn("978-2-7117-8644-1")
            .available(true).build());

        books.add(Book.builder()
            .title("Clean Code — Coder proprement").author("Robert C. Martin (Uncle Bob)")
            .description("Un manuel de bonnes pratiques de développement logiciel.")
            .category("Informatique").genre("Manuel professionnel")
            .language("Français").year(2009).pages(431)
            .publisher("Pearson").isbn("978-0-13-235088-4")
            .available(true).build());

        books.add(Book.builder()
            .title("Génie logiciel — Méthodes et techniques").author("Ian Sommerville")
            .description("La référence en génie logiciel. Couvre le cycle de vie du logiciel, " +
                "les méthodes agiles (Scrum, XP), l'ingénierie des exigences.")
            .category("Informatique").genre("Manuel universitaire")
            .language("Français").year(2016).pages(810)
            .publisher("Pearson").isbn("978-2-7440-7710-3")
            .available(true).build());

        books.add(Book.builder()
            .title("Bases de données — Conception, réalisation et utilisation")
            .author("Ramez Elmasri, Shamkant B. Navathe")
            .description("Manuel de référence sur les systèmes de gestion de bases de données.")
            .category("Informatique").genre("Manuel universitaire")
            .language("Français").year(2017).pages(1008)
            .publisher("Pearson").isbn("978-2-7440-7840-7")
            .available(true).build());

        books.add(Book.builder()
            .title("Histoire générale de l'Afrique — Vol. I")
            .author("UNESCO (sous la dir. de Joseph Ki-Zerbo)")
            .description("Premier volume de la monumentale Histoire générale de l'Afrique publiée par l'UNESCO.")
            .category("Histoire").genre("Histoire")
            .language("Français").year(1980).pages(829)
            .publisher("UNESCO / Présence Africaine").isbn("978-92-3-201707-4")
            .available(true).build());

        books.add(Book.builder()
            .title("Discours sur le colonialisme").author("Aimé Césaire")
            .description("Essai-pamphlet dans lequel Aimé Césaire dénonce le colonialisme comme une forme de barbarie.")
            .category("Histoire").genre("Essai politique")
            .language("Français").year(1950).pages(92)
            .publisher("Présence Africaine").isbn("978-2-7087-0617-2")
            .available(true).build());

        bookRepository.saveAll(books);
        log.info("Bibliothèque initialisée : {} livres ajoutés.", books.size());
    }

    private void createCourse(String title, String description, String category, String level, User teacher) {
        Course course = Course.builder()
            .title(title).description(description)
            .category(category).level(level)
            .teacher(teacher).published(true).build();
        Course saved = courseRepository.save(course);

        List<String> lessonTitles = List.of(
            "Introduction et installation de l'environnement",
            "Variables, types de données et opérateurs",
            "Structures de contrôle : if/else, switch",
            "Boucles : for, while, do-while",
            "Fonctions et méthodes",
            "Collections et tableaux",
            "Programmation orientée objet",
            "Gestion des exceptions",
            "Projet final : Mini application"
        );

        for (int i = 0; i < lessonTitles.size(); i++) {
            Lesson lesson = Lesson.builder()
                .title(lessonTitles.get(i))
                .description("Leçon " + (i + 1) + " du cours " + title)
                .type(i % 3 == 0 ? Lesson.LessonType.VIDEO : i % 3 == 1 ? Lesson.LessonType.PDF : Lesson.LessonType.CODE_EXERCISE)
                .duration(20 + (i * 5))
                .orderIndex(i)
                .course(saved).build();
            saved.getLessons().add(lesson);
        }
        courseRepository.save(saved);
    }

    private void createSampleExam(User teacher) {
        Exam exam = examRepository.findAll().stream()
            .filter(e -> "Programmation Orientée Objet en Java".equals(e.getTitle()))
            .findFirst()
            .orElseGet(() -> {
                Exam newExam = Exam.builder()
                    .title("Programmation Orientée Objet en Java")
                    .description("Examen de 30 minutes couvrant les principes fondamentaux de la POO : classes, " +
                        "héritage, polymorphisme, encapsulation et abstraction. Répondez à chaque question en détail.")
                    .professor(teacher)
                    .status(ExamStatus.PUBLISHED)
                    .createdAt(LocalDateTime.now())
                    .build();

                List<ExamQuestion> questions = new ArrayList<>();

                questions.add(ExamQuestion.builder()
                    .exam(newExam)
                    .orderIndex(1)
                    .questionText("Définissez le concept d'encapsulation en programmation orientée objet et " +
                        "expliquez comment elle améliore la maintenabilité du code. Donnez un exemple avec du code Java.")
                    .referenceAnswer("L'encapsulation est le regroupement des données (attributs) et des méthodes " +
                        "(comportement) au sein d'une classe. Elle consiste à masquer les détails internes de l'objet " +
                        "en utilisant des modificateurs d'accès (private, public, protected). Avantages: contrôle de l'accès, " +
                        "sécurité des données, facilité de maintenance. Exemple: classe BankAccount avec balance private et " +
                        "méthodes public pour débiter/créditer.")
                    .maxScore(10)
                    .build());

                questions.add(ExamQuestion.builder()
                    .exam(newExam)
                    .orderIndex(2)
                    .questionText("Expliquez la différence entre l'héritage et le polymorphisme. " +
                        "Comment se manifeste le polymorphisme en Java?")
                    .referenceAnswer("Héritage: mécanisme permettant à une classe (subclass) d'hériter des attributs " +
                        "et méthodes d'une autre classe (superclass). Polymorphisme: capacité d'un objet à prendre plusieurs formes " +
                        "ou à être traité de plusieurs façons. Se manifeste par: 1) Polymorphisme de compilation (overloading) - " +
                        "plusieurs méthodes avec même nom mais paramètres différents. 2) Polymorphisme d'exécution (overriding) - " +
                        "une méthode redéfinie dans une classe fille. Exemple: List peut référencer ArrayList ou LinkedList.")
                    .maxScore(10)
                    .build());

                questions.add(ExamQuestion.builder()
                    .exam(newExam)
                    .orderIndex(3)
                    .questionText("Écrivez une classe Java 'Employe' avec les attributs suivants: " +
                        "id, nom, prenom, salaire, dateEmbauche. Incluez constructeur, getters/setters, " +
                        "et une méthode calculerAnciennete() retournant le nombre d'années d'expérience.")
                    .referenceAnswer("public class Employe {\n" +
                        "  private int id;\n" +
                        "  private String nom, prenom;\n" +
                        "  private double salaire;\n" +
                        "  private LocalDate dateEmbauche;\n" +
                        "  \n" +
                        "  public Employe(int id, String nom, String prenom, double salaire, LocalDate dateEmbauche) {\n" +
                        "    this.id = id;\n" +
                        "    this.nom = nom;\n" +
                        "    this.prenom = prenom;\n" +
                        "    this.salaire = salaire;\n" +
                        "    this.dateEmbauche = dateEmbauche;\n" +
                        "  }\n" +
                        "  \n" +
                        "  public int calculerAnciennete() {\n" +
                        "    return (int) ChronoUnit.YEARS.between(dateEmbauche, LocalDate.now());\n" +
                        "  }\n" +
                        "  // getters/setters...\n" +
                        "}")
                    .maxScore(15)
                    .build());

                questions.add(ExamQuestion.builder()
                    .exam(newExam)
                    .orderIndex(4)
                    .questionText("Qu'est-ce qu'une interface en Java? Quelle est la différence " +
                        "avec une classe abstraite? Donnez un exemple d'utilisation.")
                    .referenceAnswer("Interface: contrat définissant les méthodes que les classes doivent implémenter. " +
                        "Différence avec classe abstraite: interface ne peut avoir que des méthodes abstraites (avant Java 8), " +
                        "pas d'attributs d'instance, pas de constructeur. Classe abstraite: peut avoir des méthodes concrètes, " +
                        "des attributs, des constructeurs. Utilisation: Serializable, Comparable, Runnable. Une classe " +
                        "peut implémenter plusieurs interfaces mais n'héritera que d'une classe.")
                    .maxScore(10)
                    .build());

                questions.add(ExamQuestion.builder()
                    .exam(newExam)
                    .orderIndex(5)
                    .questionText("Créez une hiérarchie de classes pour un système de gestion de véhicules. " +
                        "Définissez une classe mère 'Vehicule' et au moins 2 classes filles ('Voiture', 'Moto'). " +
                        "Incluez une méthode polymorphe pour calculer la taxe.")
                    .referenceAnswer("public abstract class Vehicule {\n" +
                        "  protected String marque, modele;\n" +
                        "  protected int annee;\n" +
                        "  \n" +
                        "  public abstract double calculerTaxe();\n" +
                        "}\n" +
                        "public class Voiture extends Vehicule {\n" +
                        "  private int nbPortes;\n" +
                        "  @Override\n" +
                        "  public double calculerTaxe() { return nbPortes > 5 ? 150 : 100; }\n" +
                        "}\n" +
                        "public class Moto extends Vehicule {\n" +
                        "  private int cylindree;\n" +
                        "  @Override\n" +
                        "  public double calculerTaxe() { return cylindree > 500 ? 50 : 25; }\n" +
                        "}\n")
                    .maxScore(20)
                    .build());

                newExam.setQuestions(questions);
                return examRepository.save(newExam);
            });

        List<User> approvedStudents = userRepository.findAll().stream()
            .filter(user -> user.getRole() == Role.ROLE_STUDENT)
            .filter(user -> "APPROVED".equalsIgnoreCase(user.getRegistrationStatus()))
            .toList();

        for (User student : approvedStudents) {
            boolean alreadyAssigned = examStudentRepository.findByExamAndStudentEmail(exam, student.getEmail()).isPresent();
            if (alreadyAssigned) continue;

            ExamStudent examStudent = ExamStudent.builder()
                .exam(exam)
                .studentName(student.getFirstName() + " " + student.getLastName())
                .studentEmail(student.getEmail())
                .status(StudentExamStatus.INVITED)
                .accessToken(generateAccessToken())
                .invitedAt(LocalDateTime.now())
                .build();

            examStudentRepository.save(examStudent);
        }

        log.info("Examen d'exemple assuré: '{}' avec {} étudiants assignés.",
            exam.getTitle(), approvedStudents.size());
    }

    private void createAccountingExams(User teacher) {
        createAccountingExam(teacher, "Comptabilité générale - Licence 3",
            "Partie A : bilan, écritures, TVA, amortissement, résultat et provisions.", List.of(
                question("Le bilan d'une entreprise présente principalement :\nA. Les produits et les charges de l'exercice\nB. Le patrimoine de l'entreprise à une date donnée\nC. Les flux de trésorerie uniquement\nD. Le chiffre d'affaires mensuel", "B", 1),
                question("La relation fondamentale du bilan est :\nA. Actif = Produits - Charges\nB. Actif = Passif\nC. Passif = Charges + Produits\nD. Actif + Passif = Résultat", "B", 1),
                question("Lequel constitue généralement une immobilisation corporelle ?\nA. Stock de marchandises\nB. Créance client\nC. Machine industrielle\nD. Banque", "C", 1),
                question("Achat de marchandises pour 1 000 000 FCFA HT à crédit :\nA. Débit Achats / Crédit Fournisseurs\nB. Débit Fournisseurs / Crédit Achats\nC. Débit Banque / Crédit Achats\nD. Débit Achats / Crédit Banque", "A", 1),
                question("La TVA collectée correspond à :\nA. La TVA payée aux fournisseurs\nB. La TVA facturée aux clients\nC. La TVA sur les immobilisations uniquement\nD. Une charge définitive pour l'entreprise", "B", 1),
                question("L'amortissement d'une immobilisation permet principalement :\nA. D'augmenter sa valeur\nB. De constater la consommation progressive des avantages économiques\nC. De constater une entrée de trésorerie\nD. D'augmenter automatiquement le résultat", "B", 1),
                question("Le résultat comptable d'un exercice est généralement déterminé par :\nA. Actif - Passif\nB. Produits - Charges\nC. Charges - Produits\nD. Capitaux propres - Dettes", "B", 1),
                question("Lorsqu'une entreprise vend des marchandises à crédit :\nA. Sa créance client augmente\nB. Sa dette fournisseur augmente\nC. Sa trésorerie augmente immédiatement\nD. Ses charges diminuent nécessairement", "A", 1),
                question("Une provision est notamment constatée lorsqu'il existe :\nA. Une certitude absolue de bénéfice\nB. Un risque ou une obligation incertaine dans son échéance ou son montant\nC. Une augmentation certaine de trésorerie\nD. Un apport en capital", "B", 1),
                question("Le compte de résultat permet principalement de connaître :\nA. La situation patrimoniale à une date précise\nB. La performance économique sur une période\nC. Le montant exact des stocks physiques uniquement\nD. Le montant du capital social uniquement", "B", 1)
            ));

        createAccountingExam(teacher, "Comptabilité analytique - Licence 3",
            "Partie B : coûts, charges, marges et seuil de rentabilité.", List.of(
                question("La comptabilité analytique sert principalement à :\nA. Déterminer uniquement la TVA\nB. Analyser les coûts et la rentabilité des produits, activités ou centres\nC. Remplacer totalement la comptabilité générale\nD. Enregistrer uniquement les opérations bancaires", "B", 1),
                question("Une charge directe est une charge :\nA. Qui concerne nécessairement toute l'entreprise\nB. Qui peut être directement affectée à un produit ou objet de coût\nC. Qui ne peut jamais être calculée\nD. Qui correspond uniquement aux salaires", "B", 1),
                question("Une charge indirecte :\nA. Peut toujours être affectée directement à un seul produit\nB. Nécessite généralement une répartition entre plusieurs objets de coût\nC. Est toujours variable\nD. Est toujours fixe", "B", 1),
                question("Le coût d'achat d'une matière première comprend généralement :\nA. Prix d'achat + frais d'approvisionnement liés à l'achat\nB. Prix de vente - marge\nC. Charges administratives uniquement\nD. Coût de production + résultat", "A", 1),
                question("Le coût de production comprend notamment :\nA. Les charges liées à la fabrication du produit\nB. Uniquement le prix d'achat des matières\nC. Uniquement les frais financiers\nD. Uniquement les frais commerciaux", "A", 1),
                question("Le coût de revient correspond généralement :\nA. Au coût d'achat uniquement\nB. Au coût de production uniquement\nC. À l'ensemble des coûts supportés jusqu'à la mise à disposition ou vente\nD. Au chiffre d'affaires", "C", 1),
                question("Une charge fixe est une charge qui :\nA. Varie exactement avec chaque unité produite\nB. Reste globalement stable dans une certaine plage d'activité\nC. Est toujours nulle lorsque la production augmente\nD. Est toujours directe", "B", 1),
                question("Une charge variable :\nA. Évolue en fonction du niveau d'activité selon une relation donnée\nB. Ne dépend jamais du niveau d'activité\nC. Est toujours indirecte\nD. Est toujours financière", "A", 1),
                question("La marge sur coût variable est calculée par :\nA. Chiffre d'affaires - coûts variables\nB. Chiffre d'affaires - coûts fixes\nC. Coûts fixes - coûts variables\nD. Résultat + chiffre d'affaires", "A", 1),
                question("Le seuil de rentabilité correspond au niveau de chiffre d'affaires pour lequel :\nA. L'entreprise réalise son bénéfice maximum\nB. Le résultat est nul\nC. Les charges variables sont nulles\nD. Les charges fixes sont nulles", "B", 1)
            ));

        createAccountingExam(teacher, "Comptabilité analytique - Calculs - Licence 3",
            "Partie C : exercices de calcul sur les marges, le résultat et le seuil de rentabilité.", List.of(
                question("Chiffre d'affaires 20 000 000 FCFA et charges variables 12 000 000 FCFA. La marge sur coût variable est :\nA. 8 000 000 FCFA\nB. 12 000 000 FCFA\nC. 20 000 000 FCFA\nD. 32 000 000 FCFA", "A", 2),
                question("Chiffre d'affaires 30 000 000 FCFA, charges variables 18 000 000 FCFA et charges fixes 8 000 000 FCFA. Le résultat est :\nA. 4 000 000 FCFA\nB. 8 000 000 FCFA\nC. 12 000 000 FCFA\nD. 20 000 000 FCFA", "A", 2),
                question("Chiffre d'affaires 40 000 000 FCFA et marge sur coût variable 16 000 000 FCFA. Le taux de marge sur coût variable est :\nA. 20 %\nB. 30 %\nC. 40 %\nD. 60 %", "C", 2),
                question("Charges fixes 10 000 000 FCFA et taux de marge sur coût variable 25 %. Le seuil de rentabilité en chiffre d'affaires est :\nA. 2 500 000 FCFA\nB. 25 000 000 FCFA\nC. 40 000 000 FCFA\nD. 50 000 000 FCFA", "C", 2),
                question("Un produit est vendu 10 000 FCFA et son coût variable unitaire est de 6 000 FCFA. La marge sur coût variable unitaire est :\nA. 2 000 FCFA\nB. 4 000 FCFA\nC. 6 000 FCFA\nD. 16 000 FCFA", "B", 2)
            ));
    }

    private QuestionSeed question(String text, String answer, int score) {
        return new QuestionSeed(text, answer, score);
    }

    private void createAccountingExam(User teacher, String title, String description,
                                      List<QuestionSeed> seeds) {
        Exam exam = examRepository.findAll().stream()
            .filter(existing -> title.equals(existing.getTitle()))
            .findFirst()
            .orElseGet(() -> {
                Exam created = Exam.builder()
                    .title(title)
                    .description(description)
                    .professor(teacher)
                    .estimatedDurationMinutes(30)
                    .status(ExamStatus.PUBLISHED)
                    .createdAt(LocalDateTime.now())
                    .build();
                List<ExamQuestion> questions = new ArrayList<>();
                for (int i = 0; i < seeds.size(); i++) {
                    QuestionSeed seed = seeds.get(i);
                    questions.add(ExamQuestion.builder()
                        .exam(created)
                        .orderIndex(i + 1)
                        .questionText(seed.text())
                        .referenceAnswer(seed.answer())
                        .maxScore(seed.score())
                        .build());
                }
                created.setQuestions(questions);
                return examRepository.save(created);
            });

        List<User> students = userRepository.findAll().stream()
            .filter(user -> "APPROVED".equalsIgnoreCase(user.getRegistrationStatus()))
            .toList();
        for (User student : students) {
            if (examStudentRepository.findByExamAndStudentEmail(exam, student.getEmail()).isEmpty()) {
                examStudentRepository.save(ExamStudent.builder()
                    .exam(exam)
                    .studentName(student.getFirstName() + " " + student.getLastName())
                    .studentEmail(student.getEmail())
                    .status(StudentExamStatus.INVITED)
                    .accessToken(generateAccessToken())
                    .invitedAt(LocalDateTime.now())
                    .build());
            }
        }
    }

    private record QuestionSeed(String text, String answer, int score) {}

    private String generateAccessToken() {
        return java.util.UUID.randomUUID().toString();
    }

    private void createAccountingCaseQcm(User teacher) {
        final String title = "Devoir - Comptabilité analytique : étude de cas EcoPack";
        User featuredStudent = userRepository.findByEmail("moussampthioune@gmail.com")
            .orElseThrow(() -> new RuntimeException("Étudiant moussampthioune@gmail.com non trouvé"));

        Qcm qcm = qcmRepository.findByTitle(title).orElseGet(() -> {
            Qcm created = Qcm.builder()
                .title(title)
                .description("Devoir mixte : notions de comptabilité analytique, étude de cas et calculs de rentabilité.")
                .estimatedDurationMinutes(45)
                .paperCorrectionRequired(true)
                .professor(teacher)
                .status("PUBLISHED")
                .createdAt(LocalDateTime.now())
                .build();

            QcmQuestion q1 = QcmQuestion.builder()
                .qcm(created).orderIndex(1)
                .questionText("La marge sur coût variable se calcule par :")
                .points(2).questionType("QCM").build();
            q1.setChoices(new ArrayList<>(List.of(
                QcmChoice.builder().question(q1).orderIndex(1).choiceText("Chiffre d'affaires - coûts variables").isCorrect(true).build(),
                QcmChoice.builder().question(q1).orderIndex(2).choiceText("Chiffre d'affaires - charges fixes").isCorrect(false).build(),
                QcmChoice.builder().question(q1).orderIndex(3).choiceText("Charges fixes - coûts variables").isCorrect(false).build(),
                QcmChoice.builder().question(q1).orderIndex(4).choiceText("Résultat + charges fixes").isCorrect(false).build()
            )));

            QcmQuestion q2 = QcmQuestion.builder()
                .qcm(created).orderIndex(2)
                .questionText("Analysez la rentabilité de l'activité EcoPack et expliquez si l'entreprise peut accorder une remise commerciale.")
                .points(6).questionType("CASE")
                .caseScenario("La société EcoPack fabrique des kits de bureau destinés aux PME.\n\n"
                    + "Elle vend 1 200 kits au prix unitaire de 60 €. Le coût variable unitaire est de 35 € "
                    + "et les charges fixes mensuelles s'élèvent à 18 000 €.\n\n"
                    + "La direction souhaite mesurer la rentabilité avant d'accorder une remise commerciale.")
                .expectedAnswer("Calculer le chiffre d'affaires, le coût variable total, la marge sur coût variable, "
                    + "le résultat et argumenter sur la remise commerciale.")
                .build();

            QcmQuestion q3 = QcmQuestion.builder()
                .qcm(created).orderIndex(3)
                .questionText("Calculez les indicateurs chiffrés du cas EcoPack.")
                .points(6).questionType("PRACTICAL")
                .correctionData("{\"chiffre_affaires\":72000,\"cout_variable_total\":42000,\"marge_sur_cout_variable\":30000,\"resultat\":12000,\"seuil_rentabilite_quantite\":720,\"seuil_rentabilite_ca\":43200}")
                .build();

            created.setQuestions(new ArrayList<>(List.of(q1, q2, q3)));
            return qcmRepository.save(created);
        });

        qcm.setPaperCorrectionRequired(true);
        qcmRepository.save(qcm);
        ensureStudentAssigned(qcm, featuredStudent);
        userRepository.findAll().stream()
            .filter(user -> user.getRole() == Role.ROLE_STUDENT)
            .filter(user -> "APPROVED".equalsIgnoreCase(user.getRegistrationStatus()))
            .forEach(student -> ensureStudentAssigned(qcm, student));

        log.info("Devoir mixte créé/assuré: '{}' avec {} questions.", title, qcm.getQuestions().size());
    }

    // Ajoute l'étudiant au QCM s'il n'y est pas déjà (auto-réparation en cas d'échec partiel précédent)
    private void ensureStudentAssigned(Qcm qcm, User student) {
        boolean alreadyAssigned = qcm.getAssignedStudents().stream()
            .anyMatch(s -> s.getStudentEmail().equalsIgnoreCase(student.getEmail()));
        if (alreadyAssigned) return;

        qcm.getAssignedStudents().add(QcmStudent.builder()
            .qcm(qcm)
            .studentName(student.getFirstName() + " " + student.getLastName())
            .studentEmail(student.getEmail())
            .build());
        qcmRepository.save(qcm);
    }

    private void createSampleQcm(User teacher) {
        User student = userRepository.findByEmail("moussampthioune@gmail.com")
            .orElseThrow(() -> new RuntimeException("Étudiant moussampthioune@gmail.com non trouvé"));

        // Déjà créé (éventuellement lors d'un précédent démarrage) → s'assurer juste que l'étudiant est assigné
        java.util.Optional<Qcm> existing = qcmRepository.findByTitle("QCM - Concepts Fondamentaux Java");
        if (existing.isPresent()) {
            ensureStudentAssigned(existing.get(), student);
            return;
        }

        Qcm qcm = Qcm.builder()
            .title("QCM - Concepts Fondamentaux Java")
            .description("Quiz à choix multiples sur les concepts de base de Java et POO. " +
                "5 questions avec 4 réponses possibles. Temps limité.")
            .professor(teacher)
            .status("PUBLISHED")
            .createdAt(LocalDateTime.now())
            .build();

        // Questions du QCM
        List<QcmQuestion> questions = new ArrayList<>();

        // Question 1
        QcmQuestion q1 = QcmQuestion.builder()
            .qcm(qcm)
            .orderIndex(1)
            .questionText("Qu'est-ce qu'un package en Java?")
            .points(2)
            .build();
        List<QcmChoice> choices1 = new ArrayList<>();
        choices1.add(QcmChoice.builder().question(q1).orderIndex(1).choiceText("Un ensemble de classes organisées en répertoires").isCorrect(true).build());
        choices1.add(QcmChoice.builder().question(q1).orderIndex(2).choiceText("Un fichier exécutable Java").isCorrect(false).build());
        choices1.add(QcmChoice.builder().question(q1).orderIndex(3).choiceText("Un fichier de configuration").isCorrect(false).build());
        choices1.add(QcmChoice.builder().question(q1).orderIndex(4).choiceText("Un type de variable").isCorrect(false).build());
        q1.setChoices(choices1);
        questions.add(q1);

        // Question 2
        QcmQuestion q2 = QcmQuestion.builder()
            .qcm(qcm)
            .orderIndex(2)
            .questionText("Quel mot-clé Java permet d'éviter qu'une classe soit héritable?")
            .points(2)
            .build();
        List<QcmChoice> choices2 = new ArrayList<>();
        choices2.add(QcmChoice.builder().question(q2).orderIndex(1).choiceText("protected").isCorrect(false).build());
        choices2.add(QcmChoice.builder().question(q2).orderIndex(2).choiceText("final").isCorrect(true).build());
        choices2.add(QcmChoice.builder().question(q2).orderIndex(3).choiceText("private").isCorrect(false).build());
        choices2.add(QcmChoice.builder().question(q2).orderIndex(4).choiceText("abstract").isCorrect(false).build());
        q2.setChoices(choices2);
        questions.add(q2);

        // Question 3
        QcmQuestion q3 = QcmQuestion.builder()
            .qcm(qcm)
            .orderIndex(3)
            .questionText("Quelle est la portée d'une variable déclarée avec le mot-clé private?")
            .points(2)
            .build();
        List<QcmChoice> choices3 = new ArrayList<>();
        choices3.add(QcmChoice.builder().question(q3).orderIndex(1).choiceText("Accessible depuis n'importe où").isCorrect(false).build());
        choices3.add(QcmChoice.builder().question(q3).orderIndex(2).choiceText("Accessible uniquement dans la classe").isCorrect(true).build());
        choices3.add(QcmChoice.builder().question(q3).orderIndex(3).choiceText("Accessible dans le package").isCorrect(false).build());
        choices3.add(QcmChoice.builder().question(q3).orderIndex(4).choiceText("Accessible dans la classe et ses sous-classes").isCorrect(false).build());
        q3.setChoices(choices3);
        questions.add(q3);

        // Question 4
        QcmQuestion q4 = QcmQuestion.builder()
            .qcm(qcm)
            .orderIndex(4)
            .questionText("Quel mot-clé Java est utilisé pour implémenter une interface?")
            .points(2)
            .build();
        List<QcmChoice> choices4 = new ArrayList<>();
        choices4.add(QcmChoice.builder().question(q4).orderIndex(1).choiceText("extends").isCorrect(false).build());
        choices4.add(QcmChoice.builder().question(q4).orderIndex(2).choiceText("implements").isCorrect(true).build());
        choices4.add(QcmChoice.builder().question(q4).orderIndex(3).choiceText("inherits").isCorrect(false).build());
        choices4.add(QcmChoice.builder().question(q4).orderIndex(4).choiceText("uses").isCorrect(false).build());
        q4.setChoices(choices4);
        questions.add(q4);

        // Question 5
        QcmQuestion q5 = QcmQuestion.builder()
            .qcm(qcm)
            .orderIndex(5)
            .questionText("Quel est le rôle du mot-clé 'static' en Java?")
            .points(2)
            .build();
        List<QcmChoice> choices5 = new ArrayList<>();
        choices5.add(QcmChoice.builder().question(q5).orderIndex(1).choiceText("Rendre une variable ou méthode accessible sans créer d'instance de la classe").isCorrect(true).build());
        choices5.add(QcmChoice.builder().question(q5).orderIndex(2).choiceText("Rendre une classe immuable").isCorrect(false).build());
        choices5.add(QcmChoice.builder().question(q5).orderIndex(3).choiceText("Empêcher l'héritage").isCorrect(false).build());
        choices5.add(QcmChoice.builder().question(q5).orderIndex(4).choiceText("Définir une interface").isCorrect(false).build());
        q5.setChoices(choices5);
        questions.add(q5);

        qcm.setQuestions(questions);
        Qcm savedQcm = qcmRepository.save(qcm);
        ensureStudentAssigned(savedQcm, student);

        log.info("QCM d'exemple créé: '{}' avec {} questions. Étudiant: {}",
            savedQcm.getTitle(), questions.size(), student.getEmail());
    }

    private void createExceptionsStringsQcm(User teacher) {
        final String TITLE = "QCM - Exceptions et Chaînes de caractères (String) en Java";

        User student = userRepository.findByEmail("moussampthioune@gmail.com")
            .orElseThrow(() -> new RuntimeException("Étudiant moussampthioune@gmail.com non trouvé"));

        // Déjà créé (éventuellement lors d'un précédent démarrage) → s'assurer juste que l'étudiant est assigné
        java.util.Optional<Qcm> existing = qcmRepository.findByTitle(TITLE);
        if (existing.isPresent()) {
            ensureStudentAssigned(existing.get(), student);
            return;
        }

        Qcm qcm = Qcm.builder()
            .title(TITLE)
            .description("Quiz à choix multiples sur la gestion des exceptions (try/catch/finally, " +
                "hiérarchie Throwable) et la classe String en Java (immutabilité, comparaison, StringBuilder). " +
                "8 questions avec 4 réponses possibles.")
            .professor(teacher)
            .status("PUBLISHED")
            .createdAt(LocalDateTime.now())
            .build();

        List<QcmQuestion> questions = new ArrayList<>();

        // Question 1
        QcmQuestion eq1 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(1)
            .questionText("Quelle est la classe mère de toutes les exceptions et erreurs en Java ?")
            .points(2).build();
        List<QcmChoice> echoices1 = new ArrayList<>();
        echoices1.add(QcmChoice.builder().question(eq1).orderIndex(1).choiceText("Exception").isCorrect(false).build());
        echoices1.add(QcmChoice.builder().question(eq1).orderIndex(2).choiceText("Throwable").isCorrect(true).build());
        echoices1.add(QcmChoice.builder().question(eq1).orderIndex(3).choiceText("RuntimeException").isCorrect(false).build());
        echoices1.add(QcmChoice.builder().question(eq1).orderIndex(4).choiceText("Object").isCorrect(false).build());
        eq1.setChoices(echoices1);
        questions.add(eq1);

        // Question 2
        QcmQuestion eq2 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(2)
            .questionText("Quel mot-clé permet de lancer manuellement une exception en Java ?")
            .points(2).build();
        List<QcmChoice> echoices2 = new ArrayList<>();
        echoices2.add(QcmChoice.builder().question(eq2).orderIndex(1).choiceText("throws").isCorrect(false).build());
        echoices2.add(QcmChoice.builder().question(eq2).orderIndex(2).choiceText("throw").isCorrect(true).build());
        echoices2.add(QcmChoice.builder().question(eq2).orderIndex(3).choiceText("catch").isCorrect(false).build());
        echoices2.add(QcmChoice.builder().question(eq2).orderIndex(4).choiceText("raise").isCorrect(false).build());
        eq2.setChoices(echoices2);
        questions.add(eq2);

        // Question 3
        QcmQuestion eq3 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(3)
            .questionText("Quel bloc s'exécute toujours, qu'une exception soit levée ou non ?")
            .points(2).build();
        List<QcmChoice> echoices3 = new ArrayList<>();
        echoices3.add(QcmChoice.builder().question(eq3).orderIndex(1).choiceText("catch").isCorrect(false).build());
        echoices3.add(QcmChoice.builder().question(eq3).orderIndex(2).choiceText("try").isCorrect(false).build());
        echoices3.add(QcmChoice.builder().question(eq3).orderIndex(3).choiceText("finally").isCorrect(true).build());
        echoices3.add(QcmChoice.builder().question(eq3).orderIndex(4).choiceText("finalize").isCorrect(false).build());
        eq3.setChoices(echoices3);
        questions.add(eq3);

        // Question 4
        QcmQuestion eq4 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(4)
            .questionText("Qu'est-ce qui différencie une exception \"checked\" d'une exception \"unchecked\" en Java ?")
            .points(2).build();
        List<QcmChoice> echoices4 = new ArrayList<>();
        echoices4.add(QcmChoice.builder().question(eq4).orderIndex(1).choiceText("Aucune différence, ce sont des synonymes").isCorrect(false).build());
        echoices4.add(QcmChoice.builder().question(eq4).orderIndex(2).choiceText("La checked doit être déclarée (throws) ou capturée à la compilation, pas l'unchecked").isCorrect(true).build());
        echoices4.add(QcmChoice.builder().question(eq4).orderIndex(3).choiceText("L'unchecked ne peut jamais être capturée").isCorrect(false).build());
        echoices4.add(QcmChoice.builder().question(eq4).orderIndex(4).choiceText("La checked n'existe que pour les erreurs système").isCorrect(false).build());
        eq4.setChoices(echoices4);
        questions.add(eq4);

        // Question 5
        QcmQuestion sq1 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(5)
            .questionText("Que renvoie l'expression \"Bonjour\".equals(\"bonjour\") en Java ?")
            .points(2).build();
        List<QcmChoice> schoices1 = new ArrayList<>();
        schoices1.add(QcmChoice.builder().question(sq1).orderIndex(1).choiceText("true").isCorrect(false).build());
        schoices1.add(QcmChoice.builder().question(sq1).orderIndex(2).choiceText("false").isCorrect(true).build());
        schoices1.add(QcmChoice.builder().question(sq1).orderIndex(3).choiceText("Une erreur de compilation").isCorrect(false).build());
        schoices1.add(QcmChoice.builder().question(sq1).orderIndex(4).choiceText("null").isCorrect(false).build());
        sq1.setChoices(schoices1);
        questions.add(sq1);

        // Question 6
        QcmQuestion sq2 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(6)
            .questionText("Les objets de type String sont-ils mutables en Java ?")
            .points(2).build();
        List<QcmChoice> schoices2 = new ArrayList<>();
        schoices2.add(QcmChoice.builder().question(sq2).orderIndex(1).choiceText("Oui, on peut modifier leur contenu directement").isCorrect(false).build());
        schoices2.add(QcmChoice.builder().question(sq2).orderIndex(2).choiceText("Non, ils sont immuables : toute modification crée un nouvel objet").isCorrect(true).build());
        schoices2.add(QcmChoice.builder().question(sq2).orderIndex(3).choiceText("Seulement si déclarés avec 'final'").isCorrect(false).build());
        schoices2.add(QcmChoice.builder().question(sq2).orderIndex(4).choiceText("Seulement en dehors du String pool").isCorrect(false).build());
        sq2.setChoices(schoices2);
        questions.add(sq2);

        // Question 7
        QcmQuestion sq3 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(7)
            .questionText("Quelle classe utiliser pour concaténer efficacement de nombreuses chaînes dans une boucle ?")
            .points(2).build();
        List<QcmChoice> schoices3 = new ArrayList<>();
        schoices3.add(QcmChoice.builder().question(sq3).orderIndex(1).choiceText("String").isCorrect(false).build());
        schoices3.add(QcmChoice.builder().question(sq3).orderIndex(2).choiceText("StringBuilder").isCorrect(true).build());
        schoices3.add(QcmChoice.builder().question(sq3).orderIndex(3).choiceText("StringConstant").isCorrect(false).build());
        schoices3.add(QcmChoice.builder().question(sq3).orderIndex(4).choiceText("CharSequenceBuilder").isCorrect(false).build());
        sq3.setChoices(schoices3);
        questions.add(sq3);

        // Question 8
        QcmQuestion sq4 = QcmQuestion.builder()
            .qcm(qcm).orderIndex(8)
            .questionText("Que retourne \"Hello\".charAt(1) ?")
            .points(2).build();
        List<QcmChoice> schoices4 = new ArrayList<>();
        schoices4.add(QcmChoice.builder().question(sq4).orderIndex(1).choiceText("'H'").isCorrect(false).build());
        schoices4.add(QcmChoice.builder().question(sq4).orderIndex(2).choiceText("'e'").isCorrect(true).build());
        schoices4.add(QcmChoice.builder().question(sq4).orderIndex(3).choiceText("'l'").isCorrect(false).build());
        schoices4.add(QcmChoice.builder().question(sq4).orderIndex(4).choiceText("Une erreur (index invalide)").isCorrect(false).build());
        sq4.setChoices(schoices4);
        questions.add(sq4);

        qcm.setQuestions(questions);
        Qcm savedQcm = qcmRepository.save(qcm);
        ensureStudentAssigned(savedQcm, student);

        log.info("QCM 'Exceptions et String' créé: '{}' avec {} questions. Étudiant: {}",
            savedQcm.getTitle(), questions.size(), student.getEmail());
    }
}