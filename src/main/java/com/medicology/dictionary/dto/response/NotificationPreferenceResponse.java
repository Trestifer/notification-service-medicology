package com.medicology.dictionary.dto.response;

import java.time.LocalTime;
import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID userId,
        boolean emailEnabled,
        String email,
        boolean dailyReminderEnabled,
        LocalTime reminderTime) {
}
