package com.medicology.dictionary.dto.response;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        int numberOfElements,
        boolean first,
        boolean last) {
}
