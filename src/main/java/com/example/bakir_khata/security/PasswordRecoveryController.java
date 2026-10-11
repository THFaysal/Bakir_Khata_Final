package com.example.bakir_khata.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    @GetMapping("/forgot-password") public String forgot() {return "auth/forgot-password";}
    @PostMapping("/forgot-password") public String request(@RequestParam String email,RedirectAttributes ra) {
        recovery.request(email);
        ra.addFlashAttribute("successMessage","If the account exists, recovery instructions have been sent. In local demo mode check application logs.");
        return "redirect:/forgot-password";
    }
    @GetMapping("/reset-password") public String resetPage(@RequestParam String token,Model model) {
        model.addAttribute("token",token);return "auth/reset-password";
    }
    @PostMapping("/reset-password") public String reset(@RequestParam String token,@RequestParam String password,
                        @RequestParam String confirmPassword,RedirectAttributes ra) {
        recovery.reset(token,password,confirmPassword);
        ra.addFlashAttribute("successMessage","Password updated. Sign in using your new password.");
        return "redirect:/login";
    }
}
