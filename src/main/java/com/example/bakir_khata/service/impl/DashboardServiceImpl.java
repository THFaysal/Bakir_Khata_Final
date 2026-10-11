package com.example.bakir_khata.service.impl;


import com.example.bakir_khata.dto.DashboardSummaryDTO;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.repository.PaymentRepository;
import com.example.bakir_khata.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public DashboardSummaryDTO buildSummary(User user) {
        List<Loan> loans = loanRepository.findByUserOrderByDueDateAsc(user);

        BigDecimal totalBorrowed = loans.stream()
                .map(Loan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingBalance = loans.stream()
                .map(Loan::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = totalBorrowed.subtract(remainingBalance);

        long overdueCount = loans.stream().filter(l -> l.getStatus() == LoanStatus.OVERDUE).count();

        LocalDate today = LocalDate.now();
        long dueTodayCount = loans.stream().filter(l -> l.getDueDate().isEqual(today)).count();
        long dueThisWeekCount = loans.stream()
                .filter(l -> !l.getDueDate().isBefore(today) && !l.getDueDate().isAfter(today.plusDays(7)))
                .count();

        List<Loan> upcoming = loans.stream()
                .filter(l -> l.getStatus() != LoanStatus.PAID)
                .sorted(Comparator.comparing(Loan::getDueDate))
                .limit(5)
                .collect(Collectors.toList());

        List<Payment> recentPayments = paymentRepository.findRecentByUser(user).stream()
                .limit(5)
                .collect(Collectors.toList());

        return DashboardSummaryDTO.builder()
                .totalBorrowed(totalBorrowed)
                .totalPaid(totalPaid)
                .remainingBalance(remainingBalance)
                .overdueCount(overdueCount)
                .dueTodayCount(dueTodayCount)
                .dueThisWeekCount(dueThisWeekCount)
                .upcomingDueLoans(upcoming)
                .recentPayments(recentPayments)
                .build();
    }
}
