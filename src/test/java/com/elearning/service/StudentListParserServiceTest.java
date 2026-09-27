package com.elearning.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StudentListParserServiceTest {

    private final StudentListParserService parser = new StudentListParserService();

    private List<StudentListParserService.StudentInfo> parseCsv(String content) throws Exception {
        return parser.parseFile(new MockMultipartFile("file", "etudiants.csv", "text/csv",
            content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsNamedColumnsInAnyOrder() throws Exception {
        List<StudentListParserService.StudentInfo> students = parseCsv("""
            Email;Mot de passe;Prénom;Nom;Niveau
            awa.diop@example.com;Ad9x;Awa;Diop;L3
            """);
        assertEquals(1, students.size());
        var s = students.get(0);
        assertEquals("Awa Diop", s.name());
        assertEquals("awa.diop@example.com", s.email());
        assertEquals("Awa", s.firstName());
        assertEquals("Diop", s.lastName());
        assertEquals("L3", s.level());
        assertEquals("Ad9x", s.password());
    }

    @Test
    void singleNomColumnIsTreatedAsFullName() throws Exception {
        List<StudentListParserService.StudentInfo> students = parseCsv("""
            Nom,Email,Classe
            Moussa Thioune,moussa@example.com,L3
            """);
        var s = students.get(0);
        assertEquals("Moussa Thioune", s.name());
        assertEquals("Moussa", s.firstName());
        assertEquals("Thioune", s.lastName());
        assertEquals("L3", s.level());
        assertNull(s.password());
    }

    @Test
    void legacyFormatWithoutHeaderStillWorks() throws Exception {
        List<StudentListParserService.StudentInfo> students = parseCsv("Fatou Diallo,fatou@example.com\n");
        assertEquals(1, students.size());
        assertEquals("Fatou Diallo", students.get(0).name());
    }
}
