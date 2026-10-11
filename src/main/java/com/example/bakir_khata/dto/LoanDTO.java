package com.example.bakir_khata.dto;


import com.example.bakir_khata.model.enums.LoanTag;
import com.example.bakir_khata.model.enums.PaymentType;
import com.example.bakir_khata.model.enums.Priority;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanDTO(

        Long id,

        @NotNull(message = "Lender is required")
        Long lenderId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Enter a valid amount")
        BigDecimal amount,

        @NotNull(message = "Borrow date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate borrowDate,

        @NotNull(message = "Due date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dueDate,

        @Size(max = 150, message = "Purpose cannot exceed 150 characters")
        String purpose,

        @NotNull(message = "Priority is required")
        Priority priority,

        LoanTag tag,

        @NotNull(message = "Payment type is required")
        PaymentType paymentType,

        @Min(value = 2, message = "Installment count must be at least 2")
        Integer installmentCount,

        @DecimalMin(value = "0.01", message = "Installment amount must be greater than zero")
        BigDecimal expectedInstallmentAmount,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes
) {


    public LoanDTO {
        if (paymentType == null) {
            paymentType = PaymentType.ONE_TIME;
        }
    }

    public LoanDTO() {
        this(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public boolean isDueDateValid() {
        return borrowDate == null || dueDate == null || !dueDate.isBefore(borrowDate);
    }

    public boolean isInstallmentDataValid() {
        if (paymentType != PaymentType.INSTALLMENT) {
            return true;
        }
        return installmentCount != null && expectedInstallmentAmount != null;
    }
}
