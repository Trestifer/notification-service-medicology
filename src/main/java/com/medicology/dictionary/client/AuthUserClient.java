package com.medicology.dictionary.client;

import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class AuthUserClient {

    private static final String TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate;
    private final String authBaseUrl;
    private final String internalServiceToken;

    public AuthUserClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${auth.service-url:http://localhost:8080}") String authBaseUrl,
            @Value("${app.internal-service-token:}") String internalServiceToken) {
        this.restTemplate = restTemplateBuilder.build();
        this.authBaseUrl = trimTrailingSlash(authBaseUrl);
        this.internalServiceToken = internalServiceToken;
    }

    public Optional<AuthUser> getUser(UUID userId) {
        if (userId == null || internalServiceToken == null || internalServiceToken.isBlank()) {
            return Optional.empty();
        }

        String url = authBaseUrl + "/api/v1/auth/internal/users/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(TOKEN_HEADER, internalServiceToken);
        try {
            ResponseEntity<AuthUser> response =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), AuthUser.class);
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("auth user lookup failed userId={} reason={}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record AuthUser(UUID id, String email) {}
}
