package com.elearning.controller;

import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.BookResponse;
import com.elearning.entity.Book;
import com.elearning.repository.BookRepository;
import com.elearning.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class BookController {

    private final BookRepository bookRepository;
    private final FileStorageService fileStorageService;

    // ─── Public / Étudiant ────────────────────────────────────────────────────

    @GetMapping("/api/books")
    public ResponseEntity<List<BookResponse>> getBooks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String search) {

        List<Book> books = bookRepository.findWithFilters(
                category != null && !category.isBlank() ? category : null,
                language != null && !language.isBlank() ? language : null,
                search != null && !search.isBlank() ? search : null);

        return ResponseEntity.ok(books.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/api/books/{id}")
    public ResponseEntity<BookResponse> getBook(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(toResponse(book));
    }

    // ─── Professeur / Admin ───────────────────────────────────────────────────

    @PostMapping("/api/teacher/books")
    public ResponseEntity<BookResponse> createBook(@RequestBody Book book) {
        return ResponseEntity.ok(toResponse(bookRepository.save(book)));
    }

    @PutMapping("/api/teacher/books/{id}")
    public ResponseEntity<BookResponse> updateBook(@PathVariable Long id, @RequestBody Book data) {
        Book book = bookRepository.findById(id).orElseThrow();
        book.setTitle(data.getTitle());
        book.setAuthor(data.getAuthor());
        book.setDescription(data.getDescription());
        book.setCategory(data.getCategory());
        book.setGenre(data.getGenre());
        book.setLanguage(data.getLanguage());
        book.setYear(data.getYear());
        book.setPages(data.getPages());
        book.setPublisher(data.getPublisher());
        book.setIsbn(data.getIsbn());
        if (data.getExternalReadUrl() != null) book.setExternalReadUrl(data.getExternalReadUrl());
        book.setAvailable(data.isAvailable());
        return ResponseEntity.ok(toResponse(bookRepository.save(book)));
    }

    @PostMapping("/api/teacher/books/{id}/upload-pdf")
    public ResponseEntity<ApiResponse<String>> uploadPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        Book book = bookRepository.findById(id).orElseThrow();
        String url = fileStorageService.store(file, "books/pdf");
        book.setFileUrl(url);
        bookRepository.save(book);
        return ResponseEntity.ok(ApiResponse.success("PDF téléversé avec succès", url));
    }

    @PostMapping("/api/teacher/books/{id}/upload-cover")
    public ResponseEntity<ApiResponse<String>> uploadCover(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        Book book = bookRepository.findById(id).orElseThrow();
        String url = fileStorageService.store(file, "books/covers");
        book.setCoverUrl(url);
        bookRepository.save(book);
        return ResponseEntity.ok(ApiResponse.success("Couverture téléversée avec succès", url));
    }

    @DeleteMapping("/api/teacher/books/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        bookRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Livre supprimé", null));
    }

    @GetMapping("/api/books/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(bookRepository.findAll().stream()
                .map(Book::getCategory).filter(c -> c != null && !c.isBlank())
                .distinct().sorted().collect(Collectors.toList()));
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private BookResponse toResponse(Book b) {
        return BookResponse.builder()
                .id(b.getId())
                .title(b.getTitle())
                .author(b.getAuthor())
                .description(b.getDescription())
                .coverUrl(b.getCoverUrl())
                .fileUrl(b.getFileUrl())
                .category(b.getCategory())
                .genre(b.getGenre())
                .language(b.getLanguage())
                .year(b.getYear())
                .pages(b.getPages())
                .publisher(b.getPublisher())
                .isbn(b.getIsbn())
                .externalReadUrl(b.getExternalReadUrl())
                .available(b.isAvailable())
                .hasFile(b.getFileUrl() != null && !b.getFileUrl().isBlank())
                .hasExternalRead(b.getExternalReadUrl() != null && !b.getExternalReadUrl().isBlank())
                .addedAt(b.getAddedAt())
                .build();
    }
}
