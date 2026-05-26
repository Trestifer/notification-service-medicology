package com.medicology.dictionary.service;

import com.medicology.dictionary.dto.request.NotificationCreateRequest;
import com.medicology.dictionary.dto.request.NotificationPreferenceRequest;
import com.medicology.dictionary.dto.response.NotificationPageResponse;
import com.medicology.dictionary.dto.response.NotificationPreferenceResponse;
import com.medicology.dictionary.dto.response.NotificationResponse;
import com.medicology.dictionary.entity.Notification;
import com.medicology.dictionary.entity.NotificationDelivery;
import com.medicology.dictionary.entity.NotificationPreference;
import com.medicology.dictionary.repository.notification.NotificationDeliveryRepository;
import com.medicology.dictionary.repository.notification.NotificationPreferenceRepository;
import com.medicology.dictionary.repository.notification.NotificationRepository;
import com.medicology.dictionary.wrapper.UserPrincipal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications() {
        UUID userId = authenticatedUserService.getCurrentUserId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getCurrentUserNotifications(int page, int size, Boolean read) {
        UUID userId = authenticatedUserService.getCurrentUserId();
        Pageable pageable = buildPageable(page, size);
        Page<Notification> notifications = read == null
                ? notificationRepository.findByUserId(userId, pageable)
                : notificationRepository.findByUserIdAndRead(userId, read, pageable);
        return toPageResponse(notifications);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications() {
        UUID userId = authenticatedUserService.getCurrentUserId();
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getUnreadNotifications(int page, int size) {
        UUID userId = authenticatedUserService.getCurrentUserId();
        return toPageResponse(notificationRepository.findByUserIdAndReadFalse(userId, buildPageable(page, size)));
    }

    @Transactional(readOnly = true)
    public long countUnreadNotifications() {
        UUID userId = authenticatedUserService.getCurrentUserId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse createNotification(NotificationCreateRequest request) {
        UserPrincipal currentUser = authenticatedUserService.getCurrentUser();
        UUID targetUserId = request.userId() != null ? request.userId() : currentUser.getId();
        if (!currentUser.isAdmin() && !currentUser.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền tạo thông báo cho người dùng khác.");
        }

        Notification notification = new Notification();
        notification.setUserId(targetUserId);
        notification.setType(request.type().trim().toUpperCase());
        notification.setTitle(request.title().trim());
        notification.setMessage(request.message().trim());
        notification.setRelatedCommentId(request.relatedCommentId());
        notification.setRelatedCourseId(request.relatedCourseId());

        Notification saved = notificationRepository.save(notification);

        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotification(saved);
        delivery.setStatus("PENDING");
        deliveryRepository.save(delivery);

        return toResponse(saved);
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        Notification notification = findOwnedNotification(id);
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        return toResponse(notification);
    }

    @Transactional
    public NotificationResponse markUnread(UUID id) {
        Notification notification = findOwnedNotification(id);
        notification.setRead(false);
        notification.setReadAt(null);
        return toResponse(notification);
    }

    @Transactional
    public NotificationPreferenceResponse getPreference() {
        UserPrincipal currentUser = authenticatedUserService.getCurrentUser();
        NotificationPreference preference = getOrCreatePreference(currentUser.getId());
        syncPreferenceEmail(preference, currentUser.getEmail());
        return toPreferenceResponse(preference);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(NotificationPreferenceRequest request) {
        UserPrincipal currentUser = authenticatedUserService.getCurrentUser();
        NotificationPreference preference = getOrCreatePreference(currentUser.getId());
        syncPreferenceEmail(preference, currentUser.getEmail());
        if (request.emailEnabled() != null) {
            preference.setEmailEnabled(request.emailEnabled());
        }
        if (request.dailyReminderEnabled() != null) {
            preference.setDailyReminderEnabled(request.dailyReminderEnabled());
        }
        if (request.reminderTime() != null) {
            preference.setReminderTime(request.reminderTime());
        }
        return toPreferenceResponse(preference);
    }

    private void syncPreferenceEmail(NotificationPreference preference, String email) {
        if (email != null && !email.isBlank()) {
            preference.setEmail(email.trim());
        }
    }

    private Notification findOwnedNotification(UUID id) {
        UUID userId = authenticatedUserService.getCurrentUserId();
        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo."));
    }

    private NotificationPreference getOrCreatePreference(UUID userId) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            NotificationPreference preference = new NotificationPreference();
            preference.setUserId(userId);
            preference.setEmailEnabled(true);
            preference.setDailyReminderEnabled(true);
            preference.setReminderTime(LocalTime.of(8, 0));
            return preferenceRepository.save(preference);
        });
    }

    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private NotificationPageResponse toPageResponse(Page<Notification> notifications) {
        List<NotificationResponse> content = notifications.stream()
                .map(this::toResponse)
                .toList();
        return new NotificationPageResponse(
                content,
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getNumberOfElements(),
                notifications.isFirst(),
                notifications.isLast());
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationDelivery delivery = deliveryRepository.findFirstByNotification_IdOrderBySentAtDesc(notification.getId())
                .orElse(null);
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedCommentId(),
                notification.getRelatedCourseId(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                delivery != null ? delivery.getStatus() : "PENDING",
                delivery != null ? delivery.getSentAt() : null,
                delivery != null ? delivery.getFailureReason() : null);
    }

    private NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.getUserId(),
                preference.isEmailEnabled(),
                preference.getEmail(),
                preference.isDailyReminderEnabled(),
                preference.getReminderTime());
    }
}
