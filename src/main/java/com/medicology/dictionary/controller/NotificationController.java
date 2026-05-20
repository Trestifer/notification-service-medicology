package com.medicology.dictionary.controller;

import com.medicology.dictionary.dto.request.EmailReminderRequest;
import com.medicology.dictionary.dto.request.NotificationCreateRequest;
import com.medicology.dictionary.dto.request.NotificationPreferenceRequest;
import com.medicology.dictionary.dto.response.EmailReminderResponse;
import com.medicology.dictionary.dto.response.NotificationPreferenceResponse;
import com.medicology.dictionary.dto.response.NotificationResponse;
import com.medicology.dictionary.service.EmailReminderService;
import com.medicology.dictionary.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final EmailReminderService emailReminderService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications() {
        return ResponseEntity.ok(notificationService.getCurrentUserNotifications());
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications() {
        return ResponseEntity.ok(notificationService.getUnreadNotifications());
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> countUnreadNotifications() {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnreadNotifications()));
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(@Valid @RequestBody NotificationCreateRequest request) {
        return ResponseEntity.ok(notificationService.createNotification(request));
    }

    @PostMapping("/emails/daily-reminder")
    public ResponseEntity<EmailReminderResponse> sendDailyReminder(@Valid @RequestBody(required = false) EmailReminderRequest request) {
        return ResponseEntity.ok(emailReminderService.sendDailyReminder(request));
    }

    @PostMapping("/emails/streak-risk")
    public ResponseEntity<EmailReminderResponse> sendStreakRiskReminder(@Valid @RequestBody(required = false) EmailReminderRequest request) {
        return ResponseEntity.ok(emailReminderService.sendStreakRiskReminder(request));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PatchMapping("/{id}/unread")
    public ResponseEntity<NotificationResponse> markUnread(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markUnread(id));
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreference() {
        return ResponseEntity.ok(notificationService.getPreference());
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(@RequestBody NotificationPreferenceRequest request) {
        return ResponseEntity.ok(notificationService.updatePreference(request));
    }
}
