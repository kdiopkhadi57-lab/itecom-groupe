package com.elearning.controller;

import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<User> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userRepository.findByEmail(userDetails.getUsername()).orElseThrow());
    }

    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(
            @RequestBody Map<String, String> data,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        if (data.containsKey("firstName")) user.setFirstName(data.get("firstName"));
        if (data.containsKey("lastName")) user.setLastName(data.get("lastName"));
        if (data.containsKey("bio")) user.setBio(data.get("bio"));
        if (data.containsKey("phone")) user.setPhone(data.get("phone"));
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> data,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        if (!passwordEncoder.matches(data.get("currentPassword"), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mot de passe actuel incorrect"));
        }
        user.setPassword(passwordEncoder.encode(data.get("newPassword")));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }

    @GetMapping("/admin/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }
}
