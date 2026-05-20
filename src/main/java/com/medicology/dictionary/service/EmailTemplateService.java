package com.medicology.dictionary.service;

import com.medicology.dictionary.dto.EmailTemplatePreviewRequest;
import com.medicology.dictionary.dto.EmailTemplateType;
import com.medicology.dictionary.dto.StreakRiskEmailPreviewRequest;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class EmailTemplateService {

    private static final String DEFAULT_RECIPIENT_NAME = "bạn";
    private static final String DEFAULT_HEADLINE = "Cập nhật mới từ Medicology";
    private static final String DEFAULT_MESSAGE = "Bạn có một cập nhật mới trong hành trình học tập.";
    private static final String DEFAULT_ACTION_TEXT = "Mở Medicology";
    private static final String DEFAULT_ACTION_URL = "https://medicology.app";
    private static final String DEFAULT_SECONDARY_MESSAGE = "Học một chút mỗi ngày sẽ giúp bạn giữ vững tiến độ.";
    private static final String DEFAULT_SUPPORT_EMAIL = "support@medicology.app";
    private static final int DEFAULT_CURRENT_STREAK = 7;
    private static final String STREAK_RISK_ACTION_TEXT = "Giữ streak ngay";
    private static final String EVERYDAY_REMINDER_HEADLINE = "Sẵn sàng cho bài học hôm nay chưa?";
    private static final String EVERYDAY_REMINDER_MESSAGE = "Chỉ cần một phiên học ngắn trên Medicology hôm nay cũng giúp bạn giữ thói quen học đều.";
    private static final String EVERYDAY_REMINDER_ACTION_TEXT = "Bắt đầu học";
    private static final String EVERYDAY_REMINDER_SECONDARY_MESSAGE = "Tiếp tục từ nơi bạn đã dừng lại và biến hôm nay thành một ngày học hiệu quả.";
    private static final String CALLING_BACK_HEADLINE = "Quay lại Medicology nhé";
    private static final String CALLING_BACK_MESSAGE = "Bài học tiếp theo vẫn đang chờ bạn, chỉ vài phút là bạn có thể bắt nhịp lại.";
    private static final String CALLING_BACK_ACTION_TEXT = "Tiếp tục học";
    private static final String CALLING_BACK_SECONDARY_MESSAGE = "Quay lại hôm nay để giữ kiến thức y khoa luôn mới và dễ nhớ.";

    private final TemplateEngine templateEngine;

    public EmailTemplateService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderNotificationEmail(EmailTemplatePreviewRequest request) {
        Context context = new Context();
        context.setVariable("recipientName", valueOrDefault(request.recipientName(), DEFAULT_RECIPIENT_NAME));
        context.setVariable("headline", valueOrDefault(request.headline(), DEFAULT_HEADLINE));
        context.setVariable("message", valueOrDefault(request.message(), DEFAULT_MESSAGE));
        context.setVariable("actionText", valueOrDefault(request.actionText(), DEFAULT_ACTION_TEXT));
        context.setVariable("actionUrl", valueOrDefault(request.actionUrl(), DEFAULT_ACTION_URL));
        context.setVariable("secondaryMessage", valueOrDefault(request.secondaryMessage(), DEFAULT_SECONDARY_MESSAGE));
        context.setVariable("supportEmail", valueOrDefault(request.supportEmail(), DEFAULT_SUPPORT_EMAIL));

        return templateEngine.process("email/notification-email", context);
    }

    public String renderEmail(EmailTemplateType templateType, EmailTemplatePreviewRequest request, Integer currentStreak) {
        return switch (templateType) {
            case STREAK_RISK -> renderStreakRiskEmail(new StreakRiskEmailPreviewRequest(
                    request.recipientName(),
                    currentStreak,
                    request.actionText(),
                    request.actionUrl(),
                    request.supportEmail()
            ));
            case EVERYDAY_REMINDER -> renderEverydayReminderEmail(request);
            case CALLING_BACK -> renderCallingBackEmail(request);
            case NOTIFICATION -> renderNotificationEmail(request);
        };
    }

    public String renderStreakRiskEmail(StreakRiskEmailPreviewRequest request) {
        Context context = new Context();
        context.setVariable("recipientName", valueOrDefault(request.recipientName(), DEFAULT_RECIPIENT_NAME));
        context.setVariable("currentStreak", currentStreakOrDefault(request.currentStreak()));
        context.setVariable("actionText", valueOrDefault(request.actionText(), STREAK_RISK_ACTION_TEXT));
        context.setVariable("actionUrl", valueOrDefault(request.actionUrl(), DEFAULT_ACTION_URL));
        context.setVariable("supportEmail", valueOrDefault(request.supportEmail(), DEFAULT_SUPPORT_EMAIL));

        return templateEngine.process("email/streak-risk-email", context);
    }

    public String renderEverydayReminderEmail(EmailTemplatePreviewRequest request) {
        Context context = new Context();
        context.setVariable("recipientName", valueOrDefault(request.recipientName(), DEFAULT_RECIPIENT_NAME));
        context.setVariable("headline", valueOrDefault(request.headline(), EVERYDAY_REMINDER_HEADLINE));
        context.setVariable("message", valueOrDefault(request.message(), EVERYDAY_REMINDER_MESSAGE));
        context.setVariable("actionText", valueOrDefault(request.actionText(), EVERYDAY_REMINDER_ACTION_TEXT));
        context.setVariable("actionUrl", valueOrDefault(request.actionUrl(), DEFAULT_ACTION_URL));
        context.setVariable("secondaryMessage", valueOrDefault(request.secondaryMessage(), EVERYDAY_REMINDER_SECONDARY_MESSAGE));
        context.setVariable("supportEmail", valueOrDefault(request.supportEmail(), DEFAULT_SUPPORT_EMAIL));

        return templateEngine.process("email/everyday-reminder-email", context);
    }

    public String renderCallingBackEmail(EmailTemplatePreviewRequest request) {
        Context context = new Context();
        context.setVariable("recipientName", valueOrDefault(request.recipientName(), DEFAULT_RECIPIENT_NAME));
        context.setVariable("headline", valueOrDefault(request.headline(), CALLING_BACK_HEADLINE));
        context.setVariable("message", valueOrDefault(request.message(), CALLING_BACK_MESSAGE));
        context.setVariable("actionText", valueOrDefault(request.actionText(), CALLING_BACK_ACTION_TEXT));
        context.setVariable("actionUrl", valueOrDefault(request.actionUrl(), DEFAULT_ACTION_URL));
        context.setVariable("secondaryMessage", valueOrDefault(request.secondaryMessage(), CALLING_BACK_SECONDARY_MESSAGE));
        context.setVariable("supportEmail", valueOrDefault(request.supportEmail(), DEFAULT_SUPPORT_EMAIL));

        return templateEngine.process("email/calling-back-email", context);
    }

    public EmailTemplatePreviewRequest defaultPreviewRequest() {
        return new EmailTemplatePreviewRequest(
                DEFAULT_RECIPIENT_NAME,
                DEFAULT_HEADLINE,
                DEFAULT_MESSAGE,
                DEFAULT_ACTION_TEXT,
                DEFAULT_ACTION_URL,
                DEFAULT_SECONDARY_MESSAGE,
                DEFAULT_SUPPORT_EMAIL
        );
    }

    public StreakRiskEmailPreviewRequest defaultStreakRiskPreviewRequest() {
        return new StreakRiskEmailPreviewRequest(
                DEFAULT_RECIPIENT_NAME,
                DEFAULT_CURRENT_STREAK,
                STREAK_RISK_ACTION_TEXT,
                DEFAULT_ACTION_URL,
                DEFAULT_SUPPORT_EMAIL
        );
    }

    public EmailTemplatePreviewRequest defaultEverydayReminderPreviewRequest() {
        return new EmailTemplatePreviewRequest(
                DEFAULT_RECIPIENT_NAME,
                EVERYDAY_REMINDER_HEADLINE,
                EVERYDAY_REMINDER_MESSAGE,
                EVERYDAY_REMINDER_ACTION_TEXT,
                DEFAULT_ACTION_URL,
                EVERYDAY_REMINDER_SECONDARY_MESSAGE,
                DEFAULT_SUPPORT_EMAIL
        );
    }

    public EmailTemplatePreviewRequest defaultCallingBackPreviewRequest() {
        return new EmailTemplatePreviewRequest(
                DEFAULT_RECIPIENT_NAME,
                CALLING_BACK_HEADLINE,
                CALLING_BACK_MESSAGE,
                CALLING_BACK_ACTION_TEXT,
                DEFAULT_ACTION_URL,
                CALLING_BACK_SECONDARY_MESSAGE,
                DEFAULT_SUPPORT_EMAIL
        );
    }

    public EmailTemplateType resolveTemplateType(
            EmailTemplateType requestedTemplateType,
            Integer currentStreak,
            LocalDate lastActivityDate
    ) {
        if (requestedTemplateType != null) {
            return requestedTemplateType;
        }
        if (shouldUseStreakRisk(currentStreak, lastActivityDate)) {
            return EmailTemplateType.STREAK_RISK;
        }
        if (shouldUseCallingBack(lastActivityDate)) {
            return EmailTemplateType.CALLING_BACK;
        }
        return EmailTemplateType.EVERYDAY_REMINDER;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int currentStreakOrDefault(Integer currentStreak) {
        return currentStreak == null || currentStreak < 0 ? DEFAULT_CURRENT_STREAK : currentStreak;
    }

    private boolean shouldUseStreakRisk(Integer currentStreak, LocalDate lastActivityDate) {
        return currentStreak != null
                && currentStreak > 0
                && lastActivityDate != null
                && lastActivityDate.isEqual(LocalDate.now().minusDays(1));
    }

    private boolean shouldUseCallingBack(LocalDate lastActivityDate) {
        return lastActivityDate == null || ChronoUnit.DAYS.between(lastActivityDate, LocalDate.now()) > 1;
    }
}
