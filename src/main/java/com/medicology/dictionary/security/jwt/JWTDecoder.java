package com.medicology.dictionary.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JWTDecoder {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.issuer:medicology-auth}")
    private String expectedIssuer;

    @Value("${jwt.audience:medicology-api}")
    private String expectedAudience;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.get("email", String.class);
        if (email != null) {
            return email;
        }
        return claims.getSubject();
    }

    public String extractId(String token) {
        Claims claims = extractAllClaims(token);
        String id = claims.get("id", String.class);
        if (id != null) {
            return id;
        }
        return claims.getSubject();
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        String role = claims.get("role", String.class);
        if (role != null && !role.equalsIgnoreCase("authenticated")) {
            return role;
        }
        Object appMetadataObj = claims.get("app_metadata");
        if (appMetadataObj instanceof java.util.Map<?, ?> appMetadata) {
            Object appRole = appMetadata.get("role");
            if (appRole instanceof String appRoleStr) {
                return appRoleStr;
            }
        }
        Object userMetadataObj = claims.get("user_metadata");
        if (userMetadataObj instanceof java.util.Map<?, ?> userMetadata) {
            Object userRole = userMetadata.get("role");
            if (userRole instanceof String userRoleStr) {
                return userRoleStr;
            }
        }
        return role;
    }

    public boolean extractIsAdmin(String token) {
        String role = extractRole(token);
        return "ADMIN".equalsIgnoreCase(role) || "service_role".equalsIgnoreCase(role);
    }

    public boolean isTokenValid(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenType = claims.get("type", String.class);
            if (tokenType != null && !expectedType.equals(tokenType)) {
                return false;
            }

            String issuer = claims.getIssuer();
            if (issuer == null || !expectedIssuer.equals(issuer)) {
                return false;
            }

            Object audience = claims.get("aud");
            if (audience instanceof String value) {
                return expectedAudience.equals(value);
            }
            if (audience instanceof Collection<?> values) {
                return values.stream().anyMatch(expectedAudience::equals);
            }
            return false;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
