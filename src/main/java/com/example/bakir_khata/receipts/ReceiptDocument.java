package com.example.bakir_khata.receipts;

import java.util.List;

/** A read-only, identity-filtered presentation of a stored loan/payment record. */
public record ReceiptDocument(
        ReceiptKind kind, Long sourceId, String title, String reference, String status,
        String amountLabel, String amount, String description,
        List<Field> details, List<Field> breakdown,
        String notice, String verifyUrl) {
    public record Field(String label, String value) { }
}
