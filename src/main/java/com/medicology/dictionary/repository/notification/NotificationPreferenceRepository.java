package com.medicology.dictionary.repository.notification;

import com.medicology.dictionary.entity.NotificationPreference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByEmailEnabledTrue();
}
