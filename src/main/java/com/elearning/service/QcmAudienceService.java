package com.elearning.service;

import com.elearning.entity.Qcm;
import com.elearning.entity.QcmStudent;
import com.elearning.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Étudiants concernés par un devoir : ceux de la liste du professeur (import ou ajout manuel)
 * et tous les étudiants approuvés des niveaux ciblés, y compris ceux créés après le devoir.
 */
@Service
@RequiredArgsConstructor
public class QcmAudienceService {

    private final StudentAudienceService students;

    /** Liste du professeur suivie des étudiants des niveaux ciblés qui n'y figurent pas (entrées non enregistrées). */
    public List<QcmStudent> audience(Qcm qcm) {
        List<QcmStudent> result = new ArrayList<>(qcm.getAssignedStudents());
        if (qcm.targetLevelList().isEmpty()) return result;
        Set<String> listed = new HashSet<>();
        qcm.getAssignedStudents().forEach(s -> listed.add(s.getStudentEmail().toLowerCase()));
        for (User u : students.studentsOfLevels(qcm.getTargetLevels())) {
            if (listed.add(u.getEmail().toLowerCase())) result.add(fromAccount(u));
        }
        return result;
    }

    /** L'inscription de l'étudiant à ce devoir, par la liste ou par son niveau. */
    public Optional<QcmStudent> findAssignment(Qcm qcm, User student) {
        Optional<QcmStudent> listed = qcm.getAssignedStudents().stream()
            .filter(s -> s.getStudentEmail().equalsIgnoreCase(student.getEmail()))
            .findFirst();
        if (listed.isPresent()) return listed;
        if (StudentAudienceService.inLevels(student, qcm.getTargetLevels())) {
            return Optional.of(fromAccount(student));
        }
        return Optional.empty();
    }

    public boolean canAccess(Qcm qcm, User student) {
        return qcm.isOpenToAll() || findAssignment(qcm, student).isPresent();
    }

    private static QcmStudent fromAccount(User u) {
        return QcmStudent.builder()
            .studentName(StudentAudienceService.fullName(u))
            .studentEmail(u.getEmail())
            .firstName(u.getFirstName()).lastName(u.getLastName())
            .level(u.getLevel())
            .build();
    }
}
