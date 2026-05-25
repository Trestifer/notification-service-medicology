package com.medicology.dictionary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.dictionary.dto.EmailTemplatePreviewRequest;
import com.medicology.dictionary.dto.EmailTemplateType;
import com.medicology.dictionary.dto.request.EmailReminderRequest;
import com.medicology.dictionary.dto.response.EmailReminderResponse;
import com.medicology.dictionary.entity.Notification;
import com.medicology.dictionary.entity.NotificationDelivery;
import com.medicology.dictionary.entity.NotificationPreference;
import com.medicology.dictionary.entity.UserDailyStreak;
import com.medicology.dictionary.repository.auth.UserAccountRepository;
import com.medicology.dictionary.repository.learning.UserDailyStreakRepository;
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
    private final UserAccountRepository userAccountRepository;
    private final UserDailyStreakRepository streakRepository;
    private final AuthenticatedUserService authenticatedUserService;
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
        UserDailyStreak streak = streakRepository.findById(target.userId()).orElseGet(() -> emptyStreak(target.userId()));
        int currentStreak = streakValue(streak);
        EmailTemplateType templateType = emailTemplateService.resolveTemplateType(null, currentStreak, streak.getLastActivityDate());
        return sendAndRecord(target, notificationTypeFor(templateType), templateType, buildReminderCopy(templateType, streak), currentStreak);
    }

    @Transactional
    public EmailReminderResponse sendStreakRiskReminder(EmailReminderRequest request) {
        ReminderTarget target = resolveTarget(request);
        return sendStreakRiskReminder(target);
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
            String email = resolveStoredEmail(preference);
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
        UserDailyStreak streak = streakRepository.findById(target.userId()).orElseGet(() -> emptyStreak(target.userId()));
        int currentStreak = streakValue(streak);
        EmailTemplateType templateType = emailTemplateService.resolveTemplateType(null, currentStreak, streak.getLastActivityDate());
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

    private String resolveStoredEmail(NotificationPreference preference) {
        String preferenceEmail = trimToNull(preference.getEmail());
        if (preferenceEmail != null) {
            return preferenceEmail;
        }
        return userAccountRepository.findById(preference.getUserId())
                .map(user -> trimToNull(user.getEmail()))
                .orElse(null);
    }

    private EmailReminderResponse sendStreakRiskReminder(ReminderTarget target) {
        UserDailyStreak streak = streakRepository.findById(target.userId()).orElseGet(() -> emptyStreak(target.userId()));
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

        String email = requestedEmail != null ? requestedEmail : currentUser.getEmail();
        if (!currentUserTarget && requestedEmail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can cung cap email khi admin gui cho user khac.");
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khong tim thay email nguoi nhan.");
        }
        return new ReminderTarget(targetUserId, email);
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

    private ReminderCopy buildReminderCopy(EmailTemplateType templateType, UserDailyStreak streak) {
        int currentStreak = streakValue(streak);
        return switch (templateType) {
            case STREAK_RISK -> new ReminderCopy(
                    "Đừng để mất streak " + currentStreak + " ngày của bạn",
                    "Hoàn thành một bài học ngắn hôm nay để bảo vệ tiến độ của bạn.",
                    "Streak của bạn đang có nguy cơ bị mất");
            case CALLING_BACK -> new ReminderCopy(
                    "Quay lại Medicology nhé",
                    callbackBySeverity(streak),
                    "Quay lại Medicology nhé");
            case EVERYDAY_REMINDER -> new ReminderCopy(
                    currentStreak > 0 ? "Sẵn sàng giữ streak " + currentStreak + " ngày?" : "Sẵn sàng cho bài học hôm nay chưa?",
                    "Chỉ cần một phiên học ngắn trên Medicology hôm nay cũng giúp bạn giữ thói quen học đều.",
                    "Sẵn sàng cho bài học hôm nay chưa?");
            case NOTIFICATION -> new ReminderCopy(
                    "Cập nhật mới từ Medicology",
                    "Bạn có một cập nhật mới trong hành trình học tập.",
                    "Cập nhật mới từ Medicology");
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

    private static boolean shouldSendStreakRisk(UserDailyStreak streak) {
        if (streak == null || streakValue(streak) <= 0 || streak.getLastActivityDate() == null) {
            return false;
        }
        return streak.getLastActivityDate().isEqual(LocalDate.now().minusDays(1));
    }

    private static String callbackBySeverity(UserDailyStreak streak) {
        if (streak == null || streak.getLastActivityDate() == null) {
            return "Lâu rồi mình chưa thấy bạn học. Quay lại với một bài ngắn để khởi động lại nhé.";
        }
        long inactiveDays = java.time.temporal.ChronoUnit.DAYS.between(streak.getLastActivityDate(), LocalDate.now());
        if (inactiveDays <= 1) {
            return "Bài học tiếp theo vẫn đang chờ bạn, chỉ vài phút là bạn có thể bắt nhịp lại.";
        }
        if (inactiveDays <= 3) {
            return "Streak có thể đã bị gián đoạn, nhưng chỉ cần một phiên học hôm nay là bạn bắt đầu lại được.";
        }
        return "Kiến thức y khoa cần được ôn đều. Hôm nay là lúc tốt để kết nối lại với Medicology.";
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
            case STREAK_RISK -> "Giữ streak ngay";
            case CALLING_BACK -> "Tiếp tục học";
            case EVERYDAY_REMINDER -> "Bắt đầu học";
            case NOTIFICATION -> "Mở Medicology";
        };
    }

    private static String secondaryMessageFor(EmailTemplateType templateType) {
        return switch (templateType) {
            case STREAK_RISK -> "Một phiên ôn tập ngắn cũng được tính. Hãy quay lại trước khi ngày kết thúc để giữ ngọn lửa học tập.";
            case CALLING_BACK -> "Quay lại hôm nay để giữ kiến thức y khoa luôn mới và dễ nhớ.";
            case EVERYDAY_REMINDER -> "Tiếp tục từ nơi bạn đã dừng lại và biến hôm nay thành một ngày học hiệu quả.";
            case NOTIFICATION -> "Học một chút mỗi ngày sẽ giúp bạn giữ vững tiến độ.";
        };
    }

    private static int streakValue(UserDailyStreak streak) {
        return streak != null && streak.getCurrentStreak() != null ? Math.max(0, streak.getCurrentStreak()) : 0;
    }

    private static UserDailyStreak emptyStreak(UUID userId) {
        UserDailyStreak streak = new UserDailyStreak();
        streak.setUserId(userId);
        streak.setCurrentStreak(0);
        streak.setLongestStreak(0);
        streak.setTotalActiveDays(0);
        return streak;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ReminderTarget(UUID userId, String email) {}
    private record ReminderCopy(String subject, String preview, String headline) {}
}
