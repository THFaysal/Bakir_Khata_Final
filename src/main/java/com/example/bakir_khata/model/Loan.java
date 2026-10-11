package com.example.bakir_khata.model;


import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.model.enums.LoanTag;
import com.example.bakir_khata.model.enums.PaymentType;
import com.example.bakir_khata.model.enums.Priority;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loans")
@Data
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lender_id", nullable = false)
    private Lender lender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overduePenalty = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer overdueDays = 0;

    /** Last date through which an overdue penalty was actually paid/settled. */
    private LocalDate penaltySettledThrough;

    /** Cumulative penalty already paid, used to enforce the lifetime penalty cap. */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalPenaltyPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate borrowDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(length = 150)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentType paymentType = PaymentType.ONE_TIME;

    private Integer installmentCount;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedInstallmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LoanTag tag;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @Transient
    public int getRepaymentPercentage() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        BigDecimal paid = amount.subtract(remainingAmount);
        return paid.multiply(BigDecimal.valueOf(100))
                .divide(amount, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }
    @Transient
    public boolean isUntouched() {
        return amount != null && remainingAmount != null
                && remainingAmount.compareTo(amount) == 0;
    }

    @Transient
    public boolean isFullyLocked() {
        return status == LoanStatus.PAID;
    }

    @Transient
    public boolean isPartiallyLocked() {
        return !isFullyLocked() && !isUntouched();
    }
    @Transient
    public boolean isDeletable() {
        return isUntouched();
    }

    @Transient
    public BigDecimal getTotalOutstanding() {
        BigDecimal principal = remainingAmount == null ? BigDecimal.ZERO : remainingAmount;
        BigDecimal penalty = overduePenalty == null ? BigDecimal.ZERO : overduePenalty;
        return principal.add(penalty);
    }
}
