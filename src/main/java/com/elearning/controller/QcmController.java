package com.elearning.controller;

import com.elearning.entity.*;
import com.elearning.repository.*;
import com.elearning.service.StudentListParserService;
import com.elearning.service.DocumentTextExtractorService;
import com.elearning.service.FileStorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher/qcms")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
@RequiredArgsConstructor
public class QcmController {

    private final QcmRepository qcmRepo;
    private final QcmPassageRepository passageRepo;
    private final UserRepository userRepo;
    private final StudentListParserService parserService;
    private final DocumentTextExtractorService documentTextExtractorService;
    private final FileStorageService fileStorageService;

    // ── DTOs ───────────────────────────────────────────────────────────────

    @Data static class ChoiceInput   { String choiceText; Boolean isCorrect; }
    @Data static class QuestionInput { String questionText; Integer points; String questionType; String correctionData; String caseScenario; String expectedAnswer; List<ChoiceInput> choices; }
    @Data static class StudentInput  { String name; String email; String firstName; String lastName; String level; String password; }
    @Data static class QcmInput     { String title; String description; Integer estimatedDurationMinutes; Boolean paperCorrectionRequired; List<QuestionInput> questions; List<StudentInput> students; }

    @Data static class ChoiceDto    { Long id; String choiceText; Boolean isCorrect; Integer orderIndex; }
    @Data static class QuestionDto  { Long id; String questionText; Integer points; Integer orderIndex; String questionType; String correctionData; String caseScenario; String expectedAnswer; List<ChoiceDto> choices; }
    @Data static class StudentDto   { Long id; String studentName; String studentEmail; String firstName; String lastName; String level; String password; }
    @Data static class QcmDto {
        Long id; String title; String description; Integer estimatedDurationMinutes; Boolean paperCorrectionRequired; String status;
        String professorName; int questionCount; int studentCount; String createdAt;
        List<QuestionDto> questions;
        List<StudentDto> students;
    }

    @Data static class PassageResultDto {
        Long passageId; Long studentId; String studentName; String studentEmail; String studentLevel;
        Integer score; Integer maxScore; String percentage; String submittedAt;
        Integer manualScore; String manualCorrectionNote; Integer ocrScore; String ocrCorrectionNote; String paperCorrectionUrl; String paperCorrectionFilename;
        String status; String documentAnswer; String correctionText;
        List<ReponseDetailDto> reponses;
    }
    @Data static class ReponseDetailDto {
        Long questionId; String questionText; Integer points; String questionType;
        String choiceSelected; Boolean isCorrect; String correctChoice; String textAnswer;
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    private ChoiceDto toChoiceDto(QcmChoice c) {
        ChoiceDto d = new ChoiceDto();
        d.id = c.getId(); d.choiceText = c.getChoiceText();
        d.isCorrect = c.getIsCorrect(); d.orderIndex = c.getOrderIndex();
        return d;
    }

    private QuestionDto toQuestionDto(QcmQuestion q) {
        QuestionDto d = new QuestionDto();
        d.id = q.getId(); d.questionText = q.getQuestionText();
        d.points = q.getPoints(); d.orderIndex = q.getOrderIndex();
        d.questionType = q.getQuestionType();
        d.correctionData = q.getCorrectionData();
        d.caseScenario = q.getCaseScenario();
        d.expectedAnswer = q.getExpectedAnswer();
        d.choices = q.getChoices().stream().map(this::toChoiceDto).collect(Collectors.toList());
        return d;
    }

    private QcmDto toQcmDto(Qcm qcm, boolean withQuestions) {
        QcmDto d = new QcmDto();
        d.id = qcm.getId(); d.title = qcm.getTitle();
        d.description = qcm.getDescription(); d.status = qcm.getStatus();
        d.estimatedDurationMinutes = qcm.getEstimatedDurationMinutes();
        d.paperCorrectionRequired = Boolean.TRUE.equals(qcm.getPaperCorrectionRequired());
        d.professorName = qcm.getProfessor().getFirstName() + " " + qcm.getProfessor().getLastName();
        d.questionCount = qcm.getQuestions().size();
        d.studentCount  = qcm.getAssignedStudents().size();
        d.createdAt = qcm.getCreatedAt() != null ? qcm.getCreatedAt().toString() : null;
        if (withQuestions) {
            d.questions = qcm.getQuestions().stream().map(this::toQuestionDto).collect(Collectors.toList());
            d.students  = qcm.getAssignedStudents().stream().map(s -> {
                StudentDto sd = new StudentDto();
                sd.id = s.getId(); sd.studentName = s.getStudentName(); sd.studentEmail = s.getStudentEmail();
                sd.firstName = s.getFirstName(); sd.lastName = s.getLastName();
                sd.level = s.getLevel(); sd.password = s.getAccessPassword();
                return sd;
            }).collect(Collectors.toList());
        }
        return d;
    }

