package com.medicology.dictionary.dto.response;

import java.util.UUID;

public record EmailReminderResponse(
        UUID notificationId,
        UUID userId,
        String email,
        String type,
        String subject,
        String preview,
        int currentStreak,
        boolean sent,
        String deliveryStatus,
        String failureReason) {
}
