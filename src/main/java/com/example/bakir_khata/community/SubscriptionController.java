package com.example.bakir_khata.community;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
public class SubscriptionController {
    private final CommunityService community;
    @Value("${app.payments.mode:demo}") private String mode;

    @GetMapping("/subscription") public String pricing(@AuthenticationPrincipal User user,Model model) {
        model.addAttribute("plan",community.plan(user));
        model.addAttribute("quota",community.weeklyQuota(user));
        model.addAttribute("lenders",community.lenderQuota(user));
        model.addAttribute("demoEnabled","demo".equalsIgnoreCase(mode));
        return "subscription/index";
    }
    @PostMapping("/subscription/demo-upgrade") public String upgrade(@AuthenticationPrincipal User user,RedirectAttributes ra) {
        // In demo mode only, a test upgrade is available without entering payment details.
        if (!"demo".equalsIgnoreCase(mode)) throw new BusinessRuleException("Premium purchase is under construction; ask an admin for a demo grant.");
        // A staff-managed role is used only for permission validation; no external charge.
        community.demoUpgrade(user,30);
        ra.addFlashAttribute("successMessage","DEMO Premium activated for 30 days. No real money was charged.");
        return "redirect:/subscription";
    }
    @GetMapping("/admin/plans") public String adminPlans(Model model) { return "subscription/admin"; }
    @PostMapping("/admin/plans/grant") public String grant(@AuthenticationPrincipal User admin,
                                    @RequestParam Long userId,@RequestParam int days,RedirectAttributes ra) {
        community.grantPremium(admin,userId,days);
        ra.addFlashAttribute("successMessage","Premium demo membership granted.");
        return "redirect:/admin/plans";
    }
}
