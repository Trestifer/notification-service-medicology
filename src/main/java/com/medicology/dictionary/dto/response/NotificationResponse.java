package com.medicology.dictionary.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String type,
        String title,
        String message,
        UUID relatedCommentId,
        UUID relatedCourseId,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        String deliveryStatus,
        LocalDateTime sentAt,
        String failureReason) {
}
