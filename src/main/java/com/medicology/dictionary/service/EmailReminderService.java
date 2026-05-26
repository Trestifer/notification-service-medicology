package com.medicology.dictionary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.dictionary.client.AuthUserClient;
import com.medicology.dictionary.client.LearningStreakClient;
import com.medicology.dictionary.dto.request.EmailReminderRequest;
import com.medicology.dictionary.dto.response.EmailReminderResponse;
import com.medicology.dictionary.entity.Notification;
import com.medicology.dictionary.entity.NotificationDelivery;
import com.medicology.dictionary.entity.NotificationPreference;
import com.medicology.dictionary.repository.notification.NotificationDeliveryRepository;
import com.medicology.dictionary.repository.notification.NotificationPreferenceRepository;
import com.medicology.dictionary.repository.notification.NotificationRepository;
import com.medicology.dictionary.wrapper.UserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class EmailReminderService {

    private static final String DAILY_REMINDER = "DAILY_STUDY_REMINDER";
    private static final String STREAK_RISK = "STREAK_RISK_REMINDER";

    private static final List<String> ACTIVE_STREAK_MESSAGES = List.of(
            "Chuẩn bị bắt đầu 1 ngày học năng động thôi nào, mạch học của bạn đang rất đẹp.",
            "Hôm nay mình tiếp tục nhé, chỉ một bài ngắn cũng giữ nhịp học rất tốt.",
            "Bạn đang tạo được thói quen học đều mỗi ngày, vào học một chút để giữ lửa nào.",
            "Một ngày mới để thêm kiến thức mới, streak của bạn đang cho bạn thêm động lực.",
            "Đừng để những ngày học tốt bị ngắt quãng, bắt đầu bằng một bài nhẹ nhàng nhé.",
            "Tiếp tục hành trình nào, mỗi lần học hôm nay sẽ làm ngày mai dễ dàng hơn.");

    private static final List<String> CALLBACK_MESSAGES = List.of(
            "Lâu rồi mình chưa thấy bạn học. Quay lại với một bài ngắn để khởi động lại nhé.",
            "Streak có thể đã bị gián đoạn, nhưng chỉ cần một phiên học hôm nay là bạn bắt đầu lại được.",
            "Đừng để việc học xa dần thêm. Mở Medicology và hoàn thành một nội dung nhỏ trước nhé.",
            "Bạn đã nghỉ khá lâu rồi. Hãy quay lại bằng bài học ngắn nhất để lấy lại nhịp.",
            "Kiến thức y khoa cần được ôn đều. Hôm nay là lúc tốt để kết nối lại với Medicology.");

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AuthUserClient authUserClient;
    private final LearningStreakClient learningStreakClient;
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
        ReminderCopy copy = buildDailyCopy(streak);
        return sendAndRecord(target, DAILY_REMINDER, copy, streakValue(streak));
    }

    @Transactional
    public EmailReminderResponse sendStreakRiskReminder(EmailReminderRequest request) {
        ReminderTarget target = resolveTarget(request);
        return sendStreakRiskReminder(target, resolveStreak(target, request));
    }

    public int sendStreakRiskRemindersToSubscribedUsers() {
        int sent = 0;
        for (NotificationPreference preference : preferenceRepository.findByEmailEnabledTrue()) {
            String email = resolveEmail(preference);
            if (email == null) {
                continue;
            }
            ReminderTarget target = new ReminderTarget(preference.getUserId(), email);
            EmailReminderResponse response = sendStreakRiskReminder(target, resolveStreak(target, null));
            if (response.sent()) {
                sent++;
            }
        }
        return sent;
    }

    private String resolveEmail(NotificationPreference preference) {
        String apiEmail = authUserClient.getUser(preference.getUserId())
                .map(AuthUserClient.AuthUser::email)
                .map(EmailReminderService::trimToNull)
                .orElse(null);
        if (apiEmail != null) {
            return apiEmail;
        }

        String preferenceEmail = trimToNull(preference.getEmail());
        if (preferenceEmail != null) {
            return preferenceEmail;
        }
        return null;
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
        ReminderCopy copy = new ReminderCopy(
                "Đừng để mất streak " + currentStreak + " ngày của bạn",
                "Chỉ còn từ 21:30 tới trước nửa đêm để giữ streak " + currentStreak + " ngày. Hoàn thành một bài học ngắn trên Medicology ngay nhé.",
                "Streak " + currentStreak + " ngày đang cần bạn quay lại");
        return sendAndRecord(target, STREAK_RISK, copy, currentStreak);
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

    private EmailReminderResponse sendAndRecord(ReminderTarget target, String type, ReminderCopy copy, int currentStreak) {
        Notification notification = new Notification();
        notification.setUserId(target.userId());
        notification.setType(type);
        notification.setTitle(copy.subject());
        notification.setMessage(copy.preview());
        Notification saved = notificationRepository.save(notification);

        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotification(saved);
        try {
            sendViaSendGrid(target.email(), copy.subject(), copy.preview(), buildHtml(copy, currentStreak, type));
            delivery.setStatus("SENT");
            delivery.setSentAt(java.time.LocalDateTime.now());
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

    private void sendViaSendGrid(String toEmail, String subject, String text, String html) {
        if (sendGridApiKey == null || sendGridApiKey.isBlank() || fromEmail == null || fromEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SendGrid is not configured.");
        }

        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sendGridApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail)))),
                "from", Map.of("email", fromEmail),
                "subject", subject,
                "content", List.of(
                        Map.of("type", "text/plain", "value", text),
                        Map.of("type", "text/html", "value", html)));

        try {
            String payload = objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.sendgrid.com/v3/mail/send",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SendGrid rejected the email.");
            }
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SendGrid send failed: " + ex.getMessage());
        }
    }

    private ReminderCopy buildDailyCopy(ReminderStreak streak) {
        boolean validStreak = hasValidStreak(streak);
        String message = validStreak ? random(ACTIVE_STREAK_MESSAGES) : callbackBySeverity(streak);
        int currentStreak = streakValue(streak);
        String subject = validStreak
                ? "Sẵn sàng giữ streak " + currentStreak + " ngày?"
                : "Medicology đang chờ bạn quay lại";
        return new ReminderCopy(subject, message, validStreak ? "Hôm nay học tiếp để giữ nhịp nhé" : "Bắt đầu lại bằng một bài ngắn");
    }

    private static boolean hasValidStreak(ReminderStreak streak) {
        if (streak == null || streakValue(streak) <= 0 || streak.lastActivityDate() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return streak.lastActivityDate().isEqual(today) || streak.lastActivityDate().isEqual(today.minusDays(1));
    }

    private static boolean shouldSendStreakRisk(ReminderStreak streak) {
        if (streak == null || streakValue(streak) <= 0 || streak.lastActivityDate() == null) {
            return false;
        }
        return streak.lastActivityDate().isEqual(LocalDate.now().minusDays(1));
    }

    private static String callbackBySeverity(ReminderStreak streak) {
        if (streak == null || streak.lastActivityDate() == null) {
            return CALLBACK_MESSAGES.get(0);
        }
        long inactiveDays = java.time.temporal.ChronoUnit.DAYS.between(streak.lastActivityDate(), LocalDate.now());
        int index = (int) Math.max(0, Math.min(CALLBACK_MESSAGES.size() - 1, inactiveDays - 1));
        return CALLBACK_MESSAGES.get(index);
    }

    private static String buildHtml(ReminderCopy copy, int currentStreak, String type) {
        String accent = STREAK_RISK.equals(type) ? "#ef4444" : "#14b8a6";
        String cta = STREAK_RISK.equals(type) ? "Giữ streak ngay" : "Vào học ngay";
        return """
                <!doctype html>
                <html>
                <body style="margin:0;background:#f3f7fb;font-family:Arial,Helvetica,sans-serif;color:#152238;">
                  <div style="max-width:640px;margin:0 auto;padding:32px 16px;">
                    <div style="background:#ffffff;border:1px solid #dbe7f0;border-radius:18px;overflow:hidden;">
                      <div style="background:#0f172a;padding:28px 30px;color:#ffffff;">
                        <div style="font-size:14px;letter-spacing:.08em;text-transform:uppercase;color:#a7f3d0;">Medicology</div>
                        <h1 style="margin:10px 0 0;font-size:28px;line-height:1.25;">%s</h1>
                      </div>
                      <div style="padding:30px;">
                        <div style="display:inline-block;background:%s;color:#ffffff;border-radius:999px;padding:10px 16px;font-weight:700;">
                          Streak hiện tại: %d ngày
                        </div>
                        <p style="font-size:17px;line-height:1.7;margin:24px 0;color:#334155;">%s</p>
                        <a href="https://medicology-website.vercel.app/dashboard" style="display:inline-block;background:#0f172a;color:#ffffff;text-decoration:none;border-radius:10px;padding:13px 18px;font-weight:700;">%s</a>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escape(copy.headline()), accent, currentStreak, escape(copy.preview()), cta);
    }

    private static String escape(String value) {
        return Optional.ofNullable(value).orElse("")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static int streakValue(ReminderStreak streak) {
        return streak != null && streak.currentStreak() != null ? Math.max(0, streak.currentStreak()) : 0;
    }

    private static String random(List<String> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
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
