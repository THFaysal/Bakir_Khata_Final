package com.example.bakir_khata.controller;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.MobileProvider;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.service.PaymentAccountService;
import com.example.bakir_khata.service.RatingService;
import com.example.bakir_khata.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final UserService userService;
    private final RatingService ratingService;
    private final LoanRepository loanRepository;
    private final PaymentAccountService paymentAccountService;

    @GetMapping
    public String profile(@AuthenticationPrincipal User user, Model model) {
        List<Loan> loans = loanRepository.findByUserOrderByDueDateAsc(user);
        BigDecimal totalBorrowed = loans.stream().map(Loan::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = loans.stream().map(Loan::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = totalBorrowed.subtract(remaining);

        model.addAttribute("rating", ratingService.calculateRating(user));
        model.addAttribute("totalBorrowed", totalBorrowed);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("remaining", remaining);
        model.addAttribute("hasDue", remaining.compareTo(BigDecimal.ZERO) > 0);
        model.addAttribute("loanCount", loans.size());
        model.addAttribute("paymentAccounts", paymentAccountService.listForUser(user));
        model.addAttribute("mobileProviders", List.of(MobileProvider.BKASH, MobileProvider.NAGAD, MobileProvider.ROCKET));
        return "profile/index";
    }

    @PostMapping("/picture")
    public String updatePicture(@AuthenticationPrincipal User user,
                                @RequestParam("profileImage") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        userService.updateProfilePicture(user, file);
        redirectAttributes.addFlashAttribute("successMessage", "Profile picture updated.");
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/bank")
    public String addBank(@AuthenticationPrincipal User user,
                          @RequestParam String bankName,
                          @RequestParam String accountHolderName,
                          @RequestParam String accountNumber,
                          @RequestParam(required = false) String branchName,
                          @RequestParam(required = false) String routingNumber,
                          RedirectAttributes redirectAttributes) {
        paymentAccountService.addBank(user, bankName, accountHolderName, accountNumber, branchName, routingNumber);
        redirectAttributes.addFlashAttribute("successMessage", "Bank details saved as UNVERIFIED. Bank ownership verification is under construction.");
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/mobile")
    public String addMobile(@AuthenticationPrincipal User user,
                            @RequestParam String provider,
                            @RequestParam String mobileNumber,
                            RedirectAttributes redirectAttributes) {
        paymentAccountService.addMobile(user, parseMobileProvider(provider), mobileNumber);
        redirectAttributes.addFlashAttribute("successMessage", "Mobile number saved but NOT verified. Send an OTP to prove control of this number.");
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/{id}/mobile/update")
    public String updateMobile(@PathVariable Long id,
                               @AuthenticationPrincipal User user,
                               @RequestParam String provider,
                               @RequestParam String mobileNumber,
                               RedirectAttributes redirectAttributes) {
        paymentAccountService.updateMobile(id, user, parseMobileProvider(provider), mobileNumber);
        redirectAttributes.addFlashAttribute("successMessage", "Destination updated. If its provider or number changed, OTP verification is required again.");
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/{id}/bank/update")
    public String updateBank(@PathVariable Long id, @AuthenticationPrincipal User user,
                             @RequestParam String bankName, @RequestParam String accountHolderName,
                             @RequestParam String accountNumber,
                             @RequestParam(required = false) String branchName,
                             @RequestParam(required = false) String routingNumber,
                             RedirectAttributes flash) {
        paymentAccountService.updateBank(id, user, bankName, accountHolderName, accountNumber, branchName, routingNumber);
        flash.addFlashAttribute("successMessage", "Bank information updated. Verification is still unavailable.");
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/{id}/otp/send")
    public String sendMobileAccountOtp(@PathVariable Long id, @AuthenticationPrincipal User user,
                                       RedirectAttributes flash) {
        var delivery = paymentAccountService.sendMobileVerification(id, user);
        String message = "OTP requested for " + delivery.maskedPhone() + ". ";
        if ("DEMO".equals(delivery.mode()) && delivery.demoCode() != null) {
            message += "DEVELOPMENT SIMULATION ONLY. Your test OTP is shown under the corresponding payment destination.";
            flash.addFlashAttribute("accountDevOtp", delivery.demoCode());
            flash.addFlashAttribute("accountDevOtpId", id);
        } else {
            message += "Check your SMS. This checks phone control, not wallet registration.";
        }
        flash.addFlashAttribute("successMessage", message);
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/{id}/otp/confirm")
    public String confirmMobileAccountOtp(@PathVariable Long id, @AuthenticationPrincipal User user,
                                          @RequestParam String code, RedirectAttributes flash) {
        boolean approved = paymentAccountService.confirmMobileVerification(id, user, code);
        flash.addFlashAttribute(approved ? "successMessage" : "errorMessage",
                approved ? "Phone OTP confirmed. Wallet registration with the provider remains UNVERIFIED."
                        : "Incorrect OTP. Check the code and try again before it expires.");
        return "redirect:/profile";
    }

    @PostMapping("/payment-accounts/{id}/remove")
    public String removeAccount(@PathVariable Long id,
                                @AuthenticationPrincipal User user,
                                RedirectAttributes redirectAttributes) {
        paymentAccountService.deactivate(id, user);
        redirectAttributes.addFlashAttribute("successMessage", "Payment destination removed.");
        return "redirect:/profile";
    }

    private MobileProvider parseMobileProvider(String raw) {
        try {
            return MobileProvider.valueOf(raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Choose a valid mobile provider: bKash, Nagad or Rocket.");
        }
    }

    @PostMapping("/delete")
    public String deleteAccount(@AuthenticationPrincipal User user,
                                RedirectAttributes redirectAttributes) {
        userService.deleteAccount(user);
        redirectAttributes.addFlashAttribute("successMessage",
                "Your account has been disabled. Financial history was preserved.");
        return "redirect:/login?logout=true";
    }
}
