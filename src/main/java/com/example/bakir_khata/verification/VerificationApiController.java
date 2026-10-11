package com.example.bakir_khata.verification;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/verification") @RequiredArgsConstructor
public class VerificationApiController {
    private final VerificationService verification;
    private final UserRepository users;
    public record CodeRequest(String code) {}
    @GetMapping("/status") public Map<String,Boolean> status(@AuthenticationPrincipal User actor) {
        User user=users.findById(actor.getId()).orElseThrow();
        return Map.of("emailVerified",user.isEmailVerified(),"phoneVerified",user.isPhoneVerified());
    }
    @PostMapping("/email/send") public Map<String,String> emailSend(@AuthenticationPrincipal User actor) {
        return Map.of("message",verification.sendEmail(actor));
    }
    @PostMapping("/email/confirm") public Map<String,Boolean> emailConfirm(@AuthenticationPrincipal User actor,@RequestBody CodeRequest code) {
        return Map.of("verified",verification.verifyEmail(actor,code.code()));
    }
    @PostMapping("/phone/send") public Map<String,String> phoneSend(@AuthenticationPrincipal User actor) {
        var result=verification.sendPhone(actor);
        return Map.of("message","OTP request accepted for "+result.maskedPhone(),
                "mode",result.mode()); // Never expose demo code through a public API.
    }
    @PostMapping("/phone/confirm") public Map<String,Boolean> phoneConfirm(@AuthenticationPrincipal User actor,@RequestBody CodeRequest code) {
        return Map.of("verified",verification.verifyPhone(actor,code.code()));
    }
}
