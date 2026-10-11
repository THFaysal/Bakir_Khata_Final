package com.example.bakir_khata.service;

import com.example.bakir_khata.model.Loan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class FinancialPolicyService {

    @Value("${app.finance.service-fee-rate:0.04}")
    private BigDecimal serviceFeeRate;

    @Value("${app.finance.daily-overdue-rate:0.005}")
    private BigDecimal dailyOverdueRate;

    @Value("${app.finance.max-penalty-rate:0.20}")
    private BigDecimal maxPenaltyRate;

    @Value("${app.finance.platform-penalty-share:0.20}")
    private BigDecimal platformPenaltyShare;

    public BigDecimal calculateServiceFee(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return money(amount.multiply(serviceFeeRate));
    }

    public BigDecimal calculateOverduePenalty(Loan loan, LocalDate today) {
        if (loan == null || loan.getDueDate() == null || !loan.getDueDate().isBefore(today)
                || loan.getRemainingAmount() == null || loan.getRemainingAmount().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        LocalDate accrualStart = loan.getDueDate();
        if (loan.getPenaltySettledThrough() != null && loan.getPenaltySettledThrough().isAfter(accrualStart)) {
            accrualStart = loan.getPenaltySettledThrough();
        }
        long chargeableDays = Math.max(0, ChronoUnit.DAYS.between(accrualStart, today));
        BigDecimal raw = loan.getRemainingAmount()
                .multiply(dailyOverdueRate)
                .multiply(BigDecimal.valueOf(chargeableDays));

        BigDecimal lifetimeCap = loan.getAmount().multiply(maxPenaltyRate);
        BigDecimal alreadyPaid = loan.getTotalPenaltyPaid() == null ? BigDecimal.ZERO : loan.getTotalPenaltyPaid();
        BigDecimal remainingCap = lifetimeCap.subtract(alreadyPaid).max(BigDecimal.ZERO);
        return money(raw.min(remainingCap));
    }

    public int overdueDays(Loan loan, LocalDate today) {
        if (loan == null || loan.getDueDate() == null || !loan.getDueDate().isBefore(today)) return 0;
        return (int) Math.max(0, ChronoUnit.DAYS.between(loan.getDueDate(), today));
    }

    public BigDecimal platformPenaltyShare(BigDecimal penalty) {
        if (penalty == null) return BigDecimal.ZERO;
        return money(penalty.multiply(platformPenaltyShare));
    }

    public BigDecimal lenderPenaltyShare(BigDecimal penalty) {
        if (penalty == null) return BigDecimal.ZERO;
        return money(penalty.subtract(platformPenaltyShare(penalty)));
    }

    public BigDecimal getServiceFeePercent() { return serviceFeeRate.multiply(BigDecimal.valueOf(100)); }
    public BigDecimal getDailyOverduePercent() { return dailyOverdueRate.multiply(BigDecimal.valueOf(100)); }
    public BigDecimal getMaxPenaltyPercent() { return maxPenaltyRate.multiply(BigDecimal.valueOf(100)); }
    public BigDecimal getPlatformPenaltySharePercent() { return platformPenaltyShare.multiply(BigDecimal.valueOf(100)); }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
