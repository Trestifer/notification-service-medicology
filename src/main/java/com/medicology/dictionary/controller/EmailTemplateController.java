package com.medicology.dictionary.controller;

import com.medicology.dictionary.dto.EmailTemplatePreviewRequest;
import com.medicology.dictionary.dto.StreakRiskEmailPreviewRequest;
import com.medicology.dictionary.service.EmailTemplateService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email-templates")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    public EmailTemplateController(EmailTemplateService emailTemplateService) {
        this.emailTemplateService = emailTemplateService;
    }

    @GetMapping(value = "/notification/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewDefaultNotificationEmail() {
        return emailTemplateService.renderNotificationEmail(emailTemplateService.defaultPreviewRequest());
    }

    @PostMapping(value = "/notification/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewNotificationEmail(@RequestBody EmailTemplatePreviewRequest request) {
        if (request == null) {
            request = emailTemplateService.defaultPreviewRequest();
        }

        return emailTemplateService.renderNotificationEmail(request);
    }

    @GetMapping(value = "/streak-risk/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewDefaultStreakRiskEmail() {
        return emailTemplateService.renderStreakRiskEmail(emailTemplateService.defaultStreakRiskPreviewRequest());
    }

    @PostMapping(value = "/streak-risk/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewStreakRiskEmail(@RequestBody StreakRiskEmailPreviewRequest request) {
        if (request == null) {
            request = emailTemplateService.defaultStreakRiskPreviewRequest();
        }

        return emailTemplateService.renderStreakRiskEmail(request);
    }

    @GetMapping(value = "/everyday-reminder/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewDefaultEverydayReminderEmail() {
        return emailTemplateService.renderEverydayReminderEmail(emailTemplateService.defaultEverydayReminderPreviewRequest());
    }

    @PostMapping(value = "/everyday-reminder/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewEverydayReminderEmail(@RequestBody EmailTemplatePreviewRequest request) {
        if (request == null) {
            request = emailTemplateService.defaultEverydayReminderPreviewRequest();
        }

        return emailTemplateService.renderEverydayReminderEmail(request);
    }

    @GetMapping(value = "/calling-back/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewDefaultCallingBackEmail() {
        return emailTemplateService.renderCallingBackEmail(emailTemplateService.defaultCallingBackPreviewRequest());
    }

    @PostMapping(value = "/calling-back/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewCallingBackEmail(@RequestBody EmailTemplatePreviewRequest request) {
        if (request == null) {
            request = emailTemplateService.defaultCallingBackPreviewRequest();
        }

        return emailTemplateService.renderCallingBackEmail(request);
    }
}
