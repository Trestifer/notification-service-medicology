package com.medicology.dictionary.repository.notification;

import com.medicology.dictionary.entity.NotificationDelivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    Optional<NotificationDelivery> findFirstByNotification_IdOrderBySentAtDesc(UUID notificationId);
}
