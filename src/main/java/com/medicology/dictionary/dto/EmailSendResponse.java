package com.medicology.dictionary.dto;

public record EmailSendResponse(
        String toEmail,
        EmailTemplateType templateType,
        String subject,
        boolean sent,
        String status,
        String failureReason
) {
}
