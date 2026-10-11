package com.example.bakir_khata.dto;


import com.example.bakir_khata.model.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentDTO(

        Long id,

        @NotNull(message = "Loan is required")
        Long loanId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Enter a valid amount")
        BigDecimal amount,

        @NotNull(message = "Payment date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate paymentDate,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @Size(max = 500, message = "Notes cannot exceed 500 characters")
        String notes,

        MultipartFile proofFile
) {


    public PaymentDTO() {
        this(null, null, null, null, null, null, null);
    }

    public PaymentDTO(Long loanId) {
        this(null, loanId, null, null, null, null, null);
    }
}
