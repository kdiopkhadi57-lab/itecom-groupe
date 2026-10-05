package com.elearning.controller;

import com.elearning.entity.Notification;
import com.elearning.entity.User;
import com.elearning.repository.NotificationRepository;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Notifications de l'utilisateur connecté (cloche de l'en-tête). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public record NotificationView(Long id, String title, String message, String link, String category, boolean read,
                                   LocalDateTime createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getTitle(), n.getMessage(), n.getLink(), n.getCategory(), n.isRead(), n.getCreatedAt());
        }
    }

    private User me(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));
    }

    @GetMapping
    public List<NotificationView> list(@AuthenticationPrincipal UserDetails principal) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(me(principal), PageRequest.of(0, 30)).stream()
            .map(NotificationView::of).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails principal) {
        return Map.of("count", notificationRepository.countByUserAndReadFalse(me(principal)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        User me = me(principal);
        notificationRepository.findById(id).filter(n -> n.getUser().getId().equals(me.getId())).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserDetails principal) {
        notificationRepository.markAllRead(me(principal));
        return ResponseEntity.noContent().build();
    }
}
