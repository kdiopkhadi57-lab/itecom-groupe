package com.elearning.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private int jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration}")
    private int refreshExpirationMs;

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())));
    }

    public String generateAccessToken(String email) {
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
            .signWith(key(), SignatureAlgorithm.HS256)
            .compact();
    }

    /** Jeton de rafraîchissement : marqué « refresh », il ne sert qu'à obtenir un nouveau jeton d'accès. */
    public String generateRefreshToken(String email) {
        return Jwts.builder()
            .setSubject(email)
            .claim("typ", "refresh")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
            .signWith(key(), SignatureAlgorithm.HS256)
            .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
            .parseClaimsJws(token).getBody().getSubject();
    }

    /** Email du jeton s'il est valide et marqué « refresh », sinon null. */
    public String getEmailFromRefreshToken(String token) {
        try {
            var claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
            return "refresh".equals(claims.get("typ")) ? claims.getSubject() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Un jeton de rafraîchissement ne doit pas être accepté comme jeton d'accès. */
    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody().get("typ"));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }
}
