package com.medicology.dictionary.controller;

import com.medicology.dictionary.dto.EmailSendRequest;
import com.medicology.dictionary.dto.EmailSendResponse;
import com.medicology.dictionary.service.EmailSendingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
public class EmailSendController {

    private final EmailSendingService emailSendingService;

    public EmailSendController(EmailSendingService emailSendingService) {
        this.emailSendingService = emailSendingService;
    }

    @PostMapping("/send")
    public ResponseEntity<EmailSendResponse> sendEmail(@Valid @RequestBody EmailSendRequest request) {
        return ResponseEntity.ok(emailSendingService.sendEmail(request));
    }
}
