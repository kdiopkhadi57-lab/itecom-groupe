package com.elearning.service;

import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Choix des étudiants d'un devoir, d'un examen ou d'une classe virtuelle : par niveau (L1…M2)
 * ou par liste d'emails. Dans les deux cas, seuls les comptes étudiants existants sont retenus :
 * le mot de passe est celui du compte, créé une seule fois.
 */
@Service
@RequiredArgsConstructor
public class StudentAudienceService {

    public static final List<String> LEVELS = List.of("L1", "L2", "L3", "M1", "M2");

    private final UserRepository userRepo;

    /** Niveaux reconnus, sans doublon, dans l'ordre du cursus, séparés par des virgules ; null si aucun. */
    public static String normalizeLevels(Collection<String> levels) {
        if (levels == null) return null;
        Set<String> chosen = levels.stream().filter(Objects::nonNull)
            .map(l -> l.trim().toUpperCase()).collect(Collectors.toSet());
        String joined = LEVELS.stream().filter(chosen::contains).collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    /** "L1,L3" → [L1, L3] (vide si aucun). */
    public static List<String> levelList(String targetLevels) {
        if (targetLevels == null || targetLevels.isBlank()) return List.of();
        return Arrays.stream(targetLevels.split(",")).map(String::trim).filter(l -> !l.isEmpty()).toList();
    }

    public static boolean inLevels(User user, String targetLevels) {
        return user.getRole() == Role.ROLE_STUDENT && user.getLevel() != null
            && levelList(targetLevels).stream().anyMatch(l -> l.equalsIgnoreCase(user.getLevel()));
    }

    /** Étudiants approuvés des niveaux ciblés. */
    public List<User> studentsOfLevels(String targetLevels) {
        List<String> levels = levelList(targetLevels);
        if (levels.isEmpty()) return List.of();
        return userRepo.findByRoleAndRegistrationStatusAndLevelIn(Role.ROLE_STUDENT, "APPROVED", levels);
    }

    /** Refuse les emails sans compte étudiant, en les citant tous. */
    public void requireStudentAccounts(Collection<String> emails) {
        List<String> missing = emails.stream()
            .map(e -> e.trim().toLowerCase())
            .filter(e -> userRepo.findByEmail(e).map(u -> u.getRole() != Role.ROLE_STUDENT).orElse(true))
            .distinct().toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Aucun compte étudiant pour : " + String.join(", ", missing)
                + ". Créez d'abord ces comptes depuis la gestion des utilisateurs, ou retirez-les de la liste.");
        }
    }

    /** Personnes conviées à une classe virtuelle (email → nom) : la liste, puis les étudiants des niveaux ciblés. */
    public java.util.LinkedHashMap<String, String> virtualClassAudience(com.elearning.entity.VirtualClass vc) {
        java.util.LinkedHashMap<String, String> audience = new java.util.LinkedHashMap<>();
        vc.getStudents().forEach(s -> audience.putIfAbsent(s.getStudentEmail().toLowerCase(), s.getStudentName()));
        studentsOfLevels(vc.getTargetLevels()).forEach(u -> audience.putIfAbsent(u.getEmail().toLowerCase(), fullName(u)));
        return audience;
    }

    public static boolean isInvited(com.elearning.entity.VirtualClass vc, User user) {
        return vc.getStudents().stream().anyMatch(s -> s.getStudentEmail().equalsIgnoreCase(user.getEmail()))
            || inLevels(user, vc.getTargetLevels());
    }

    public static String fullName(User u) {
        return ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
    }
}
