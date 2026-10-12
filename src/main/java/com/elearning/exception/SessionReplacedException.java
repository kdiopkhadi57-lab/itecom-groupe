package com.elearning.exception;

/** Le compte a été ouvert ailleurs : la session de cet appareil a été remplacée (une seule session par compte). */
public class SessionReplacedException extends RuntimeException {
    public static final String MESSAGE = "Votre compte a été ouvert sur un autre appareil : vous avez été déconnecté.";

    public SessionReplacedException() {
        super(MESSAGE);
    }
}
