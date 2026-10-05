package com.elearning.service;

import com.elearning.entity.Notification;
import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.NotificationRepository;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

/** Notifications de la plateforme : cloche de l'en-tête et, si demandé, email. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public Notification notify(User user, String category, String title, String message, String link, boolean email) {
        Notification n = notificationRepository.save(Notification.builder()
            .user(user).category(category).title(truncate(title, 255)).message(truncate(message, 1000)).link(link)
            .build());
        if (email && user.getEmail() != null) {
            emailService.sendNotification(user.getEmail(), user.getFirstName(), title, message, link);
        }
        return n;
    }

    public int notifyAll(Collection<User> users, String category, String title, String message, String link, boolean email) {
        users.forEach(u -> notify(u, category, title, message, link, email));
        return users.size();
    }

    /** Prévient tous les administrateurs (ex. un paiement à vérifier). */
    public void notifyAdmins(String category, String title, String message, String link) {
        userRepository.findAll().stream()
            .filter(u -> u.getRole() == Role.ROLE_ADMIN)
            .forEach(u -> notify(u, category, title, message, link, false));
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
