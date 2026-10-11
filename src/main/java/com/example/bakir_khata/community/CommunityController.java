package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller @RequestMapping("/community") @RequiredArgsConstructor
public class CommunityController {
    private final CommunityService community;
    private final com.example.bakir_khata.repository.UserRepository users;

    @GetMapping public String feed(@AuthenticationPrincipal User user,Model model) {
        if (users.findById(user.getId()).filter(User::isCommunityVerified).isEmpty()) return "redirect:/verification";
        model.addAttribute("profile",community.profile(user));
        model.addAttribute("feed",community.publicFeed(user));
        model.addAttribute("owned",community.ownBroadcasts(user));
        model.addAttribute("myOffers",community.myOffers(user));
        model.addAttribute("pendingAgreements",community.pendingForLender(user));
        model.addAttribute("borrowerPending",community.pendingForBorrower(user));
        model.addAttribute("connections",community.connections(user));
        model.addAttribute("plan",community.plan(user));
        model.addAttribute("quota",community.weeklyQuota(user));
        model.addAttribute("used",community.usedThisWeek(user));
        model.addAttribute("lenderQuota",community.lenderQuota(user));
        return "community/index";
    }
    @PostMapping("/profile") public String update(@AuthenticationPrincipal User user,
                            @RequestParam String displayName,
                            @RequestParam(required=false) MultipartFile avatar, RedirectAttributes ra) {
        community.updateProfile(user,displayName,avatar);ra.addFlashAttribute("successMessage","Messenger profile saved.");
        return "redirect:/community";
    }
    @PostMapping("/broadcasts") public String publish(@AuthenticationPrincipal User user,
             @RequestParam BigDecimal amount,@RequestParam String purpose,@RequestParam String description,
             @RequestParam int durationDays,RedirectAttributes ra) {
        community.publish(user,amount,purpose,description,durationDays);
        ra.addFlashAttribute("successMessage","Your request is visible to verified community members.");
        return "redirect:/community";
    }
    @PostMapping("/broadcasts/{id}/close") public String close(@AuthenticationPrincipal User user,@PathVariable Long id) {
        community.close(user,id); return "redirect:/community";
    }
    @PostMapping("/offers") public String offer(@AuthenticationPrincipal User user,
                    @RequestParam Long broadcastId,@RequestParam BigDecimal amount,@RequestParam String note,
                    RedirectAttributes ra) {
        community.offer(user,broadcastId,amount,note);ra.addFlashAttribute("successMessage","Lending offer sent.");
        return "redirect:/community";
    }
    @GetMapping("/broadcasts/{id}/offers") public String offers(@AuthenticationPrincipal User user,@PathVariable Long id,Model model) {
        model.addAttribute("offers",community.offersFor(user,id));
        return "community/offers";
    }
    @PostMapping("/offers/{id}/accept") public String accept(@AuthenticationPrincipal User user,@PathVariable Long id,
                                        RedirectAttributes ra) {
        community.accept(user,id);ra.addFlashAttribute("successMessage","Offer accepted. Lender must confirm demo disbursement before the loan appears.");
        return "redirect:/community";
    }
    @PostMapping("/connections/{lenderId}/disconnect")
    public String disconnect(@AuthenticationPrincipal User user,@PathVariable Long lenderId,RedirectAttributes ra) {
        community.disconnect(user,lenderId);
        ra.addFlashAttribute("successMessage","Lender connection deactivated. Your available lender slots were updated.");
        return "redirect:/community";
    }
    @PostMapping("/agreements/{id}/cancel") public String cancel(@AuthenticationPrincipal User user,@PathVariable Long id,
                                       RedirectAttributes ra) {
        community.cancelPendingAgreement(user,id);
        ra.addFlashAttribute("successMessage","Agreement cancelled. No demo loan was created.");
        return "redirect:/community";
    }
    @PostMapping("/agreements/{id}/confirm") public String confirm(@AuthenticationPrincipal User user,@PathVariable Long id,
                                         RedirectAttributes ra) {
        community.confirmDisbursement(user,id);ra.addFlashAttribute("successMessage","DEMO loan created. No real money was transferred.");
        return "redirect:/community";
    }
    @PostMapping("/reports") public String report(@AuthenticationPrincipal User user,
                                @RequestParam Long broadcastId,@RequestParam String reason,RedirectAttributes ra) {
        community.report(user,broadcastId,reason);ra.addFlashAttribute("successMessage","Report submitted for moderation.");
        return "redirect:/community";
    }
}
