package com.medicology.dictionary.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record EmailSendRequest(
        @NotBlank @Email String toEmail,
        String recipientName,
        EmailTemplateType templateType,
        String subject,
        String headline,
        String message,
        String actionText,
        String actionUrl,
        String secondaryMessage,
        String supportEmail,
        Integer currentStreak,
        LocalDate lastActivityDate
) {
}
