package com.medicology.dictionary.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record NotificationCreateRequest(
        UUID userId,
        @NotBlank String type,
        @NotBlank String title,
        @NotBlank String message,
        UUID relatedCommentId,
        UUID relatedCourseId) {
}
