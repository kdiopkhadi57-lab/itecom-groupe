package com.elearning.security;

import com.elearning.security.jwt.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwt() {
        JwtUtils j = new JwtUtils();
        ReflectionTestUtils.setField(j, "jwtSecret", "elearningSecretKey2024VeryLongSecretKeyForHMACSHA256Algorithm");
        ReflectionTestUtils.setField(j, "jwtExpirationMs", 60_000);
        ReflectionTestUtils.setField(j, "refreshExpirationMs", 600_000);
        return j;
    }

    @Test
    void refreshTokenRenewsTheSessionButIsNotAnAccessToken() {
        JwtUtils j = jwt();
        String access = j.generateAccessToken("awa@test.com");
        String refresh = j.generateRefreshToken("awa@test.com");

        assertEquals("awa@test.com", j.getEmailFromRefreshToken(refresh));
        assertNull(j.getEmailFromRefreshToken(access), "un jeton d'accès ne renouvelle pas la session");
        assertNull(j.getEmailFromRefreshToken("faux.jeton.x"));
        assertTrue(j.isRefreshToken(refresh));
        assertFalse(j.isRefreshToken(access), "le jeton d'accès reste accepté par le filtre");
    }
}
