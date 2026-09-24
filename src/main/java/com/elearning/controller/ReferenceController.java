package com.elearning.controller;

import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.ReferenceResponse;
import com.elearning.entity.Reference;
import com.elearning.entity.User;
import com.elearning.repository.ReferenceRepository;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceRepository referenceRepository;
    private final UserRepository userRepository;

    @GetMapping("/api/references")
    public ResponseEntity<List<ReferenceResponse>> getReferences(
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<Reference> refs;

        if (search != null && !search.isBlank()) {
            refs = referenceRepository.search(user, search);
        } else if (collection != null && !collection.isBlank()) {
            refs = referenceRepository.findByUserAndCollectionOrderByCreatedAtDesc(user, collection);
        } else {
            refs = referenceRepository.findByUserOrderByCreatedAtDesc(user);
        }

        return ResponseEntity.ok(refs.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/api/references/collections")
    public ResponseEntity<List<String>> getCollections(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(referenceRepository.findCollectionsByUser(user));
    }

    @GetMapping("/api/references/{id}")
    public ResponseEntity<ReferenceResponse> getReference(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Reference ref = referenceRepository.findById(id).orElseThrow();
        if (!ref.getUser().getId().equals(user.getId())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(toResponse(ref));
    }

    @PostMapping("/api/references")
    public ResponseEntity<ReferenceResponse> createReference(
            @RequestBody Reference data,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        data.setUser(user);
        return ResponseEntity.ok(toResponse(referenceRepository.save(data)));
    }

    @PutMapping("/api/references/{id}")
    public ResponseEntity<ReferenceResponse> updateReference(
            @PathVariable Long id,
            @RequestBody Reference data,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Reference ref = referenceRepository.findById(id).orElseThrow();
        if (!ref.getUser().getId().equals(user.getId())) return ResponseEntity.status(403).build();

        ref.setRefType(data.getRefType()); ref.setTitle(data.getTitle());
        ref.setAuthors(data.getAuthors()); ref.setYear(data.getYear());
        ref.setPublisher(data.getPublisher()); ref.setPlace(data.getPlace());
        ref.setIsbn(data.getIsbn()); ref.setEdition(data.getEdition());
        ref.setPages(data.getPages()); ref.setJournal(data.getJournal());
        ref.setVolume(data.getVolume()); ref.setIssue(data.getIssue());
        ref.setDoi(data.getDoi()); ref.setStartPage(data.getStartPage());
        ref.setEndPage(data.getEndPage()); ref.setUrl(data.getUrl());
        ref.setAccessDate(data.getAccessDate()); ref.setUniversity(data.getUniversity());
        ref.setThesisType(data.getThesisType()); ref.setCollection(data.getCollection());
        ref.setTags(data.getTags()); ref.setAbstract_(data.getAbstract_());
        ref.setNote(data.getNote());

        return ResponseEntity.ok(toResponse(referenceRepository.save(ref)));
    }

    @DeleteMapping("/api/references/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReference(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Reference ref = referenceRepository.findById(id).orElseThrow();
        if (!ref.getUser().getId().equals(user.getId())) return ResponseEntity.status(403).build();
        referenceRepository.delete(ref);
        return ResponseEntity.ok(ApiResponse.success("Référence supprimée", null));
    }

    private ReferenceResponse toResponse(Reference r) {
        return ReferenceResponse.builder()
                .id(r.getId()).refType(r.getRefType()).title(r.getTitle())
                .authors(r.getAuthors()).year(r.getYear()).publisher(r.getPublisher())
                .place(r.getPlace()).isbn(r.getIsbn()).edition(r.getEdition())
                .pages(r.getPages()).journal(r.getJournal()).volume(r.getVolume())
                .issue(r.getIssue()).doi(r.getDoi()).startPage(r.getStartPage())
                .endPage(r.getEndPage()).url(r.getUrl()).accessDate(r.getAccessDate())
                .university(r.getUniversity()).thesisType(r.getThesisType())
                .collection(r.getCollection()).tags(r.getTags())
                .abstract_(r.getAbstract_()).note(r.getNote())
                .createdAt(r.getCreatedAt()).build();
    }
}
