package com.example.bakir_khata.controller;


import com.example.bakir_khata.dto.LoanDTO;
import com.example.bakir_khata.receipts.ReceiptService;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.model.enums.Priority;
import com.example.bakir_khata.service.LenderService;
import com.example.bakir_khata.service.LoanService;
import com.example.bakir_khata.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final LenderService lenderService;
    private final PaymentService paymentService;
    private final ReceiptService receiptService;

    @GetMapping
    public String list(@AuthenticationPrincipal User user,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "status", required = false) LoanStatus status,
                       @RequestParam(value = "priority", required = false) Priority priority,
                       @RequestParam(value = "view", required = false, defaultValue = "active") String view,
                       Model model) {
        List<Loan> loans;
        if (q != null && !q.isBlank()) {
            loans = loanService.searchLoans(user, q);
        } else if (status != null) {
            loans = loanService.filterByStatus(user, status);
        } else if (priority != null) {
            loans = loanService.filterByPriority(user, priority);
        } else {
            loans = loanService.getAllLoans(user);
        }

        // Settled (fully paid) loans are never deleted — the payment trail feeds the borrower
        // rating — but they're kept out of the default "Active" view so the working list stays
        // clean. "History" shows only settled loans; "All" shows everything.
        if ("history".equalsIgnoreCase(view)) {
            loans = loans.stream().filter(l -> l.getStatus() == LoanStatus.PAID).toList();
        } else if (!"all".equalsIgnoreCase(view)) {
            loans = loans.stream().filter(l -> l.getStatus() != LoanStatus.PAID).toList();
        }

        model.addAttribute("communityLockedIds", loans.stream()
                .filter(loan -> receiptService.confirmedCommunityLoan(loan.getId(), user))
                .map(Loan::getId).toList());
        model.addAttribute("loans", loans);
        model.addAttribute("query", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("priorityFilter", priority);
        model.addAttribute("view", view);
        model.addAttribute("statuses", LoanStatus.values());
        model.addAttribute("priorities", Priority.values());
        return "loans/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal User user, Model model) {
        if (!model.containsAttribute("loanDTO")) {
            model.addAttribute("loanDTO", new LoanDTO());
        }
        model.addAttribute("lenders", lenderService.getAllLenders(user));
        return "loans/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal User user, Model model,
                           RedirectAttributes redirectAttributes) {
        Loan loan = loanService.getLoanById(id, user);

        if (receiptService.confirmedCommunityLoan(id, user)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Confirmed community loan terms are locked.");
            return "redirect:/loans/" + id;
        }
        if (loan.isFullyLocked()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This loan is fully settled and can no longer be edited.");
            return "redirect:/loans/" + id;
        }

        LoanDTO dto = new LoanDTO(
                loan.getId(),
                loan.getLender().getId(),
                loan.getAmount(),
                loan.getBorrowDate(),
                loan.getDueDate(),
                loan.getPurpose(),
                loan.getPriority(),
                loan.getTag(),
                loan.getPaymentType(),
                loan.getInstallmentCount(),
                loan.getExpectedInstallmentAmount(),
                loan.getNotes()
        );
        model.addAttribute("loanDTO", dto);
        model.addAttribute("lenders", lenderService.getAllLenders(user));
        model.addAttribute("loan", loan); // exposes loan.untouched / partiallyLocked / fullyLocked to the template
        return "loans/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("loanDTO") LoanDTO dto,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal User user,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("lenders", lenderService.getAllLenders(user));
            return "loans/form";
        }
        Loan loan = loanService.saveLoan(dto, user);
        redirectAttributes.addFlashAttribute("successMessage",
                dto.id() == null ? "Loan added successfully." : "Loan updated successfully.");
        return "redirect:/loans/" + loan.getId();
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        Loan loan = loanService.getLoanById(id, user);
        model.addAttribute("loan", loan);
        model.addAttribute("payments", paymentService.getPaymentsForLoan(id, user));
        model.addAttribute("settlementAvailable", receiptService.settlementAvailable(id, user));
        model.addAttribute("communityConfirmed", receiptService.confirmedCommunityLoan(id, user));
        return "loans/details";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal User user,
                         RedirectAttributes redirectAttributes) {
        loanService.deleteLoan(id, user);
        redirectAttributes.addFlashAttribute("successMessage", "Loan deleted.");
        return "redirect:/loans";
    }
}