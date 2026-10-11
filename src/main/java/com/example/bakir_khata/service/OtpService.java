package com.example.bakir_khata.service;

import com.example.bakir_khata.model.User;

public interface OtpService {
    OtpDelivery send(User user, String purpose);
    OtpDelivery sendToPhone(User user, String phone, String purpose);
    boolean verify(User user, String purpose, String code);
    // Verify only the challenge sent to this exact phone. Returns its delivery mode,
    // so a local demo code cannot accidentally be treated as genuine SMS evidence.
    VerificationResult verifyForPhone(User user, String purpose, String expectedPhone, String code);
    record VerificationResult(boolean approved, String deliveryMode) {}
    String normalizePhone(String phone);

    record OtpDelivery(String maskedPhone, String mode, String demoCode) {}
}