    private void applyStudents(Qcm qcm, List<StudentInput> inputs) {
        List<StudentInput> valid = inputs == null ? List.of() : inputs.stream()
            .filter(si -> si.email != null && !si.email.isBlank()).toList();
        validateStudents(valid);

        Set<String> usedPasswords = valid.stream().map(si -> trimToNull(si.password))
            .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        qcm.getAssignedStudents().clear();
        for (StudentInput si : valid) {
            String password = trimToNull(si.password);
            if (password == null) password = generateUniquePassword(usedPasswords);
            qcm.getAssignedStudents().add(QcmStudent.builder()
                .qcm(qcm).studentName(displayName(si))
                .studentEmail(si.email.trim().toLowerCase())
                .firstName(trimToNull(si.firstName)).lastName(trimToNull(si.lastName))
                .level(trimToNull(si.level)).accessPassword(password).build());
        }
    }

    /** Refuse les emails ou mots de passe en double : chaque étudiant doit avoir un mot de passe qui lui est propre. */
    private void validateStudents(List<StudentInput> students) {
        Set<String> emails = new HashSet<>();
        Map<String, String> passwordOwners = new HashMap<>();
        for (StudentInput si : students) {
            String email = si.email.trim().toLowerCase();
            if (!emails.add(email)) {
                throw new IllegalArgumentException("L'email " + email + " apparaît plusieurs fois dans la liste des étudiants.");
            }
            String password = trimToNull(si.password);
            if (password == null) continue;
            String owner = passwordOwners.putIfAbsent(password, displayName(si));
            if (owner != null) {
                throw new IllegalArgumentException("Le mot de passe « " + password + " » est attribué à la fois à "
                    + owner + " et à " + displayName(si) + ". Chaque étudiant doit avoir un mot de passe unique.");
            }
        }
    }

    private static String displayName(StudentInput si) {
        String composed = ((si.firstName != null ? si.firstName.trim() : "") + " "
            + (si.lastName != null ? si.lastName.trim() : "")).trim();
        if (!composed.isEmpty()) return composed;
        if (si.name != null && !si.name.isBlank()) return si.name.trim();
        return si.email.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Sans caractères ambigus (0/O, 1/l/I) pour faciliter la saisie par l'étudiant
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    static String generateUniquePassword(Set<String> usedPasswords) {
        String password;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
            password = sb.toString();
        } while (!usedPasswords.add(password));
        return password;
    }

