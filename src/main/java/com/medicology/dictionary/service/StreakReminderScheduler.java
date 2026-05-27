package com.medicology.dictionary.service;

import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreakReminderScheduler {

    private final EmailReminderService emailReminderService;

    @Value("${notification.reminder-zone:Asia/Ho_Chi_Minh}")
    private String reminderZone;

    @Scheduled(
            cron = "${notification.reminder-check.cron:0 * * * * *}",
            zone = "${notification.reminder-zone:Asia/Ho_Chi_Minh}")
    public void sendDueEmailReminders() {
        int sent = emailReminderService.sendDueRemindersToSubscribedUsers(ZoneId.of(reminderZone));
        if (sent > 0) {
            log.info("email_reminder_scheduler_done sent={}", sent);
        }
    }
}
