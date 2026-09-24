package com.elearning.exception;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Thrown at login when a student's account is temporarily blocked
 * (excluded from an exam/QCM for repeated anti-cheat violations).
 */
@Getter
public class AccountBlockedException extends RuntimeException {
    private final LocalDateTime blockedUntil;

    public AccountBlockedException(LocalDateTime blockedUntil) {
        super("Compte temporairement bloqué");
        this.blockedUntil = blockedUntil;
    }
}
