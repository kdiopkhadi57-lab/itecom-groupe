package com.elearning.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LookupController {

    private final RestTemplate restTemplate;

    /** ISBN → OpenLibrary */
    @GetMapping("/api/lookup/isbn/{isbn}")
    public ResponseEntity<String> lookupIsbn(@PathVariable String isbn) {
        String clean = isbn.replaceAll("[\\-\\s]", "");
        String url = "https://openlibrary.org/api/books?bibkeys=ISBN:" + clean
                     + "&format=json&jscmd=data";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "ElearningPlatform/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.getBody());
        } catch (Exception e) {
            log.error("ISBN lookup failed: {}", e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"OpenLibrary indisponible\"}");
        }
    }

    /** Zotero Web API proxy (évite CORS + masque la clé API côté serveur) */
    @GetMapping("/api/lookup/zotero/{userId}/items")
    public ResponseEntity<String> zoteroItems(
            @PathVariable String userId,
            @RequestHeader(value = "X-Zotero-Key", required = false) String apiKey) {
        String url = "https://api.zotero.org/users/" + userId + "/items?format=json&limit=100";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "ElearningPlatform/1.0");
            if (apiKey != null && !apiKey.isBlank()) headers.set("Zotero-API-Key", apiKey);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp.getBody());
        } catch (Exception e) {
            log.error("Zotero lookup failed: {}", e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Zotero indisponible\"}");
        }
    }

    /** Recherche de livres via OpenLibrary Search API */
    @GetMapping("/api/lookup/books/search")
    public ResponseEntity<String> searchBooks(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int maxResults,
            @RequestParam(defaultValue = "") String lang) {
        try {
            String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
            // OpenLibrary fields: title, author, cover, isbn, year, publisher, pages, language, subject
            String fields = "key,title,author_name,first_publish_year,publisher,isbn,number_of_pages_median,language,subject,cover_i,first_sentence,ia,has_fulltext,public_scan_b,lending_edition_s,lending_identifier_s";
            // OpenLibrary uses ISO 639-2 codes (fre, eng, ara) not ISO 639-1 (fr, en, ar)
            java.util.Map<String, String> langCodes = new java.util.HashMap<>();
            langCodes.put("fr", "fre"); langCodes.put("en", "eng"); langCodes.put("ar", "ara");
            String olLang = lang.isBlank() ? "" : langCodes.getOrDefault(lang, lang);
            String url = "https://openlibrary.org/search.json?q=" + encoded
                       + "&limit=" + Math.min(maxResults, 40)
                       + "&fields=" + fields
                       + (olLang.isBlank() ? "" : "&language=" + olLang);
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "ElearningPlatform/1.0 (mailto:admin@elearning.com)");
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp.getBody());
        } catch (Exception e) {
            log.error("OpenLibrary search failed: {}", e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"OpenLibrary indisponible\"}");
        }
    }

    /** DOI → CrossRef */
    @GetMapping("/api/lookup/doi")
    public ResponseEntity<String> lookupDoi(@RequestParam String doi) {
        String url = "https://api.crossref.org/works/" + doi;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "ElearningPlatform/1.0 (mailto:admin@elearning.com)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.getBody());
        } catch (Exception e) {
            log.error("DOI lookup failed: {}", e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"CrossRef indisponible\"}");
        }
    }
}
