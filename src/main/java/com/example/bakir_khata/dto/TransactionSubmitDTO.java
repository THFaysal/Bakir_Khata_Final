package com.example.bakir_khata.dto;

import com.example.bakir_khata.model.enums.TransactionMethod;

import java.math.BigDecimal;

/**
 * Incoming form payload when a borrower submits a transaction for lender confirmation.
 * bankAccountNumber and transactionReferenceId are validated conditionally in the
 * service layer based on method (not enforced here, since records can't do
 * cross-field validation in the constructor cleanly without a compact constructor).
 */
public record TransactionSubmitDTO(
        Long loanId,
        BigDecimal amount,
        TransactionMethod method,
        String bankAccountNumber,
        String transactionReferenceId,
        String note
) {
    public TransactionSubmitDTO {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}
