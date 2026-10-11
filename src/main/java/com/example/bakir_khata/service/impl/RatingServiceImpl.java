package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.dto.UserRatingDTO;
import com.example.bakir_khata.config.ApplicationCacheConfig;
import org.springframework.cache.annotation.Cacheable;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.repository.PaymentRepository;
import com.example.bakir_khata.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService{
    private static final double NEUTRAL_STARTING_SCORE = 3.0;
    private static final double ON_TIME_WEIGHT = 5.0;
    private static final double LATE_WEIGHT = 2.0;
    private static final double OVERDUE_PENALTY = 1.0;

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Cacheable(cacheNames = ApplicationCacheConfig.BORROWER_RATINGS, key = "#p0.id", sync = true)
    public UserRatingDTO calculateRating(User user) {
        List<Loan> loans = loanRepository.findByUserOrderByDueDateAsc(user);

        int onTime = 0;
        int late = 0;
        int overdue = 0;

        for (Loan loan : loans) {
            if (loan.getStatus() == LoanStatus.OVERDUE) {
                overdue++;
            } else if (loan.getStatus() == LoanStatus.PAID) {
                if (wasPaidLate(loan)) {
                    late++;
                } else {
                    onTime++;
                }
            }
        }

        int closedLoans = onTime + late;
        boolean hasHistory = closedLoans > 0 || overdue > 0;

        double score;
        if (!hasHistory) {
            score = NEUTRAL_STARTING_SCORE;
        } else {
            double base = closedLoans == 0
                    ? NEUTRAL_STARTING_SCORE
                    : (onTime * ON_TIME_WEIGHT + late * LATE_WEIGHT) / closedLoans;
            score = base - (overdue * OVERDUE_PENALTY);
        }
        score = clamp(score, 0.0, 5.0);
        score = Math.round(score * 10.0) / 10.0;

        return UserRatingDTO.builder()
                .score(score)
                .label(labelFor(score, hasHistory))
                .onTimeCount(onTime)
                .lateCount(late)
                .overdueCount(overdue)
                .hasHistory(hasHistory)
                .filledStars((int) Math.round(score))
                .build();
    }

    private boolean wasPaidLate(Loan loan) {
        List<Payment> payments = paymentRepository.findByLoanOrderByPaymentDateDescCreatedAtDesc(loan);
        return !payments.isEmpty() && payments.get(0).getPaymentDate().isAfter(loan.getDueDate());
    }

    private String labelFor(double score, boolean hasHistory) {
        if (!hasHistory) return "New Borrower";
        if (score >= 4.5) return "Excellent";
        if (score >= 3.5) return "Good";
        if (score >= 2.5) return "Fair";
        return "Needs Improvement";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
