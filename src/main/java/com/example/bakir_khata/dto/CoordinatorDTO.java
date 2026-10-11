package com.example.bakir_khata.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CoordinatorDTO(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String approvedByName,
        LocalDateTime approvedAt,
        boolean active,
        BigDecimal monthlySalary
) {
}
