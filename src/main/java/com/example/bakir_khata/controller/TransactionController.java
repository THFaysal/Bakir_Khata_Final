package com.example.bakir_khata.controller;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String myTransactions(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("submitted", transactionService.getSubmittedByCurrentUser(user));
        model.addAttribute("pendingForMe", transactionService.getPendingForCurrentUser(user));
        return "transactions/list";
    }

    @GetMapping("/submit/{loanId}")
    @PreAuthorize("isAuthenticated()")
    public String submitForm(@PathVariable Long loanId) {
        return "redirect:/payments/new?loanId=" + loanId;
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    public String accept(@PathVariable Long id, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        transactionService.accept(id, user);
        redirectAttributes.addFlashAttribute("successMessage", "Cash payment confirmed. The loan balance and financial ledger were updated.");
        return "redirect:/transactions";
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public String reject(@PathVariable Long id, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        transactionService.reject(id, user);
        redirectAttributes.addFlashAttribute("successMessage", "Cash payment request rejected. No balance was changed.");
        return "redirect:/transactions";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        transactionService.cancel(id, user);
        redirectAttributes.addFlashAttribute("successMessage", "Payment request cancelled. No loan balance was changed.");
        return "redirect:/transactions";
    }

    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String reviewAll(Model model) {
        model.addAttribute("transactions", transactionService.getAllForReview());
        return "transactions/review-list";
    }

    @PostMapping("/{id}/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String flag(@PathVariable Long id, @RequestParam boolean flagged,
                       @RequestParam(required = false) String reviewNote,
                       @AuthenticationPrincipal User reviewer,
                       RedirectAttributes redirectAttributes) {
        transactionService.setReviewFlag(id, flagged, reviewNote, reviewer);
        redirectAttributes.addFlashAttribute("successMessage", flagged ? "Transaction flagged for review." : "Transaction review flag cleared.");
        return "redirect:/transactions/review";
    }
}
