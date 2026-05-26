package com.medicology.dictionary.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JWTDecoderTest {

    private JWTDecoder jwtDecoder;

    private final String expectedIssuer = "medicology-auth";

    private final String expectedAudience = "medicology-api";

    private final String secret = "9a4f2c3d5e6f7g8h9i0j1k2l3m4n5n2k7q8r9s0t1u2v3w4x5y6z7a8b9c0d1e2f";

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtDecoder = new JWTDecoder();
        ReflectionTestUtils.setField(jwtDecoder, "secretKey", secret);
        ReflectionTestUtils.setField(jwtDecoder, "expectedIssuer", expectedIssuer);
        ReflectionTestUtils.setField(jwtDecoder, "expectedAudience", expectedAudience);
        jwtDecoder.init();
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testValidStandardToken() {
        String userId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject("test@example.com")
                .claim("id", userId)
                .claim("role", "USER")
                .claim("type", "access")
                .issuer(expectedIssuer)
                .audience().add(expectedAudience).and()
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();

        assertTrue(jwtDecoder.isTokenValid(token, "access"));
        assertEquals("test@example.com", jwtDecoder.extractEmail(token));
        assertEquals(userId, jwtDecoder.extractId(token));
        assertEquals("USER", jwtDecoder.extractRole(token));
        assertFalse(jwtDecoder.extractIsAdmin(token));
    }

    @Test
    void testValidSupabaseToken() {
        String userId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(userId)
                .claim("email", "supabase-user@example.com")
                .claim("role", "authenticated")
                .claim("app_metadata", Map.of("role", "admin"))
                .issuer(expectedIssuer)
                .audience().add(expectedAudience).and()
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();

        assertTrue(jwtDecoder.isTokenValid(token, "access"));
        assertEquals("supabase-user@example.com", jwtDecoder.extractEmail(token));
        assertEquals(userId, jwtDecoder.extractId(token));
        assertEquals("admin", jwtDecoder.extractRole(token));
        assertTrue(jwtDecoder.extractIsAdmin(token));
    }

    @Test
    void testSupabaseServiceRoleAdmin() {
        String userId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(userId)
                .claim("email", "service-account@example.com")
                .claim("role", "service_role")
                .issuer(expectedIssuer)
                .audience().add(expectedAudience).and()
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();

        assertTrue(jwtDecoder.isTokenValid(token, "access"));
        assertEquals("service-account@example.com", jwtDecoder.extractEmail(token));
        assertEquals(userId, jwtDecoder.extractId(token));
        assertEquals("service_role", jwtDecoder.extractRole(token));
        assertTrue(jwtDecoder.extractIsAdmin(token));
    }
}
