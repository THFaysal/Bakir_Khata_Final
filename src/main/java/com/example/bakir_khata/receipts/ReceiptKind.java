package com.example.bakir_khata.receipts;

public enum ReceiptKind {
    LOAN("BK-LN"), PAYMENT("BK-PAY"), SETTLEMENT("BK-SET");

    private final String prefix;
    ReceiptKind(String prefix) { this.prefix = prefix; }
    public String reference(Long id) { return prefix + "-" + String.format(java.util.Locale.ROOT, "%010d", id); }
}
