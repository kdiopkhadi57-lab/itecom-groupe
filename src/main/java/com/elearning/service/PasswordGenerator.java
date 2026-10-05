package com.elearning.service;

import java.security.SecureRandom;

/** Mot de passe initial d'un compte créé par l'administration. */
public final class PasswordGenerator {

    // Sans caractères ambigus (0/O, 1/l/I) pour faciliter la saisie
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
