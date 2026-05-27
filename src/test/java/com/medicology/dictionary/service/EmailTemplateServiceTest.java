package com.medicology.dictionary.service;

import com.medicology.dictionary.dto.EmailTemplatePreviewRequest;
import com.medicology.dictionary.dto.EmailTemplateType;
import com.medicology.dictionary.dto.StreakRiskEmailPreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class EmailTemplateServiceTest {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Test
    void rendersNotificationEmailWithProvidedValues() {
        EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest(
                "Alex",
                "Time for today's lesson",
                "Your next cardiology lesson is ready.",
                "Start lesson",
                "https://medicology.app/lessons/123",
                "A short session today keeps your streak active.",
                "care@medicology.app"
        );

        String html = emailTemplateService.renderNotificationEmail(request);

        assertThat(html).contains("Chào <span>Alex</span>");
        assertThat(html).contains("Time for today&#39;s lesson");
        assertThat(html).contains("Your next cardiology lesson is ready.");
        assertThat(html).contains("https://medicology.app/lessons/123");
        assertThat(html).contains("Start lesson");
        assertThat(html).contains("care@medicology.app");
    }

    @Test
    void rendersStreakRiskEmailWithMascotAndStreakCount() {
        StreakRiskEmailPreviewRequest request = new StreakRiskEmailPreviewRequest(
                "Mina",
                20,
                "Save streak",
                "https://medicology.app/streak",
                "care@medicology.app"
        );

        String html = emailTemplateService.renderStreakRiskEmail(request);

        assertThat(html).contains("/images/20.svg");
        assertThat(html).contains("Chào <span>Mina</span>");
        assertThat(html).contains("Streak của bạn đang có nguy cơ bị mất");
        assertThat(html).contains("<div class=\"streak-number\"");
        assertThat(html).contains(">20</div>");
        assertThat(html).contains("&#128293;");
        assertThat(html).contains("Save streak");
    }

    @Test
    void rendersEverydayReminderEmailWithMascotAndReminderCopy() {
        EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest(
                "Sam",
                "Your lesson is waiting",
                "Spend five minutes reviewing today.",
                "Review now",
                "https://medicology.app/review",
                "Small daily reviews add up.",
                "care@medicology.app"
        );

        String html = emailTemplateService.renderEverydayReminderEmail(request);

        assertThat(html).contains("/images/16.svg");
        assertThat(html).contains("Chào <span>Sam</span>");
        assertThat(html).contains("Your lesson is waiting");
        assertThat(html).contains("Spend five minutes reviewing today.");
        assertThat(html).contains("Review now");
        assertThat(html).contains("Small daily reviews add up.");
    }

    @Test
    void rendersCallingBackEmailWithMascotAndComeBackCopy() {
        EmailTemplatePreviewRequest request = new EmailTemplatePreviewRequest(
                "Nora",
                "Come back today",
                "Your unfinished lesson is ready when you are.",
                "Continue now",
                "https://medicology.app/continue",
                "A quick return keeps your learning fresh.",
                "care@medicology.app"
        );

        String html = emailTemplateService.renderCallingBackEmail(request);

        assertThat(html).contains("/images/19.svg");
        assertThat(html).contains("Chào <span>Nora</span>");
        assertThat(html).contains("Come back today");
        assertThat(html).contains("Your unfinished lesson is ready when you are.");
        assertThat(html).contains("Continue now");
        assertThat(html).contains("A quick return keeps your learning fresh.");
    }

    @Test
    void resolvesStreakRiskForActiveStreakLastSeenYesterday() {
        EmailTemplateType type = emailTemplateService.resolveTemplateType(
                null,
                5,
                LocalDate.now().minusDays(1)
        );

        assertThat(type).isEqualTo(EmailTemplateType.STREAK_RISK);
    }

    @Test
    void resolvesCallingBackForInactiveUser() {
        EmailTemplateType type = emailTemplateService.resolveTemplateType(
                null,
                0,
                LocalDate.now().minusDays(3)
        );

        assertThat(type).isEqualTo(EmailTemplateType.CALLING_BACK);
    }

    @Test
    void resolvesEverydayReminderForUserActiveToday() {
        EmailTemplateType type = emailTemplateService.resolveTemplateType(
                null,
                3,
                LocalDate.now()
        );

        assertThat(type).isEqualTo(EmailTemplateType.EVERYDAY_REMINDER);
    }

    @Test
    void explicitTemplateTypeOverridesAutomaticSelection() {
        EmailTemplateType type = emailTemplateService.resolveTemplateType(
                EmailTemplateType.NOTIFICATION,
                5,
                LocalDate.now().minusDays(1)
        );

        assertThat(type).isEqualTo(EmailTemplateType.NOTIFICATION);
    }
}
