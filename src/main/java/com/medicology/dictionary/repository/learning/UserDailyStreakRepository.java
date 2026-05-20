package com.medicology.dictionary.repository.learning;

import com.medicology.dictionary.entity.UserDailyStreak;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDailyStreakRepository extends JpaRepository<UserDailyStreak, UUID> {
}
