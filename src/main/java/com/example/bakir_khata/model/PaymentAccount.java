package com.example.bakir_khata.model;

import com.example.bakir_khata.model.enums.MobileProvider;
import com.example.bakir_khata.model.enums.PaymentAccountType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PaymentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentAccountType type;

    @Column(length = 80)
    private String bankName;

    @Column(length = 120)
    private String accountHolderName;

    @Column(length = 80)
    private String accountNumber;

    @Column(length = 100)
    private String branchName;

    @Column(length = 30)
    private String routingNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MobileProvider mobileProvider;

    @Column(length = 20)
    private String mobileNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean primaryAccount = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Only trusted proof types count. Legacy rows used to set verified=true merely on save;
    // NULL proof marks those older rows as UNVERIFIED until an actual OTP is completed.
    @Column(name = "verification_method", length = 32)
    private String verificationMethod;

    // Binds a one-time challenge to the current revision of the saved destination.
    // Nullable for compatibility with older payment_accounts tables.
    @Builder.Default
    private Long verificationGeneration = 0L;

    private LocalDateTime verifiedAt;

    // The legacy `verified` database column was previously set on every save.
    // Provider ownership MUST have separate authentic API evidence; a phone OTP is not enough.
    @Transient
    public boolean isVerified() {
        return active && verified && verifiedAt != null &&
                ("PROVIDER_API".equals(verificationMethod) || "BANK_API".equals(verificationMethod));
    }

    @Transient
    public boolean isPhoneControlVerified() {
        return active && type == PaymentAccountType.MOBILE && verifiedAt != null
                && ("TWILIO_SMS_OTP".equals(verificationMethod) || "DEMO_OTP".equals(verificationMethod));
    }

    @Transient
    public boolean isDemoPhoneProof() {
        return isPhoneControlVerified() && "DEMO_OTP".equals(verificationMethod);
    }

    @Transient
    public boolean isRealSmsPhoneProof() {
        return isPhoneControlVerified() && "TWILIO_SMS_OTP".equals(verificationMethod);
    }

    @Transient
    public String getVerificationStatusText() {
        if (type == PaymentAccountType.BANK) {
            return isVerified() ? "Bank ownership verified by provider" :
                    "Bank ownership not verified - verification service unavailable";
        }
        if (isVerified()) return "Wallet registration verified by provider";
        if (isRealSmsPhoneProof()) return "SMS verified - wallet registration NOT checked";
        if (isDemoPhoneProof()) return "Demo OTP only - wallet registration NOT checked";
        return "Unverified - confirm control of this phone number";
    }

    public void clearVerification() {
        verified = false;
        verifiedAt = null;
        verificationMethod = null;
    }


    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public String getDisplayLabel() {
        if (type == PaymentAccountType.MOBILE) {
            String provider = mobileProvider == null ? "Mobile" : mobileProvider.getDisplayName();
            return provider + " - " + mask(mobileNumber);
        }
        return (bankName == null ? "Bank" : bankName) + " - " + mask(accountNumber);
    }

    @Transient
    public String getMaskedDestination() {
        return type == PaymentAccountType.MOBILE ? mask(mobileNumber) : mask(accountNumber);
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "Not provided";
        String clean = value.trim();
        if (clean.length() <= 4) return "****";
        return "*".repeat(Math.max(4, clean.length() - 4)) + clean.substring(clean.length() - 4);
    }
}
