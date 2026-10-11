package com.example.bakir_khata.security;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service @RequiredArgsConstructor
public class PasswordRecoveryService {
    private static final Logger log=LoggerFactory.getLogger(PasswordRecoveryService.class);
    private final UserRepository users;
    private final PasswordResetRequestRepository requests;
    private final PasswordEncoder passwordEncoder;
    @Value("${app.verify.email.mode:console}") private String mode;
    @Value("${app.verify.email.resend-key:}") private String apiKey;
    @Value("${app.verify.email.from:}") private String from;
    @Value("${app.payments.public-base-url:http://localhost:8080}") private String base;
    @Value("${spring.profiles.active:dev}") private String activeProfile;

    private static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable",ex); }
    }
    @Transactional
    public void request(String email) {
        if (email==null || email.length()>150) return;
        User user=users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user==null || !user.isEnabled()) return; // Same public response for unknown accounts.
        var previous=requests.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (previous!=null && previous.getCreatedAt().plusMinutes(2).isAfter(LocalDateTime.now())) return;
        byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        PasswordResetRequest reset=new PasswordResetRequest();reset.setUser(user);
        reset.setTokenHash(hash(token));reset.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        requests.save(reset);
        String link=base+"/reset-password?token="+URLEncoder.encode(token,StandardCharsets.UTF_8);
        if ("resend".equalsIgnoreCase(mode)) {
            if (apiKey.isBlank() || from.isBlank()) throw new BusinessRuleException("Password recovery mail provider unavailable.");
            try {
                RestClient.builder().baseUrl("https://api.resend.com")
                  .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey).build()
                  .post().uri("/emails").body(Map.of("from",from,"to",new String[]{user.getEmail()},
                         "subject","Reset your Bakir Khata password",
                         "text","Use this one-time password reset link (15 minutes): "+link))
                  .retrieve().toBodilessEntity();
            } catch(Exception ex) { log.warn("Password reset email failed: {}",ex.getClass().getSimpleName());
                throw new BusinessRuleException("Password recovery mail provider is unavailable."); }
        } else if (!activeProfile.toLowerCase().contains("prod")) {
            log.info("DEV ONLY password reset link for account {}: {}",user.getId(),link);
        } else throw new BusinessRuleException("Production password recovery requires an email provider.");
    }
    @Transactional
    public void reset(String token,String newPassword,String confirmPassword) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new BusinessRuleException("Invalid reset link.");
        if (newPassword==null || newPassword.length()<8 || newPassword.length()>72 ||
                !newPassword.equals(confirmPassword)) throw new BusinessRuleException("Password must have 8-72 characters and match its confirmation.");
        PasswordResetRequest reset=requests.findByTokenHashAndUsedFalse(hash(token))
                .orElseThrow(()->new BusinessRuleException("Reset link invalid or already used."));
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessRuleException("Reset link expired.");
        User user=reset.getUser();user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion()+1);
        users.save(user);
        reset.setUsed(true);requests.save(reset);
    }
}
