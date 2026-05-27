package com.medicology.dictionary.dto;

public record EmailTemplatePreviewRequest(
        String recipientName,
        String headline,
        String message,
        String actionText,
        String actionUrl,
        String secondaryMessage,
        String supportEmail
) {
}
