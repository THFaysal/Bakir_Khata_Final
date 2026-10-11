package com.example.bakir_khata.dto;

import com.example.bakir_khata.model.enums.ApplicationStatus;

import java.time.LocalDateTime;

public record CoordinatorApplicationDTO(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String reason,
        ApplicationStatus status,
        LocalDateTime appliedAt,
        LocalDateTime reviewedAt
) {
}
