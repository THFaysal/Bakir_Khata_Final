package com.example.bakir_khata.verification;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/verification") @RequiredArgsConstructor
public class VerificationController {
    private final VerificationService verification;
    private final UserRepository users;

    @GetMapping public String index(@AuthenticationPrincipal User principal, Model model) {
        model.addAttribute("account", users.findById(principal.getId()).orElseThrow());
        return "verification/index";
    }
    @PostMapping("/email/send") public String sendEmail(@AuthenticationPrincipal User principal, RedirectAttributes ra) {
        ra.addFlashAttribute("successMessage", verification.sendEmail(principal)); return "redirect:/verification";
    }
    @PostMapping("/email/confirm") public String verifyEmail(@AuthenticationPrincipal User principal,
                     @RequestParam String code, RedirectAttributes ra) {
        boolean valid = verification.verifyEmail(principal, code);
        ra.addFlashAttribute(valid?"successMessage":"errorMessage", valid?"Email verified.":"Invalid email code.");
        return "redirect:/verification";
    }
    @PostMapping("/phone/send") public String sendPhone(@AuthenticationPrincipal User principal, RedirectAttributes ra) {
        var delivered = verification.sendPhone(principal);
        ra.addFlashAttribute("successMessage", "Phone OTP sent to " + delivered.maskedPhone() +
                (delivered.demoCode() != null ? " (DEMO ONLY: " + delivered.demoCode() + ")" : ""));
        return "redirect:/verification";
    }
    @PostMapping("/phone/confirm") public String verifyPhone(@AuthenticationPrincipal User principal,
                     @RequestParam String code, RedirectAttributes ra) {
        boolean valid = verification.verifyPhone(principal, code);
        ra.addFlashAttribute(valid?"successMessage":"errorMessage", valid?"Phone verified.":"Invalid OTP.");
        return "redirect:/verification";
    }
}
