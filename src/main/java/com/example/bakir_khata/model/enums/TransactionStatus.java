package com.example.bakir_khata.model.enums;

public enum TransactionStatus {
    PENDING,
    AWAITING_LENDER_CONFIRMATION,
    OTP_REQUIRED,
    OTP_VERIFIED,
    PROCESSING,
    SUCCESS,
    REJECTED,
    FAILED,
    EXPIRED,
    CANCELLED,
    ACCEPTED
}
