package com.elearning.repository;

import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByResetPasswordToken(String token);
    List<User> findByRegistrationStatusOrderByCreatedAtDesc(String registrationStatus);
    long countByRegistrationStatus(String registrationStatus);
    List<User> findByRoleAndRegistrationStatusOrderByCreatedAtDesc(com.elearning.entity.Role role, String registrationStatus);
    long countByRoleAndRegistrationStatus(com.elearning.entity.Role role, String registrationStatus);
}
