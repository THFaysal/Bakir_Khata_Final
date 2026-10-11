package com.example.bakir_khata.dto;

import lombok.Builder;

@Builder
public record UserRatingDTO(
        double score,
        String label,
        int onTimeCount,
        int lateCount,
        int overdueCount,
        boolean hasHistory,
        int filledStars
) {
}
