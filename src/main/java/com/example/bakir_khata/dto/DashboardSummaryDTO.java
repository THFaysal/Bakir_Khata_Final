package com.example.bakir_khata.dto;


import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Payment;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record DashboardSummaryDTO(
        BigDecimal totalBorrowed,
        BigDecimal totalPaid,
        BigDecimal remainingBalance,
        long overdueCount,
        long dueTodayCount,
        long dueThisWeekCount,
        List<Loan> upcomingDueLoans,
        List<Payment> recentPayments
) {
}
