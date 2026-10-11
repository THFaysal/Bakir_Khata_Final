package com.example.bakir_khata.community;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable, deliberately public fields; no email, phone, sessions, or JPA entities. */
public record PublicBroadcastView(
        Long id, Long ownerId, String messengerId, String name,
        BigDecimal amount, String purpose, String description,
        int durationDays, LoanBroadcast.Status status, LocalDateTime expiresAt) {
    // Keep bean-style accessors for Thymeleaf (in addition to Java record accessors).
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getMessengerId() { return messengerId; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public String getPurpose() { return purpose; }
    public String getDescription() { return description; }
    public int getDurationDays() { return durationDays; }
    public LoanBroadcast.Status getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
