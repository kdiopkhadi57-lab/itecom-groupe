package com.elearning.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class JitsiTokenService {

    @Value("${app.jaas.app-id:}")
    private String appId;

    @Value("${app.jaas.key-id:}")
    private String keyId;

    @Value("${app.jaas.private-key:}")
    private String privateKeyPem;

    @Value("${app.jaas.domain:8x8.vc}")
    private String domain;

    public String getDomain() {
        return domain;
    }

    public String getRoomName(String roomName) {
        return appId + "/" + roomName;
    }

    public String createToken(String roomName, Long userId, String name, String email, boolean moderator) {
        if (appId.isBlank() || keyId.isBlank() || privateKeyPem.isBlank()) {
            throw new IllegalStateException("JaaS JWT non configuré: app-id, key-id et private-key sont requis");
        }

        Instant now = Instant.now();
        Map<String, Object> user = new java.util.HashMap<>();
        user.put("id", String.valueOf(userId));
        user.put("name", name);
        user.put("email", email);
        user.put("moderator", moderator);

        Map<String, Object> context = Map.of("user", user);
        return Jwts.builder()
            .setHeaderParam("kid", normalizedKeyId())
            .setAudience("jitsi")
            .setIssuer("chat")
            .setSubject(appId)
            .claim("room", "*")
            .claim("context", context)
            .setNotBefore(Date.from(now.minusSeconds(5)))
            .setExpiration(Date.from(now.plusSeconds(3600)))
            .signWith(readPrivateKey(), SignatureAlgorithm.RS256)
            .compact();
    }

    private PrivateKey readPrivateKey() {
        try {
            String keyValue = privateKeyPem;
            Path keyPath = Path.of(privateKeyPem);
            if (Files.isRegularFile(keyPath)) {
                keyValue = Files.readString(keyPath);
            }
            String normalized = keyValue
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception exception) {
            throw new IllegalStateException("La clé privée JaaS doit être une clé RSA PKCS#8 PEM valide", exception);
        }
    }

    private String normalizedKeyId() {
        return keyId.startsWith(appId + "/") ? keyId : appId + "/" + keyId;
    }
}
