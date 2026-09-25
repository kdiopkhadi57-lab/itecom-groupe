package com.elearning.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private volatile PrivateKey cachedPrivateKey;

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
        if (cachedPrivateKey == null) {
            cachedPrivateKey = parsePrivateKey(resolveKeyContent(privateKeyPem));
        }
        return cachedPrivateKey;
    }

    /**
     * La propriété peut contenir soit le chemin d'un fichier .pk/.pem, soit le contenu PEM
     * lui-même (éventuellement avec des "\n" échappés, entre guillemets, ou encodé en base64).
     */
    private String resolveKeyContent(String configured) {
        String value = configured.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.contains("-----BEGIN")) {
            return value;
        }
        String pathValue = value.startsWith("file:") ? value.substring("file:".length()) : value;
        if (pathValue.startsWith("~/")) {
            pathValue = System.getProperty("user.home") + pathValue.substring(1);
        }
        if (looksLikePath(pathValue)) {
            Path keyPath = Path.of(pathValue);
            if (!Files.isRegularFile(keyPath)) {
                throw new IllegalStateException("Fichier de clé privée JaaS introuvable sur le serveur : " + keyPath
                    + ". Définissez la variable JAAS_PRIVATE_KEY avec le contenu PEM de la clé ou un chemin valide.");
            }
            try {
                return Files.readString(keyPath);
            } catch (IOException exception) {
                throw new IllegalStateException("Impossible de lire la clé privée JaaS : " + keyPath, exception);
            }
        }
        return value;
    }

    private boolean looksLikePath(String value) {
        return value.startsWith("/") || value.startsWith("./") || value.matches("^[A-Za-z]:[\\\\/].*")
            || value.endsWith(".pk") || value.endsWith(".pem") || value.endsWith(".key");
    }

    private PrivateKey parsePrivateKey(String keyValue) {
        try {
            String pem = keyValue.replace("\\r", "").replace("\\n", "\n");
            if (!pem.contains("-----BEGIN")) {
                // Clé PEM entière encodée en base64 (pratique pour les variables d'environnement)
                String decoded = decodeBase64Text(pem);
                if (decoded != null && decoded.contains("-----BEGIN")) {
                    pem = decoded;
                }
            }
            boolean pkcs1 = pem.contains("-----BEGIN RSA PRIVATE KEY-----");
            String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]*PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            if (pkcs1) {
                der = wrapPkcs1InPkcs8(der);
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("La clé privée JaaS doit être une clé RSA PKCS#8 PEM valide", exception);
        }
    }

    private String decodeBase64Text(String value) {
        try {
            return new String(Base64.getDecoder().decode(value.replaceAll("\\s", "")), StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Convertit une clé "BEGIN RSA PRIVATE KEY" (PKCS#1) en structure PKCS#8. */
    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1) throws IOException {
        byte[] algorithmId = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.write(new byte[]{0x02, 0x01, 0x00});
        content.write(algorithmId);
        content.write(derElement((byte) 0x04, pkcs1));
        return derElement((byte) 0x30, content.toByteArray());
    }

    private byte[] derElement(byte tag, byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else if (length <= 0xff) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xffff) {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length);
        } else {
            out.write(0x83);
            out.write(length >> 16);
            out.write(length >> 8);
            out.write(length);
        }
        out.write(value);
        return out.toByteArray();
    }

    private String normalizedKeyId() {
        return keyId.startsWith(appId + "/") ? keyId : appId + "/" + keyId;
    }
}
