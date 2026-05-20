package com.medicology.dictionary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_daily_streak")
@Getter
@Setter
public class UserDailyStreak {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "current_streak")
    private Integer currentStreak = 0;

    @Column(name = "longest_streak")
    private Integer longestStreak = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "streak_started_at")
    private LocalDate streakStartedAt;

    @Column(name = "total_active_days")
    private Integer totalActiveDays = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
