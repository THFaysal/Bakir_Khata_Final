package com.example.bakir_khata.dto;

import com.example.bakir_khata.model.enums.TransactionMethod;
import com.example.bakir_khata.model.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDTO(
        Long id,
        Long loanId,
        String borrowerName,
        String lenderName,
        BigDecimal amount,
        BigDecimal serviceFee,
        BigDecimal overduePenalty,
        BigDecimal totalPayable,
        TransactionMethod method,
        String paymentProvider,
        String recipientMasked,
        String transactionReferenceId,
        String gatewayReference,
        String verificationPhoneMasked,
        TransactionStatus status,
        boolean otpVerified,
        boolean flagged,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
}
