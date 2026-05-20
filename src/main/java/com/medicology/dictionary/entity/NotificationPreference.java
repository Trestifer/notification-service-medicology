package com.medicology.dictionary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
public class NotificationPreference {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_enabled")
    private boolean emailEnabled = true;

    @Column(name = "email")
    private String email;

    @Column(name = "daily_reminder_enabled")
    private boolean dailyReminderEnabled = true;

    @Column(name = "reminder_time")
    private LocalTime reminderTime = LocalTime.of(8, 0);
}
