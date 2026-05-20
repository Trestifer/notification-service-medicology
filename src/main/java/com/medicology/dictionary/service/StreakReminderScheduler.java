package com.medicology.dictionary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreakReminderScheduler {

    private final EmailReminderService emailReminderService;

    @Scheduled(
            cron = "${notification.streak-risk.cron:0 30 21 * * *}",
            zone = "${notification.reminder-zone:Asia/Ho_Chi_Minh}")
    public void sendStreakRiskRemindersAtNight() {
        int sent = emailReminderService.sendStreakRiskRemindersToSubscribedUsers();
        log.info("streak_risk_reminder_scheduler_done sent={}", sent);
    }
}
