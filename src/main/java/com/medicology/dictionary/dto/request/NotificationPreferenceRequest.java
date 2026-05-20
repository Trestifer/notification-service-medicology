package com.medicology.dictionary.dto.request;

import java.time.LocalTime;

public record NotificationPreferenceRequest(
        Boolean emailEnabled,
        Boolean dailyReminderEnabled,
        LocalTime reminderTime) {
}
