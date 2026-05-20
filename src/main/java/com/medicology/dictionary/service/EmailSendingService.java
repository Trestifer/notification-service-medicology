package com.medicology.dictionary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.dictionary.dto.EmailSendRequest;
import com.medicology.dictionary.dto.EmailSendResponse;
import com.medicology.dictionary.dto.EmailTemplatePreviewRequest;
import com.medicology.dictionary.dto.EmailTemplateType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailSendingService {

    private static final String SENDGRID_MAIL_SEND_URL = "https://api.sendgrid.com/v3/mail/send";

    private final EmailTemplateService emailTemplateService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${sendgrid.from-email:}")
    private String fromEmail;

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    public EmailSendingService(
            EmailTemplateService emailTemplateService,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper
    ) {
        this.emailTemplateService = emailTemplateService;
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
    }

    public EmailSendResponse sendEmail(EmailSendRequest request) {
        EmailTemplateType templateType = emailTemplateService.resolveTemplateType(
                request.templateType(),
                request.currentStreak(),
                request.lastActivityDate()
        );
        EmailTemplatePreviewRequest templateRequest = buildTemplateRequest(request, templateType);
        String subject = subjectFor(request, templateType);
        String preview = templateRequest.message();
        String html = prepareHtmlForSend(templateType, emailTemplateService.renderEmail(templateType, templateRequest, request.currentStreak()));

        sendViaSendGrid(request.toEmail(), subject, preview, html, templateType);

        return new EmailSendResponse(request.toEmail(), templateType, subject, true, "SENT", null);
    }

    private EmailTemplatePreviewRequest buildTemplateRequest(EmailSendRequest request, EmailTemplateType templateType) {
        EmailTemplatePreviewRequest defaults = defaultsFor(templateType);
        return new EmailTemplatePreviewRequest(
                valueOrDefault(request.recipientName(), defaults.recipientName()),
                valueOrDefault(request.headline(), defaults.headline()),
                valueOrDefault(request.message(), defaults.message()),
                valueOrDefault(request.actionText(), defaults.actionText()),
                valueOrDefault(request.actionUrl(), defaults.actionUrl()),
                valueOrDefault(request.secondaryMessage(), defaults.secondaryMessage()),
                valueOrDefault(request.supportEmail(), defaults.supportEmail())
        );
    }

    private EmailTemplatePreviewRequest defaultsFor(EmailTemplateType templateType) {
        return switch (templateType) {
            case STREAK_RISK -> new EmailTemplatePreviewRequest(
                    "bạn",
                    "Streak của bạn đang có nguy cơ bị mất",
                    "Hoàn thành một bài học ngắn hôm nay để bảo vệ tiến độ của bạn.",
                    "Giữ streak ngay",
                    "https://medicology.app",
                    "Một phiên ôn tập ngắn cũng được tính. Hãy quay lại trước khi ngày kết thúc để giữ ngọn lửa học tập.",
                    "support@medicology.app"
            );
            case EVERYDAY_REMINDER -> emailTemplateService.defaultEverydayReminderPreviewRequest();
            case CALLING_BACK -> emailTemplateService.defaultCallingBackPreviewRequest();
            case NOTIFICATION -> emailTemplateService.defaultPreviewRequest();
        };
    }

    private String subjectFor(EmailSendRequest request, EmailTemplateType templateType) {
        if (request.subject() != null && !request.subject().isBlank()) {
            return request.subject();
        }
        int currentStreak = request.currentStreak() == null ? 0 : Math.max(0, request.currentStreak());
        return switch (templateType) {
            case STREAK_RISK -> currentStreak > 0
                    ? "Đừng để mất streak " + currentStreak + " ngày của bạn"
                    : "Streak của bạn cần bạn hôm nay";
            case EVERYDAY_REMINDER -> "Sẵn sàng cho bài học hôm nay chưa?";
            case CALLING_BACK -> "Quay lại Medicology nhé";
            case NOTIFICATION -> "Cập nhật mới từ Medicology";
        };
    }

    private String prepareHtmlForSend(EmailTemplateType templateType, String html) {
        return switch (templateType) {
            case EVERYDAY_REMINDER -> html.replace("src=\"/images/16.svg\"", "src=\"cid:mascot-16\"");
            case CALLING_BACK -> html.replace("src=\"/images/19.svg\"", "src=\"cid:mascot-19\"");
            case STREAK_RISK -> html.replace("src=\"/images/20.svg\"", "src=\"cid:mascot-20\"");
            case NOTIFICATION -> html;
        };
    }

    private void sendViaSendGrid(String toEmail, String subject, String text, String html, EmailTemplateType templateType) {
        if (sendGridApiKey == null || sendGridApiKey.isBlank() || fromEmail == null || fromEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SendGrid is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sendGridApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail)))));
        body.put("from", Map.of("email", fromEmail));
        body.put("subject", subject);
        body.put("content", List.of(
                Map.of("type", "text/plain", "value", text),
                Map.of("type", "text/html", "value", html)
        ));
        inlineMascotAttachment(templateType).ifPresent(attachment -> body.put("attachments", List.of(attachment)));

        try {
            String payload = objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = restTemplateBuilder.build().exchange(
                    SENDGRID_MAIL_SEND_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SendGrid rejected the email.");
            }
        } catch (JsonProcessingException | RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SendGrid send failed: " + ex.getMessage());
        }
    }

    private java.util.Optional<Map<String, Object>> inlineMascotAttachment(EmailTemplateType templateType) {
        String fileName = switch (templateType) {
            case EVERYDAY_REMINDER -> "16.svg";
            case CALLING_BACK -> "19.svg";
            case STREAK_RISK -> "20.svg";
            case NOTIFICATION -> null;
        };
        if (fileName == null) {
            return java.util.Optional.empty();
        }

        try {
            byte[] bytes = StreamUtils.copyToByteArray(new ClassPathResource("static/images/" + fileName).getInputStream());
            String contentId = "mascot-" + fileName.replace(".svg", "");
            return java.util.Optional.of(Map.of(
                    "content", Base64.getEncoder().encodeToString(bytes),
                    "type", "image/svg+xml",
                    "filename", fileName,
                    "disposition", "inline",
                    "content_id", contentId
            ));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot load mascot image: " + fileName);
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
