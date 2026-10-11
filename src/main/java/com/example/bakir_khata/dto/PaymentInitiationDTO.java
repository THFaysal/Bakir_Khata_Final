package com.example.bakir_khata.dto;

import com.example.bakir_khata.model.enums.TransactionMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentInitiationDTO(
        @NotNull(message = "Loan is required")
        Long loanId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Enter a valid amount")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        TransactionMethod method,

        Long paymentAccountId,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Enter a valid verification phone number")
        String payerPhone,

        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note,

        boolean holdConfirmed
) {
    public PaymentInitiationDTO() {
        this(null, null, TransactionMethod.CASH, null, null, null, false);
    }

    public PaymentInitiationDTO(Long loanId) {
        this(loanId, null, TransactionMethod.CASH, null, null, null, false);
    }
}
