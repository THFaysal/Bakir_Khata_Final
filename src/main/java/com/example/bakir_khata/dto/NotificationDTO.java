package com.example.bakir_khata.dto;

import java.time.LocalDateTime;

public record NotificationDTO(
        Long id,
        String message,
        String type,
        Long relatedTransactionId,
        boolean actionable,
        boolean isRead,
        LocalDateTime createdAt
) {
}
