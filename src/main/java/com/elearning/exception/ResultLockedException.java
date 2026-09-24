package com.elearning.exception;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Thrown when a student excluded from an exam/QCM for repeated anti-cheat
 * violations tries to view their result before the exam/QCM's estimated
 * duration has elapsed.
 */
@Getter
public class ResultLockedException extends RuntimeException {
    private final LocalDateTime availableAt;

    public ResultLockedException(LocalDateTime availableAt) {
        super("Résultat non disponible");
        this.availableAt = availableAt;
    }
}
