package com.medicology.dictionary.dto;

public record StreakRiskEmailPreviewRequest(
        String recipientName,
        Integer currentStreak,
        String actionText,
        String actionUrl,
        String supportEmail
) {
}
