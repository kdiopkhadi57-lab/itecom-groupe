package com.elearning.controller;

import com.elearning.dto.response.AnnotationResponse;
import com.elearning.dto.response.ApiResponse;
import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class BookAnnotationController {

    private final BookAnnotationRepository annotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @GetMapping("/api/books/{bookId}/annotations")
    public ResponseEntity<List<AnnotationResponse>> getAnnotations(
            @PathVariable Long bookId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Book book = bookRepository.findById(bookId).orElseThrow();
        List<BookAnnotation> annotations =
                annotationRepository.findByUserAndBookOrderByPageNumberAscCreatedAtDesc(user, book);
        return ResponseEntity.ok(annotations.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @PostMapping("/api/books/{bookId}/annotations")
    public ResponseEntity<AnnotationResponse> addAnnotation(
            @PathVariable Long bookId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Book book = bookRepository.findById(bookId).orElseThrow();

        BookAnnotation ann = BookAnnotation.builder()
                .user(user)
                .book(book)
                .pageNumber(body.get("pageNumber") != null ? (Integer) body.get("pageNumber") : null)
                .selectedText(body.get("selectedText") != null ? body.get("selectedText").toString() : null)
                .color(body.get("color") != null ? body.get("color").toString() : "yellow")
                .note(body.get("note") != null ? body.get("note").toString() : null)
                .type(body.get("type") != null
                        ? BookAnnotation.AnnotationType.valueOf(body.get("type").toString())
                        : BookAnnotation.AnnotationType.HIGHLIGHT)
                .build();

        return ResponseEntity.ok(toResponse(annotationRepository.save(ann)));
    }

    @PutMapping("/api/books/{bookId}/annotations/{id}")
    public ResponseEntity<AnnotationResponse> updateAnnotation(
            @PathVariable Long bookId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        BookAnnotation ann = annotationRepository.findById(id).orElseThrow();
        if (!ann.getUser().getId().equals(user.getId())) return ResponseEntity.status(403).build();

        if (body.containsKey("color")) ann.setColor(body.get("color").toString());
        if (body.containsKey("note"))  ann.setNote(body.get("note").toString());

        return ResponseEntity.ok(toResponse(annotationRepository.save(ann)));
    }

    @DeleteMapping("/api/books/{bookId}/annotations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAnnotation(
            @PathVariable Long bookId,
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        BookAnnotation ann = annotationRepository.findById(id).orElseThrow();
        if (!ann.getUser().getId().equals(user.getId())) return ResponseEntity.status(403).build();
        annotationRepository.delete(ann);
        return ResponseEntity.ok(ApiResponse.success("Annotation supprimée", null));
    }

    private AnnotationResponse toResponse(BookAnnotation a) {
        return AnnotationResponse.builder()
                .id(a.getId())
                .bookId(a.getBook().getId())
                .pageNumber(a.getPageNumber())
                .selectedText(a.getSelectedText())
                .color(a.getColor())
                .note(a.getNote())
                .type(a.getType())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
