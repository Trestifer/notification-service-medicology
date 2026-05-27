package com.medicology.dictionary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.dictionary.client.AuthUserClient;
import com.medicology.dictionary.client.LearningStreakClient;
import com.medicology.dictionary.dto.EmailTemplatePreviewRequest;
import com.medicology.dictionary.dto.EmailTemplateType;
import com.medicology.dictionary.dto.request.EmailReminderRequest;
import com.medicology.dictionary.dto.response.EmailReminderResponse;
import com.medicology.dictionary.entity.Notification;
import com.medicology.dictionary.entity.NotificationDelivery;
import com.medicology.dictionary.entity.NotificationPreference;
import com.medicology.dictionary.repository.notification.NotificationDeliveryRepository;
import com.medicology.dictionary.repository.notification.NotificationPreferenceRepository;
import com.medicology.dictionary.repository.notification.NotificationRepository;
import com.medicology.dictionary.wrapper.UserPrincipal;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EmailReminderService {

    private static final String DAILY_REMINDER = "DAILY_STUDY_REMINDER";
    private static final String CALLING_BACK = "CALLING_BACK_REMINDER";
    private static final String STREAK_RISK = "STREAK_RISK_REMINDER";
    private static final String SENDGRID_MAIL_SEND_URL = "https://api.sendgrid.com/v3/mail/send";
    private static final String DEFAULT_ACTION_URL = "https://medicology.app";
    private static final String DEFAULT_SUPPORT_EMAIL = "support@medicology.app";

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AuthUserClient authUserClient;
    private final LearningStreakClient learningStreakClient;
    private final EmailTemplateService emailTemplateService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${sendgrid.from-email:}")
    private String fromEmail;

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Transactional
    public EmailReminderResponse sendDailyReminder(EmailReminderRequest request) {
        ReminderTarget target = resolveTarget(request);
        ReminderStreak streak = resolveStreak(target, request);
        int currentStreak = streakValue(streak);
        EmailTemplateType templateType = emailTemplateService.resolveTemplateType(
                null,
                currentStreak,
                streak.lastActivityDate());
        return sendAndRecord(
                target,
                notificationTypeFor(templateType),
                templateType,
                buildReminderCopy(templateType, streak),
                currentStreak);
    }

    @Transactional
    public EmailReminderResponse sendStreakRiskReminder(EmailReminderRequest request) {
        ReminderTarget target = resolveTarget(request);
        return sendStreakRiskReminder(target, resolveStreak(target, request));
    }

    @Transactional
    public int sendDueRemindersToSubscribedUsers(ZoneId zoneId) {
        int sent = 0;
        LocalTime currentTime = LocalTime.now(zoneId);
        LocalDate today = LocalDate.now(zoneId);

        for (NotificationPreference preference : preferenceRepository.findByEmailEnabledTrue()) {
            if (!preference.isDailyReminderEnabled() || !isReminderMinute(preference.getReminderTime(), currentTime)) {
                continue;
            }
            String email = resolveEmail(preference);
            if (email == null) {
                continue;
            }
            EmailReminderResponse response = sendScheduledReminder(new ReminderTarget(preference.getUserId(), email), today);
            if (response.sent()) {
                sent++;
            }
        }
        return sent;
    }

    private EmailReminderResponse sendScheduledReminder(ReminderTarget target, LocalDate today) {
        ReminderStreak streak = resolveStreak(target, null);
        int currentStreak = streakValue(streak);
        EmailTemplateType templateType = emailTemplateService.resolveTemplateType(
                null,
                currentStreak,
                streak.lastActivityDate());
        String type = notificationTypeFor(templateType);

        if (notificationRepository.existsByUserIdAndTypeAndCreatedAtBetween(
                target.userId(), type, today.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            return new EmailReminderResponse(
                    null,
                    target.userId(),
                    target.email(),
                    type,
                    "Reminder skipped",
                    "Reminder already created today.",
                    currentStreak,
                    false,
                    "SKIPPED",
                    null);
        }
        return sendAndRecord(target, type, templateType, buildReminderCopy(templateType, streak), currentStreak);
    }

    private String resolveEmail(NotificationPreference preference) {
        String apiEmail = authUserClient.getUser(preference.getUserId())
                .map(AuthUserClient.AuthUser::email)
                .map(EmailReminderService::trimToNull)
                .orElse(null);
        if (apiEmail != null) {
            return apiEmail;
        }

        return trimToNull(preference.getEmail());
    }

    private EmailReminderResponse sendStreakRiskReminder(ReminderTarget target, ReminderStreak requestStreak) {
        ReminderStreak streak = requestStreak != null ? requestStreak : new ReminderStreak(0, null);
        int currentStreak = streakValue(streak);
        if (!shouldSendStreakRisk(streak)) {
            return new EmailReminderResponse(
                    null,
                    target.userId(),
                    target.email(),
                    STREAK_RISK,
                    "Streak reminder skipped",
                    "User already studied today or has no active streak.",
                    currentStreak,
                    false,
                    "SKIPPED",
                    null);
        }
        return sendAndRecord(
                target,
                STREAK_RISK,
                EmailTemplateType.STREAK_RISK,
                buildReminderCopy(EmailTemplateType.STREAK_RISK, streak),
                currentStreak);
    }

    private ReminderTarget resolveTarget(EmailReminderRequest request) {
        UserPrincipal currentUser = authenticatedUserService.getCurrentUser();
        UUID requestedUserId = request != null ? request.userId() : null;
        String requestedEmail = request != null ? trimToNull(request.email()) : null;
        UUID targetUserId = requestedUserId != null ? requestedUserId : currentUser.getId();
        boolean currentUserTarget = currentUser.getId().equals(targetUserId);

        if (!currentUserTarget && !currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ban khong co quyen gui email cho nguoi dung khac.");
        }

        String email = requestedEmail;
        if (email == null) {
            email = authUserClient.getUser(targetUserId)
                    .map(AuthUserClient.AuthUser::email)
                    .map(EmailReminderService::trimToNull)
                    .orElse(null);
        }
        if (email == null && currentUserTarget) {
            email = currentUser.getEmail();
        }
        if (!currentUserTarget && email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khong tim thay email nguoi nhan tu auth service.");
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khong tim thay email nguoi nhan.");
        }
        return new ReminderTarget(targetUserId, email);
    }

    private ReminderStreak resolveStreak(ReminderTarget target, EmailReminderRequest request) {
        Optional<LearningStreakClient.LearningStreak> apiStreak = learningStreakClient.getStreak(target.userId());
        if (apiStreak.isPresent()) {
            LearningStreakClient.LearningStreak streak = apiStreak.get();
            return new ReminderStreak(streak.currentStreak(), streak.lastActivityDate());
        }
        if (request == null) {
            return new ReminderStreak(0, null);
        }
        return new ReminderStreak(request.currentStreak(), request.lastActivityDate());
    }

    private EmailReminderResponse sendAndRecord(
            ReminderTarget target,
            String type,
            EmailTemplateType templateType,
            ReminderCopy copy,
            int currentStreak) {
        Notification notification = new Notification();
        notification.setUserId(target.userId());
        notification.setType(type);
        notification.setTitle(copy.subject());
        notification.setMessage(copy.preview());
        Notification saved = notificationRepository.save(notification);

        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotification(saved);
        try {
            sendViaSendGrid(target.email(), copy.subject(), copy.preview(), buildHtml(copy, currentStreak, templateType), templateType);
            delivery.setStatus("SENT");
            delivery.setSentAt(LocalDateTime.now());
        } catch (RuntimeException ex) {
            delivery.setStatus("FAILED");
            delivery.setFailureReason(ex.getMessage());
        }
        NotificationDelivery savedDelivery = deliveryRepository.save(delivery);

        return new EmailReminderResponse(
                saved.getId(),
                target.userId(),
                target.email(),
                type,
                copy.subject(),
                copy.preview(),
                currentStreak,
                "SENT".equals(savedDelivery.getStatus()),
                savedDelivery.getStatus(),
                savedDelivery.getFailureReason());
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
                Map.of("type", "text/html", "value", html)));
        inlineMascotAttachment(templateType).ifPresent(attachment -> body.put("attachments", List.of(attachment)));

        try {
            String payload = objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = restTemplateBuilder.build().exchange(
                    SENDGRID_MAIL_SEND_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SendGrid rejected the email.");
            }
        } catch (JsonProcessingException | RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SendGrid send failed: " + ex.getMessage());
        }
    }

    private ReminderCopy buildReminderCopy(EmailTemplateType templateType, ReminderStreak streak) {
        int currentStreak = streakValue(streak);
        return switch (templateType) {
            case STREAK_RISK -> new ReminderCopy(
                    "Dung de mat streak " + currentStreak + " ngay cua ban",
                    "Hoan thanh mot bai hoc ngan hom nay de bao ve tien do cua ban.",
                    "Streak cua ban dang co nguy co bi mat");
            case CALLING_BACK -> new ReminderCopy(
                    "Quay lai Medicology nhe",
                    callbackBySeverity(streak),
                    "Quay lai Medicology nhe");
            case EVERYDAY_REMINDER -> new ReminderCopy(
                    currentStreak > 0 ? "San sang giu streak " + currentStreak + " ngay?" : "San sang cho bai hoc hom nay chua?",
                    "Chi can mot phien hoc ngan tren Medicology hom nay cung giup ban giu thoi quen hoc deu.",
                    "San sang cho bai hoc hom nay chua?");
            case NOTIFICATION -> new ReminderCopy(
                    "Cap nhat moi tu Medicology",
                    "Ban co mot cap nhat moi trong hanh trinh hoc tap.",
                    "Cap nhat moi tu Medicology");
        };
    }

    private String buildHtml(ReminderCopy copy, int currentStreak, EmailTemplateType templateType) {
        EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest(
                null,
                copy.headline(),
                copy.preview(),
                actionTextFor(templateType),
                DEFAULT_ACTION_URL,
                secondaryMessageFor(templateType),
                DEFAULT_SUPPORT_EMAIL);
        return prepareHtmlForSend(templateType, emailTemplateService.renderEmail(templateType, request, currentStreak));
    }

    private String prepareHtmlForSend(EmailTemplateType templateType, String html) {
        return switch (templateType) {
            case EVERYDAY_REMINDER -> html.replace("src=\"/images/16.svg\"", "src=\"cid:mascot-16\"");
            case CALLING_BACK -> html.replace("src=\"/images/19.svg\"", "src=\"cid:mascot-19\"");
            case STREAK_RISK -> html.replace("src=\"/images/20.svg\"", "src=\"cid:mascot-20\"");
            case NOTIFICATION -> html;
        };
    }

    private Optional<Map<String, Object>> inlineMascotAttachment(EmailTemplateType templateType) {
        String fileName = switch (templateType) {
            case EVERYDAY_REMINDER -> "16.svg";
            case CALLING_BACK -> "19.svg";
            case STREAK_RISK -> "20.svg";
            case NOTIFICATION -> null;
        };
        if (fileName == null) {
            return Optional.empty();
        }

        try {
            byte[] bytes = StreamUtils.copyToByteArray(new ClassPathResource("static/images/" + fileName).getInputStream());
            String contentId = "mascot-" + fileName.replace(".svg", "");
            return Optional.of(Map.of(
                    "content", Base64.getEncoder().encodeToString(bytes),
                    "type", "image/svg+xml",
                    "filename", fileName,
                    "disposition", "inline",
                    "content_id", contentId));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot load mascot image: " + fileName);
        }
    }

    private static boolean shouldSendStreakRisk(ReminderStreak streak) {
        if (streak == null || streakValue(streak) <= 0 || streak.lastActivityDate() == null) {
            return false;
        }
        return streak.lastActivityDate().isEqual(LocalDate.now().minusDays(1));
    }

    private static String callbackBySeverity(ReminderStreak streak) {
        if (streak == null || streak.lastActivityDate() == null) {
            return "Lau roi minh chua thay ban hoc. Quay lai voi mot bai ngan de khoi dong lai nhe.";
        }
        long inactiveDays = java.time.temporal.ChronoUnit.DAYS.between(streak.lastActivityDate(), LocalDate.now());
        if (inactiveDays <= 1) {
            return "Bai hoc tiep theo van dang cho ban, chi vai phut la ban co the bat nhip lai.";
        }
        if (inactiveDays <= 3) {
            return "Streak co the da bi gian doan, nhung chi can mot phien hoc hom nay la ban bat dau lai duoc.";
        }
        return "Kien thuc y khoa can duoc on deu. Hom nay la luc tot de ket noi lai voi Medicology.";
    }

    private static boolean isReminderMinute(LocalTime reminderTime, LocalTime currentTime) {
        LocalTime dueTime = reminderTime != null ? reminderTime : LocalTime.of(8, 0);
        return dueTime.getHour() == currentTime.getHour() && dueTime.getMinute() == currentTime.getMinute();
    }

    private static String notificationTypeFor(EmailTemplateType templateType) {
        return switch (templateType) {
            case STREAK_RISK -> STREAK_RISK;
            case CALLING_BACK -> CALLING_BACK;
            case EVERYDAY_REMINDER -> DAILY_REMINDER;
            case NOTIFICATION -> DAILY_REMINDER;
        };
    }

    private static String actionTextFor(EmailTemplateType templateType) {
        return switch (templateType) {
            case STREAK_RISK -> "Giu streak ngay";
            case CALLING_BACK -> "Tiep tuc hoc";
            case EVERYDAY_REMINDER -> "Bat dau hoc";
            case NOTIFICATION -> "Mo Medicology";
        };
    }

    private static String secondaryMessageFor(EmailTemplateType templateType) {
        return switch (templateType) {
            case STREAK_RISK -> "Mot phien on tap ngan cung duoc tinh. Hay quay lai truoc khi ngay ket thuc de giu ngon lua hoc tap.";
            case CALLING_BACK -> "Quay lai hom nay de giu kien thuc y khoa luon moi va de nho.";
            case EVERYDAY_REMINDER -> "Tiep tuc tu noi ban da dung lai va bien hom nay thanh mot ngay hoc hieu qua.";
            case NOTIFICATION -> "Hoc mot chut moi ngay se giup ban giu vung tien do.";
        };
    }

    private static int streakValue(ReminderStreak streak) {
        return streak != null && streak.currentStreak() != null ? Math.max(0, streak.currentStreak()) : 0;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ReminderTarget(UUID userId, String email) {}
    private record ReminderStreak(Integer currentStreak, LocalDate lastActivityDate) {}
    private record ReminderCopy(String subject, String preview, String headline) {}
}