    // ── Endpoints CRUD ─────────────────────────────────────────────────────

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<QcmDto>> list(Authentication auth) {
        User prof = userRepo.findByEmail(auth.getName()).orElseThrow();
        List<Qcm> qcms = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))
            ? qcmRepo.findAll().stream()
                .sorted(java.util.Comparator.comparing(Qcm::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList()
            : qcmRepo.findByProfessorOrderByCreatedAtDesc(prof);
        List<QcmDto> list = qcms
            .stream().map(q -> toQcmDto(q, false)).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<QcmDto> get(@PathVariable Long id, Authentication auth) {
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        return ResponseEntity.ok(toQcmDto(qcm, true));
    }

    // ── Parse liste étudiants (prévisualisation, rien sauvegardé) ─────────
    @PostMapping("/parse-students")
    public ResponseEntity<?> parseStudents(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            if (file == null || file.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide ou manquant"));
            List<StudentListParserService.StudentInfo> list = parserService.parseFile(file);
            Set<String> usedPasswords = list.stream().map(s -> trimToNull(s.password()))
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
            List<Map<String, String>> result = new ArrayList<>();
            for (StudentListParserService.StudentInfo s : list) {
                Map<String, String> row = new HashMap<>();
                row.put("name", s.name());
                row.put("email", s.email());
                row.put("firstName", s.firstName() != null ? s.firstName() : "");
                row.put("lastName", s.lastName() != null ? s.lastName() : "");
                row.put("level", s.level() != null ? s.level() : "");
                // Mot de passe absent du fichier : on en propose un, que le professeur peut modifier
                row.put("password", s.password() != null ? s.password() : generateUniquePassword(usedPasswords));
                result.add(row);
            }
            return ResponseEntity.ok(Map.of("students", result, "count", list.size()));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody QcmInput input, Authentication auth) {
        try {
            validateStudents(input.students == null ? List.of() : input.students.stream()
                .filter(si -> si.email != null && !si.email.isBlank()).toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        User prof = userRepo.findByEmail(auth.getName()).orElseThrow();
        Qcm qcm = Qcm.builder().title(input.title).description(input.description)
            .estimatedDurationMinutes(input.estimatedDurationMinutes != null ? input.estimatedDurationMinutes : 30)
            .paperCorrectionRequired(Boolean.TRUE.equals(input.paperCorrectionRequired))
            .professor(prof).status("DRAFT").build();

        if (input.questions != null) {
            for (int qi = 0; qi < input.questions.size(); qi++) {
                QuestionInput qin = input.questions.get(qi);
                QcmQuestion q = QcmQuestion.builder()
                    .qcm(qcm).questionText(qin.questionText)
                    .points(qin.points != null ? qin.points : 1)
                    .questionType(qin.questionType != null ? qin.questionType : "QCM")
                    .correctionData(qin.correctionData)
                    .caseScenario(qin.caseScenario)
                    .expectedAnswer(qin.expectedAnswer)
                    .orderIndex(qi).build();
                if (qin.choices != null) {
                    for (int ci = 0; ci < qin.choices.size(); ci++) {
                        ChoiceInput cin = qin.choices.get(ci);
                        q.getChoices().add(QcmChoice.builder()
                            .question(q).choiceText(cin.choiceText)
                            .isCorrect(Boolean.TRUE.equals(cin.isCorrect))
                            .orderIndex(ci).build());
                    }
                }
                qcm.getQuestions().add(q);
            }
        }
        applyStudents(qcm, input.students);
        return ResponseEntity.ok(toQcmDto(qcmRepo.save(qcm), true));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody QcmInput input, Authentication auth) {
        try {
            validateStudents(input.students == null ? List.of() : input.students.stream()
                .filter(si -> si.email != null && !si.email.isBlank()).toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        qcm.setTitle(input.title);
        qcm.setDescription(input.description);
        qcm.setEstimatedDurationMinutes(input.estimatedDurationMinutes != null ? input.estimatedDurationMinutes : 30);
        qcm.setPaperCorrectionRequired(Boolean.TRUE.equals(input.paperCorrectionRequired));
        qcm.getQuestions().clear();

        if (input.questions != null) {
            for (int qi = 0; qi < input.questions.size(); qi++) {
                QuestionInput qin = input.questions.get(qi);
                QcmQuestion q = QcmQuestion.builder()
                    .qcm(qcm).questionText(qin.questionText)
                    .points(qin.points != null ? qin.points : 1)
                    .questionType(qin.questionType != null ? qin.questionType : "QCM")
                    .correctionData(qin.correctionData)
                    .caseScenario(qin.caseScenario)
                    .expectedAnswer(qin.expectedAnswer)
                    .orderIndex(qi).build();
                if (qin.choices != null) {
                    for (int ci = 0; ci < qin.choices.size(); ci++) {
                        ChoiceInput cin = qin.choices.get(ci);
                        q.getChoices().add(QcmChoice.builder()
                            .question(q).choiceText(cin.choiceText)
                            .isCorrect(Boolean.TRUE.equals(cin.isCorrect))
                            .orderIndex(ci).build());
                    }
                }
                qcm.getQuestions().add(q);
            }
        }
        applyStudents(qcm, input.students);
        return ResponseEntity.ok(toQcmDto(qcmRepo.save(qcm), true));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> uploadDocuments(
            @PathVariable Long id,
            @RequestPart("subjectFile") MultipartFile subjectFile,
            @RequestPart("correctionFile") MultipartFile correctionFile) {
        if (subjectFile == null || subjectFile.isEmpty() || correctionFile == null || correctionFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le sujet et la correction sont obligatoires."));
        }
        try {
            Qcm qcm = qcmRepo.findById(id).orElseThrow();
            qcm.setSubjectFileUrl(fileStorageService.store(subjectFile, "devoirs/sujets"));
            qcm.setCorrectionFileUrl(fileStorageService.store(correctionFile, "devoirs/corrections"));
            qcm.setSubjectText(documentTextExtractorService.extractText(subjectFile));
            qcm.setCorrectionText(documentTextExtractorService.extractText(correctionFile));
            ensureDocumentCaseQuestion(qcm);
            return ResponseEntity.ok(toQcmDto(qcmRepo.save(qcm), true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de lire le sujet ou la correction."));
        }
    }

    public static void ensureDocumentCaseQuestion(Qcm qcm) {
        if (qcm == null) return;

        boolean hasCaseQuestion = qcm.getQuestions() != null && qcm.getQuestions().stream().anyMatch(question ->
            "CASE".equalsIgnoreCase(question.getQuestionType())
                || ("PRACTICAL".equalsIgnoreCase(question.getQuestionType())
                    && question.getCaseScenario() != null && !question.getCaseScenario().isBlank())
        );
        if (hasCaseQuestion) return;

        String subjectText = qcm.getSubjectText() == null ? "" : qcm.getSubjectText().trim();
        String correctionText = qcm.getCorrectionText() == null ? "" : qcm.getCorrectionText().trim();
        if (subjectText.isBlank()) return;

        if (qcm.getQuestions() == null) {
            qcm.setQuestions(new ArrayList<>());
        }

        QcmQuestion question = QcmQuestion.builder()
            .qcm(qcm)
            .questionText(subjectText.length() > 2000 ? subjectText.substring(0, 2000).trim() : subjectText)
            .points(10)
            .orderIndex(qcm.getQuestions().size())
            .questionType("CASE")
            .caseScenario(subjectText.length() > 500 ? subjectText.substring(0, 500).trim() : subjectText)
            .correctionData(correctionText)
            .expectedAnswer(correctionText.isBlank() ? "Référence de correction fournie par le professeur." : correctionText)
            .choices(new ArrayList<>())
            .build();

        qcm.getQuestions().add(question);
    }

    // ── Ajouter des étudiants à un QCM existant ───────────────────────────
    @PostMapping(value = "/{id}/add-students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> addStudents(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            Qcm qcm = qcmRepo.findById(id).orElseThrow();
            java.util.Set<String> existing = qcm.getAssignedStudents().stream()
                .map(QcmStudent::getStudentEmail).collect(Collectors.toSet());
            Set<String> usedPasswords = qcm.getAssignedStudents().stream().map(QcmStudent::getAccessPassword)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
            int added = 0;
            for (StudentListParserService.StudentInfo info : parserService.parseFile(file)) {
                if (!existing.add(info.email())) continue;
                String password = trimToNull(info.password());
                if (password != null && !usedPasswords.add(password)) {
                    throw new IllegalArgumentException("Le mot de passe « " + password + " » de " + info.name()
                        + " est déjà attribué à un autre étudiant de ce devoir.");
                }
                if (password == null) password = generateUniquePassword(usedPasswords);
                qcm.getAssignedStudents().add(QcmStudent.builder()
                    .qcm(qcm).studentName(info.name())
                    .studentEmail(info.email())
                    .firstName(info.firstName()).lastName(info.lastName())
                    .level(info.level()).accessPassword(password).build());
                added++;
            }
            qcmRepo.save(qcm);
            return ResponseEntity.ok(Map.of("message", added + " étudiant(s) ajouté(s)", "total", qcm.getAssignedStudents().size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Erreur"));
        }
    }

    @PostMapping("/{id}/publish")
    @Transactional
    public ResponseEntity<Map<String, String>> publish(@PathVariable Long id) {
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        qcm.setStatus("PUBLISHED");
        qcmRepo.save(qcm);
        return ResponseEntity.ok(Map.of("message", "QCM publié."));
    }

    @PostMapping("/{id}/unpublish")
    @Transactional
    public ResponseEntity<Map<String, String>> unpublish(@PathVariable Long id) {
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        qcm.setStatus("DRAFT");
        qcmRepo.save(qcm);
        return ResponseEntity.ok(Map.of("message", "QCM remis en brouillon."));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        qcmRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Télécharger le modèle Word ───────────────────────────────────────

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── Titre du document ──────────────────────────────────────────
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setStyle("Heading1");
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("Titre: Mon QCM Java");
            titleRun.setBold(true);

            addPara(doc, "Description: Instructions pour les étudiants (durée, consignes...)");
            addPara(doc, "");

            // ── Question 1 ─────────────────────────────────────────────────
            addBold(doc, "Question 1: Quelle est la différence entre == et equals() en Java ?");
            addPara(doc, "A) Il n'y a aucune différence");
            addPara(doc, "B) == compare les références, equals() compare le contenu");
            addPara(doc, "C) equals() compare les références, == compare le contenu");
            addPara(doc, "D) == ne fonctionne pas avec les objets");
            addGreen(doc, "Bonne réponse: B");
            addPara(doc, "");

            // ── Question 2 ─────────────────────────────────────────────────
            addBold(doc, "Question 2: Quel mot-clé permet de définir une constante en Java ?");
            addPara(doc, "A) static");
            addPara(doc, "B) const");
            addPara(doc, "C) final");
            addPara(doc, "D) immutable");
            addGreen(doc, "Bonne réponse: C");
            addPara(doc, "");

            // ── Question 3 (exemple avec points) ──────────────────────────
            addBold(doc, "Question 3 (2 pts): Combien d'interfaces une classe Java peut-elle implémenter ?");
            addPara(doc, "A) Une seule");
            addPara(doc, "B) Deux au maximum");
            addPara(doc, "C) Autant que nécessaire");
            addPara(doc, "D) Cela dépend de la JVM");
            addGreen(doc, "Bonne réponse: C");
            addPara(doc, "");

            // ── Section explication ────────────────────────────────────────
            addPara(doc, "");
            XWPFParagraph notePara = doc.createParagraph();
            XWPFRun noteRun = notePara.createRun();
            noteRun.setText("─── Format accepté (à respecter) ───────────────────────────────────────────────");
            noteRun.setColor("888888");
            noteRun.setFontSize(9);

            String[] rules = {
                "• Titre du QCM     →  Titre: <texte>   ou  QCM: <texte>",
                "• Description      →  Description: <texte>",
                "• Nouvelle question →  Question N: <énoncé>",
                "• Choix de réponse →  A) <texte>   B) <texte>   C) <texte>   D) <texte>",
                "• Bonne réponse    →  Bonne réponse: B   (la lettre A/B/C/D suffit)",
                "• Points optionnel →  Question N (2 pts): <énoncé>",
                "• Lignes vides entre les questions : autorisées et ignorées"
            };
            for (String rule : rules) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(rule);
                r.setFontSize(9);
                r.setColor("555555");
            }

            doc.write(out);
            byte[] bytes = out.toByteArray();

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_qcm.docx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(bytes.length)
                .body(bytes);
        }
    }

    private XWPFParagraph addPara(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        return p;
    }

    private void addBold(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setColor("1e1b4b");
    }

    private void addGreen(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setColor("059669");
    }

    // ── Import depuis fichier Word ────────────────────────────────────────

    @PostMapping("/parse-word")
    public ResponseEntity<?> parseWord(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {

            List<QuestionInput> questions = new ArrayList<>();
            QuestionInput currentQ = null;
            String title = "QCM importé";
            String description = "";
            boolean titleRead = false;

            for (XWPFParagraph para : doc.getParagraphs()) {
                String line = para.getText() == null ? "" : para.getText().trim();
                if (line.isEmpty()) continue;

                String low = line.toLowerCase(java.util.Locale.ROOT);

                if (!titleRead && (low.startsWith("qcm:") || low.startsWith("titre:"))) {
                    title = afterColon(line);
                    titleRead = true;
                    continue;
                }
                try {
                    if (!titleRead && para.getStyleID() != null
                            && para.getStyleID().toLowerCase(java.util.Locale.ROOT).contains("heading")) {
                        title = line; titleRead = true; continue;
                    }
                } catch (Exception ignored) {}

                if (low.startsWith("description:")) {
                    description = afterColon(line);
                    continue;
                }

                if (isQuestionHeader(line, low)) {
                    if (currentQ != null && !currentQ.questionText.isEmpty()) questions.add(currentQ);
                    currentQ = new QuestionInput();
                    currentQ.points = 1;
                    currentQ.choices = new ArrayList<>();
                    currentQ.questionText = extractQuestionText(line);
                    if (low.contains("pt")) {
                        try {
                            String pts = line.replaceAll(".*\\((\\d+)\\s*[pP][tT].*\\).*", "$1");
                            if (!pts.equals(line)) currentQ.points = Integer.parseInt(pts);
                        } catch (Exception ignored) {}
                    }
                    continue;
                }

                if (currentQ == null) continue;

                if (isChoiceLine(line)) {
                    ChoiceInput ci = new ChoiceInput();
                    ci.choiceText = extractChoiceText(line);
                    ci.isCorrect = false;
                    currentQ.choices.add(ci);
                    continue;
                }

                if (isAnswerLine(low)) {
                    int colon = line.indexOf(':');
                    if (colon >= 0) {
                        String ans = line.substring(colon + 1).trim().toUpperCase(java.util.Locale.ROOT);
                        if (!ans.isEmpty()) {
                            int idx = "ABCDE".indexOf(ans.charAt(0));
                            if (idx >= 0 && idx < currentQ.choices.size()) {
                                currentQ.choices.forEach(c -> c.isCorrect = false);
                                currentQ.choices.get(idx).isCorrect = true;
                            }
                        }
                    }
                    continue;
                }

                if (low.startsWith("points:") || low.startsWith("pts:")) {
                    try { currentQ.points = Integer.parseInt(line.replaceAll("[^0-9]", "")); }
                    catch (Exception ignored) {}
                    continue;
                }

                if (currentQ.questionText == null || currentQ.questionText.isEmpty()) {
                    currentQ.questionText = line;
                }
            }

            if (currentQ != null && currentQ.questionText != null && !currentQ.questionText.isEmpty()) {
                questions.add(currentQ);
            }

            for (QuestionInput q : questions) {
                if (q.choices != null && !q.choices.isEmpty()
                        && q.choices.stream().noneMatch(c -> Boolean.TRUE.equals(c.isCorrect))) {
                    q.choices.get(0).isCorrect = true;
                }
            }

            return ResponseEntity.ok(Map.of(
                "title", title,
                "description", description,
                "questions", questions
            ));

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de lire le fichier : " + msg));
        }
    }

    private String afterColon(String line) {
        int i = line.indexOf(':');
        return i >= 0 && i < line.length() - 1 ? line.substring(i + 1).trim() : "";
    }

    private boolean isQuestionHeader(String line, String low) {
        if (low.startsWith("question") && (line.contains(":") || line.contains(".") || line.contains("-") || line.contains("—"))) {
            return true;
        }
        if (low.startsWith("qcm") && (line.contains(":") || line.contains(".") || line.contains("-") || line.contains("—"))) {
            return true;
        }
        return low.matches("(?i)^(qcm|question)\\s*\\d+.*");
    }

    private String extractQuestionText(String line) {
        String s = line.trim();
        s = s.replaceFirst("(?i)^\\s*(qcm\\s*\\d+|question\\s*\\d+)\\s*[-–—:]\\s*", "");
        s = s.replaceFirst("(?i)^\\s*(qcm|question)\\s*[-–—:]\\s*", "");
        s = s.replaceFirst("(?i)^\\s*(qcm\\s*\\d+|question\\s*\\d+)\\s*\\.?\\s*", "");
        return s.trim();
    }

    private boolean isChoiceLine(String line) {
        String s = line.trim();
        if (s.length() < 2) return false;
        char first = s.charAt(0);
        if ("ABCDEabcde".indexOf(first) < 0) return false;
        return s.charAt(1) == ')' || s.charAt(1) == '.' || s.charAt(1) == ':';
    }

    private String extractChoiceText(String line) {
        String s = line.trim();
        if (s.length() >= 2 && (s.charAt(1) == ')' || s.charAt(1) == '.' || s.charAt(1) == ':')) {
            return s.substring(2).trim();
        }
        return s.substring(1).trim();
    }

    private boolean isAnswerLine(String low) {
        return low.startsWith("bonne r")
            || low.startsWith("réponse")
            || low.startsWith("reponse")
            || low.startsWith("rép:")
            || low.startsWith("rep:")
            || low.startsWith("correction")
            || low.startsWith("corr:");
    }

    // ── Résultats des étudiants ────────────────────────────────────────────

    @GetMapping("/{id}/resultats")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PassageResultDto>> resultats(@PathVariable Long id) {
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        Map<String, QcmPassage> submittedByEmail = passageRepo.findByQcmAndIsSubmittedTrue(qcm).stream()
            .collect(Collectors.toMap(p -> p.getStudent().getEmail().toLowerCase(), p -> p, (a, b) -> a));
        List<PassageResultDto> results = new ArrayList<>();
        java.util.Set<String> listedEmails = new java.util.HashSet<>();
        for (QcmStudent assigned : qcm.getAssignedStudents()) {
            String email = assigned.getStudentEmail().toLowerCase();
            listedEmails.add(email);
            QcmPassage passage = submittedByEmail.get(email);
            PassageResultDto dto = toPassageResult(assigned.getStudentName(), assigned.getStudentEmail(), passage, qcm);
            dto.studentLevel = assigned.getLevel();
            results.add(dto);
        }
        submittedByEmail.forEach((email, passage) -> {
            if (!listedEmails.contains(email)) {
                results.add(toPassageResult(passage.getStudent().getFirstName() + " " + passage.getStudent().getLastName(),
                    passage.getStudent().getEmail(), passage, qcm));
            }
        });
        return ResponseEntity.ok(results);
    }

    private PassageResultDto toPassageResult(String studentName, String studentEmail, QcmPassage p, Qcm qcm) {
        PassageResultDto dto = new PassageResultDto();
        dto.studentName = studentName;
        dto.studentEmail = studentEmail;
        dto.status = p == null ? "NON_COMMENCE" : (Boolean.TRUE.equals(p.getIsSubmitted()) ? "SOUMIS" : "EN_COURS");
        if (p == null) return dto;
        dto.passageId = p.getId();
        dto.studentId = p.getStudent().getId();
        dto.score = p.getScore();
        dto.maxScore = p.getMaxScore();
        dto.manualScore = p.getManualScore();
        dto.manualCorrectionNote = p.getManualCorrectionNote();
        dto.ocrScore = p.getOcrScore();
        dto.ocrCorrectionNote = p.getOcrCorrectionNote();
        dto.paperCorrectionUrl = p.getPaperCorrectionUrl();
        dto.paperCorrectionFilename = p.getPaperCorrectionFilename();
        if (dto.maxScore != null && dto.maxScore > 0 && dto.score != null) {
            dto.percentage = String.format("%.0f%%", (dto.score * 100.0) / dto.maxScore);
        } else {
            dto.percentage = "—";
        }
        dto.submittedAt = p.getSubmittedAt() != null ? p.getSubmittedAt().toString() : null;
        dto.documentAnswer = p.getDocumentAnswer();
        dto.correctionText = qcm.getCorrectionText();
        dto.reponses = p.getReponses().stream().map(r -> {
            ReponseDetailDto rd = new ReponseDetailDto();
            rd.questionId = r.getQuestion().getId();
            rd.questionText = r.getQuestion().getQuestionText();
            rd.points = r.getQuestion().getPoints();
            rd.questionType = r.getQuestion().getQuestionType();
            rd.textAnswer = r.getTextAnswer();
            rd.choiceSelected = resolveChoiceLabel(r, p);
            rd.isCorrect = r.getIsCorrect();
            rd.correctChoice = r.getQuestion().getChoices().stream()
                .filter(QcmChoice::getIsCorrect).findFirst()
                .map(QcmChoice::getChoiceText).orElse("—");
            return rd;
        }).collect(Collectors.toList());
        return dto;
    }

    private String resolveChoiceLabel(QcmReponse rep, QcmPassage passage) {
        if (rep.getChoiceSelected() != null) {
            return rep.getChoiceSelected().getChoiceText();
        }
        boolean hasText = rep.getTextAnswer() != null && !rep.getTextAnswer().isBlank();
        boolean hasPaper = passage != null && passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank();
        if (hasText && hasPaper) return "Réponse saisie + copie scannée / OCR";
        if (hasText) return "Réponse saisie";
        if ("CASE".equalsIgnoreCase(rep.getQuestion().getQuestionType())
            || ("PRACTICAL".equalsIgnoreCase(rep.getQuestion().getQuestionType())
                && rep.getQuestion().getCaseScenario() != null && !rep.getQuestion().getCaseScenario().isBlank())) {
            return passage != null && passage.getPaperCorrectionUrl() != null && !passage.getPaperCorrectionUrl().isBlank()
                ? "Copie scannée / OCR"
                : "Copie scannée attendue";
        }
        if ("PRACTICAL".equalsIgnoreCase(rep.getQuestion().getQuestionType())) {
            return "Réponse pratique saisie";
        }
        return "Sans réponse";
    }

    @PatchMapping("/{id}/passages/{passageId}/note")
    @Transactional
    public ResponseEntity<PassageResultDto> gradePassage(
            @PathVariable Long id,
            @PathVariable Long passageId,
            @RequestBody Map<String, Object> input) {
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        QcmPassage passage = passageRepo.findById(passageId).orElseThrow();
        if (!passage.getQcm().getId().equals(qcm.getId())) return ResponseEntity.notFound().build();
        if (!Boolean.TRUE.equals(passage.getIsSubmitted())) return ResponseEntity.badRequest().build();
        Object rawScore = input.get("score");
        if (rawScore == null) return ResponseEntity.badRequest().build();
        int score = Integer.parseInt(String.valueOf(rawScore));
        int maxScore = passage.getMaxScore() != null ? passage.getMaxScore() : 0;
        if (score < 0 || score > maxScore) return ResponseEntity.badRequest().build();
        passage.setManualScore(score);
        passage.setScore(score);
        passage.setManualCorrectionNote((String) input.getOrDefault("note", ""));
        passageRepo.save(passage);
        return ResponseEntity.ok(toPassageResult(
            passage.getStudent().getFirstName() + " " + passage.getStudent().getLastName(),
            passage.getStudent().getEmail(), passage, qcm));
    }

    // ── Export Excel des résultats (liste étudiants + notes à uploader) ───

    @GetMapping("/{id}/report")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) throws IOException {
        Qcm qcm = qcmRepo.findById(id).orElseThrow();
        List<QcmPassage> submitted = passageRepo.findByQcmAndIsSubmittedTrue(qcm);
        Map<String, QcmPassage> byEmail = submitted.stream()
            .collect(Collectors.toMap(p -> p.getStudent().getEmail().toLowerCase(), p -> p, (a, b) -> a));

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Résultats");
            CellStyle headerStyle = createReportHeaderStyle(wb);

            String[] headers = {"Nom", "Email", "Score", "Score max", "Pourcentage", "Mention", "Statut", "Date de soumission", "Copie papier", "Observation"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            java.util.Set<String> written = new java.util.HashSet<>();
            if (!qcm.getAssignedStudents().isEmpty()) {
                for (QcmStudent qs : qcm.getAssignedStudents()) {
                    rowIdx = writeReportRow(sheet, rowIdx, qs.getStudentName(), qs.getStudentEmail(),
                        byEmail.get(qs.getStudentEmail().toLowerCase()));
                    written.add(qs.getStudentEmail().toLowerCase());
                }
            }
            // Étudiants ayant soumis mais absents de la liste assignée (QCM ouvert à tous)
            for (QcmPassage p : submitted) {
                String email = p.getStudent().getEmail().toLowerCase();
                if (written.contains(email)) continue;
                rowIdx = writeReportRow(sheet, rowIdx,
                    p.getStudent().getFirstName() + " " + p.getStudent().getLastName(),
                    p.getStudent().getEmail(), p);
                written.add(email);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            byte[] bytes = out.toByteArray();
            String safeTitle = qcm.getTitle().replaceAll("[^a-zA-Z0-9]+", "_");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resultats_qcm_" + safeTitle + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(bytes);
        }
    }

    private CellStyle createReportHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) style)
            .setFillForegroundColor(new XSSFColor(new byte[]{99, 102, (byte) 241}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private int writeReportRow(Sheet sheet, int rowIdx, String name, String email, QcmPassage passage) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(email);
        if (passage != null) {
            int score = passage.getScore() != null ? passage.getScore() : 0;
            int maxScore = passage.getMaxScore() != null ? passage.getMaxScore() : 0;
            double pct = maxScore > 0 ? (score * 100.0) / maxScore : 0;
            row.createCell(2).setCellValue(score);
            row.createCell(3).setCellValue(maxScore);
            row.createCell(4).setCellValue(String.format("%.0f%%", pct));
            row.createCell(5).setCellValue(pct >= 80 ? "Excellent" : pct >= 60 ? "Bien" : pct >= 50 ? "Passable" : "Insuffisant");
            row.createCell(6).setCellValue("Soumis");
            row.createCell(7).setCellValue(passage.getSubmittedAt() != null ? passage.getSubmittedAt().toString() : "");
            row.createCell(8).setCellValue(passage.getPaperCorrectionUrl() != null ? passage.getPaperCorrectionUrl() : "");
            row.createCell(9).setCellValue(passage.getManualCorrectionNote() != null ? passage.getManualCorrectionNote() : "");
        } else {
            row.createCell(2); row.createCell(3); row.createCell(4); row.createCell(5);
            row.createCell(6).setCellValue("Non soumis");
            row.createCell(7); row.createCell(8); row.createCell(9);
        }
        return rowIdx + 1;
    }
}
