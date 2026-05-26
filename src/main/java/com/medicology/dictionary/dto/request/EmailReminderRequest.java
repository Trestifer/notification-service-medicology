package com.medicology.dictionary.dto.request;

import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import java.util.UUID;

public record EmailReminderRequest(
        UUID userId,
        @Email String email,
        Integer currentStreak,
        LocalDate lastActivityDate) {
}
