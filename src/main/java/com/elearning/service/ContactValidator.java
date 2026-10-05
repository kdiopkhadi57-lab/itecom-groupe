package com.elearning.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Contrôle des emails et numéros de téléphone saisis sur la plateforme : on refuse les valeurs
 * fantaisistes (format, faute de frappe sur un domaine courant, numéro inexistant au Sénégal).
 * Les messages d'erreur sont destinés à l'utilisateur.
 */
public final class ContactValidator {

    private ContactValidator() {}

    private static final Pattern LOCAL = Pattern.compile("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern TLD = Pattern.compile("[a-z]{2,24}");

    /** Fautes de frappe fréquentes sur les messageries courantes → domaine voulu. */
    private static final Map<String, String> DOMAIN_TYPOS = Map.ofEntries(
        Map.entry("gmal.com", "gmail.com"), Map.entry("gmial.com", "gmail.com"), Map.entry("gmai.com", "gmail.com"),
        Map.entry("gamil.com", "gmail.com"), Map.entry("gmail.co", "gmail.com"), Map.entry("gmail.con", "gmail.com"),
        Map.entry("gmail.cm", "gmail.com"), Map.entry("gmail.om", "gmail.com"), Map.entry("gmail.fr", "gmail.com"),
        Map.entry("gmail.sn", "gmail.com"), Map.entry("gmaill.com", "gmail.com"), Map.entry("gmail.cmo", "gmail.com"),
        Map.entry("hotmial.com", "hotmail.com"), Map.entry("hotmal.com", "hotmail.com"), Map.entry("hotmail.con", "hotmail.com"),
        Map.entry("hotmail.co", "hotmail.com"), Map.entry("yaho.fr", "yahoo.fr"), Map.entry("yahou.fr", "yahoo.fr"),
        Map.entry("yahoo.con", "yahoo.com"), Map.entry("yahoo.co", "yahoo.com"), Map.entry("outlok.com", "outlook.com"),
        Map.entry("outlook.con", "outlook.com"), Map.entry("iclod.com", "icloud.com"), Map.entry("icloud.con", "icloud.com"));

    /** Email obligatoire, normalisé en minuscules ; IllegalArgumentException avec un message clair sinon. */
    public static String email(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) throw new IllegalArgumentException("Indiquez l'adresse email.");
        int at = email.lastIndexOf('@');
        if (email.length() > 254 || at <= 0 || at != email.indexOf('@')) {
            throw new IllegalArgumentException("Adresse email invalide : « " + email + " » (exemple : prenom.nom@gmail.com).");
        }
        String local = email.substring(0, at), domain = email.substring(at + 1);
        String[] labels = domain.split("\\.", -1);
        boolean ok = local.length() <= 64 && LOCAL.matcher(local).matches() && labels.length >= 2;
        for (int i = 0; ok && i < labels.length; i++) {
            ok = i == labels.length - 1 ? TLD.matcher(labels[i]).matches() : LABEL.matcher(labels[i]).matches();
        }
        if (!ok) throw new IllegalArgumentException("Adresse email invalide : « " + email + " » (exemple : prenom.nom@gmail.com).");
        String fix = DOMAIN_TYPOS.get(domain);
        if (fix != null) {
            throw new IllegalArgumentException("Adresse email incorrecte : vouliez-vous dire " + local + "@" + fix + " ?");
        }
        return email;
    }

    /** Opérateur mobile attendu pour un paiement : Orange Money (77, 78), Free Money (76), Wave (tout mobile). */
    public enum Usage { ANY, MOBILE, ORANGE_MONEY, FREE_MONEY }

    /**
     * Numéro normalisé au format « +221 77 123 45 67 » (ou international « +33612345678 » si l'usage le permet).
     * Accepte espaces, points, tirets, parenthèses et les préfixes +221 / 00221.
     */
    public static String phone(String value, Usage usage) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("Indiquez le numéro de téléphone.");
        String compact = raw.replaceAll("[\\s.()\\-]", "");
        if (compact.startsWith("00")) compact = "+" + compact.substring(2);
        if (!compact.matches("\\+?\\d+")) {
            throw new IllegalArgumentException("Numéro de téléphone invalide : seuls les chiffres sont acceptés (exemple : 77 123 45 67).");
        }
        String national;
        if (compact.startsWith("+221")) national = compact.substring(4);
        else if (compact.startsWith("221") && compact.length() == 12) national = compact.substring(3);
        else if (compact.startsWith("+")) {
            if (usage != Usage.ANY) {
                throw new IllegalArgumentException("Le paiement mobile exige un numéro sénégalais (exemple : 77 123 45 67).");
            }
            if (!compact.matches("\\+[1-9]\\d{7,14}")) {
                throw new IllegalArgumentException("Numéro international invalide (exemple : +33 6 12 34 56 78).");
            }
            return compact;
        } else national = compact;

        if (national.length() != 9) {
            throw new IllegalArgumentException("Un numéro sénégalais a 9 chiffres (exemple : 77 123 45 67).");
        }
        String prefix = national.substring(0, 2);
        boolean mobile = prefix.matches("7[05678]");
        boolean landline = prefix.matches("3[03]");
        if (!mobile && !landline) {
            throw new IllegalArgumentException("Numéro sénégalais invalide : il doit commencer par 70, 75, 76, 77, 78 (mobile) ou 33 (fixe).");
        }
        if (usage != Usage.ANY && !mobile) {
            throw new IllegalArgumentException("Le paiement mobile exige un numéro de portable (70, 75, 76, 77 ou 78).");
        }
        if (usage == Usage.ORANGE_MONEY && !prefix.matches("7[78]")) {
            throw new IllegalArgumentException("Un numéro Orange Money commence par 77 ou 78.");
        }
        if (usage == Usage.FREE_MONEY && !prefix.equals("76")) {
            throw new IllegalArgumentException("Un numéro Free Money commence par 76.");
        }
        String subscriber = national.substring(2);
        if (subscriber.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("Ce numéro de téléphone n'est pas un vrai numéro.");
        }
        return "+221 " + prefix + " " + national.substring(2, 5) + " " + national.substring(5, 7) + " " + national.substring(7);
    }

    /** Usage attendu selon le moyen de paiement mobile. */
    public static Usage usageFor(String paymentMethod) {
        return switch (paymentMethod == null ? "" : paymentMethod) {
            case "ORANGE_MONEY" -> Usage.ORANGE_MONEY;
            case "FREE_MONEY" -> Usage.FREE_MONEY;
            default -> Usage.MOBILE;
        };
    }
}
