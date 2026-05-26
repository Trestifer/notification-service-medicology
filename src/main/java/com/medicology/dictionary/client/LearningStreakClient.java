package com.medicology.dictionary.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
public class LearningStreakClient {

    private static final String TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String learningBaseUrl;
    private final String internalServiceToken;

    public LearningStreakClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${learning.service-url:http://localhost:8081}") String learningBaseUrl,
            @Value("${app.internal-service-token:}") String internalServiceToken) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.learningBaseUrl = trimTrailingSlash(learningBaseUrl);
        this.internalServiceToken = internalServiceToken;
    }

    public Optional<LearningStreak> getStreak(UUID userId) {
        if (userId == null || internalServiceToken == null || internalServiceToken.isBlank()) {
            return Optional.empty();
        }

        String url = learningBaseUrl + "/api/v1/learning/internal/users/" + userId + "/streak";
        HttpHeaders headers = new HttpHeaders();
        headers.set(TOKEN_HEADER, internalServiceToken);
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }
            Integer currentStreak = data.path("currentStreak").isNumber()
                    ? data.path("currentStreak").asInt()
                    : null;
            LocalDate lastActivityDate = parseDate(data.path("lastActivityDate").asText(null));
            return Optional.of(new LearningStreak(currentStreak, lastActivityDate));
        } catch (Exception ex) {
            if (ex instanceof RestClientException) {
                log.warn("learning streak lookup failed userId={} reason={}", userId, ex.getMessage());
            } else {
                log.warn("learning streak response parse failed userId={} reason={}", userId, ex.getMessage());
            }
            return Optional.empty();
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record LearningStreak(Integer currentStreak, LocalDate lastActivityDate) {}
}
