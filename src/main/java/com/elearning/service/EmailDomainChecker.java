package com.elearning.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vérifie qu'une adresse email est valide et que son domaine existe et reçoit des emails (DNS MX, sinon A).
 * Si le DNS ne répond pas, on ne bloque pas la saisie : seul un domaine certainement inexistant est refusé.
 */
@Service
@Slf4j
public class EmailDomainChecker {

    @Value("${app.email.domain-check:true}")
    private boolean enabled = true;

    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    /** Email normalisé, ou IllegalArgumentException si le format est faux ou le domaine n'existe pas. */
    public String check(String value) {
        String email = ContactValidator.email(value);
        String domain = email.substring(email.indexOf('@') + 1);
        if (enabled && Boolean.FALSE.equals(lookup(domain))) {
            throw new IllegalArgumentException("Le domaine « " + domain + " » n'existe pas : vérifiez l'adresse email.");
        }
        return email;
    }

    /** Réponse DNS mise en cache ; une erreur réseau (null) n'est pas mise en cache. */
    private Boolean lookup(String domain) {
        Boolean known = cache.get(domain);
        if (known != null) return known;
        Boolean result = receivesMail(domain);
        if (result != null) cache.put(domain, result);
        return result;
    }

    /** true / false selon le DNS ; null si le DNS est injoignable (on ne refuse pas sans certitude). */
    Boolean receivesMail(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "1500");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                // Un type à la fois : une requête groupée échoue dès qu'un des types n'a pas d'enregistrement
                for (String type : new String[]{"MX", "A", "AAAA"}) {
                    try {
                        Attributes attrs = ctx.getAttributes(domain.toLowerCase(Locale.ROOT), new String[]{type});
                        if (hasValue(attrs.get(type))) return true;
                    } catch (NameNotFoundException e) {
                        // pas d'enregistrement de ce type (ou domaine inexistant) : type suivant
                    }
                }
                return false;
            } finally {
                ctx.close();
            }
        } catch (NamingException | RuntimeException e) {
            log.debug("Vérification DNS impossible pour {} : {}", domain, e.getMessage());
            return null;
        }
    }

    private static boolean hasValue(Attribute a) {
        return a != null && a.size() > 0;
    }
}
