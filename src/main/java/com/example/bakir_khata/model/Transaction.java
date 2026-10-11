package com.example.bakir_khata.model;

import com.example.bakir_khata.model.enums.TransactionMethod;
import com.example.bakir_khata.model.enums.TransactionStatus;
import com.example.bakir_khata.model.enums.MobileProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the auditable lifecycle of a borrower repayment. Cash payments
 * wait for lender confirmation, while bank/mobile payments require OTP and a
 * gateway result. A final Payment row is created only after the transaction
 * reaches SUCCESS, so pending/rejected attempts never change the loan balance.
 */
@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    // The loan this claimed payment applies to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    // The borrower who submitted the transaction (owner of the loan)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user_id", nullable = false)
    private User initiatedBy;

    // Lender's linked user; confirms cash and receives digital-payment notifications
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_user_id", nullable = false)
    private User counterpartyUser;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal overduePenaltyAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal penaltyLenderShare = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal penaltyPlatformShare = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalPayable = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionMethod method;

    // Required only when method == BANK
    private String bankAccountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_account_id")
    private PaymentAccount paymentAccount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MobileProvider mobileProvider;

    @Column(length = 120)
    private String paymentProvider;

    @Column(length = 80)
    private String recipientMasked;

    // Required when method == BANK or MOBILE_BANKING
    @Column(length = 80, unique = true)
    private String transactionReferenceId;

    @Column(length = 80)
    private String gatewayReference;

    @Column(length = 20)
    private String verificationPhone;

    @Column(nullable = false)
    @Builder.Default
    private boolean otpVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    // Populated only after SUCCESS - links to the final repayment ledger entry
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private String note;

    @Column(nullable = false)
    @Builder.Default
    private boolean flagged = false;

    @Column(length = 500)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime respondedAt;
}
