package com.example.bakir_khata.verification;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import com.example.bakir_khata.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@Service @RequiredArgsConstructor
public class VerificationService {
    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final EmailChallengeRepository challenges;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final OtpService otp;
    @Value("${app.verify.email.mode:console}") private String emailMode;
    @Value("${app.verify.email.resend-key:}") private String resendKey;
    @Value("${app.verify.email.from:}") private String sender;
    @Value("${spring.profiles.active:dev}") private String activeProfile;

    @Transactional
    public String sendEmail(User current) {
        User user = load(current);
        if (user.isEmailVerified()) return "Email already verified.";
        challenges.findFirstByUserIdAndVerifiedFalseOrderByCreatedAtDesc(user.getId())
                .filter(c -> c.getCreatedAt().plusSeconds(60).isAfter(LocalDateTime.now()))
                .ifPresent(c -> { throw new BusinessRuleException("Wait 60 seconds before requesting a new email code."); });
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailChallenge challenge = new EmailChallenge();
        challenge.setUser(user); challenge.setCodeHash(encoder.encode(code));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        challenges.save(challenge);
        if ("resend".equalsIgnoreCase(emailMode)) {
            if (resendKey.isBlank() || sender.isBlank()) throw new BusinessRuleException("Resend email settings are not configured.");
            try {
                RestClient.builder().baseUrl("https://api.resend.com").defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendKey)
                        .build().post().uri("/emails").body(Map.of(
                                "from", sender, "to", new String[]{user.getEmail()},
                                "subject", "Bakir Khata email verification",
                                "text", "Your Bakir Khata verification code is " + code + ". It expires in 10 minutes."))
                        .retrieve().toBodilessEntity();
            } catch (Exception ex) { log.warn("Email delivery failed: {}", ex.getClass().getSimpleName());
                throw new BusinessRuleException("Email service unavailable. Retry later."); }
            return "Verification email sent. Check your inbox.";
        }
        if (activeProfile.toLowerCase().contains("prod")) throw new BusinessRuleException("Production email verification requires Resend.");
        log.info("DEVELOPMENT ONLY: email verification code for account {}: {}", user.getId(), code);
        return "DEV EMAIL CODE: " + code + " (not a real email delivery)";
    }

    @Transactional
    public boolean verifyEmail(User current, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        User user = load(current);
        if (user.isEmailVerified()) return true;
        EmailChallenge challenge = challenges.findFirstByUserIdAndVerifiedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new BusinessRuleException("Request a new email verification code."));
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now()) || challenge.getAttempts() >= 5)
            throw new BusinessRuleException("Email verification code expired or locked. Request a new code.");
        challenge.setAttempts(challenge.getAttempts() + 1);
        boolean valid = encoder.matches(code, challenge.getCodeHash());
        if (valid) { challenge.setVerified(true); user.setEmailVerified(true); users.save(user); }
        challenges.save(challenge);
        return valid;
    }

    public OtpService.OtpDelivery sendPhone(User current) {
        User user = load(current);
        if (user.isPhoneVerified()) throw new BusinessRuleException("Mobile number already verified.");
        return otp.send(user, "COMMUNITY_VERIFICATION");
    }

    @Transactional
    public boolean verifyPhone(User current, String code) {
        User user = load(current);
        if (user.isPhoneVerified()) return true;
        return otp.verify(user, "COMMUNITY_VERIFICATION", code);
    }

    private User load(User current) {
        return users.findById(current.getId()).orElseThrow(() -> new BusinessRuleException("Account not found"));
    }

    public User requireCommunity(User user) {
        User fresh = load(user);
        if (!fresh.isCommunityVerified()) throw new BusinessRuleException("Verify your email and mobile number in Verification before joining the community.");
        if (!fresh.isEnabled()) throw new BusinessRuleException("Account is inactive.");
        return fresh;
    }
}
