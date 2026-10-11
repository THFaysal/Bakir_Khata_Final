package com.example.bakir_khata.controller;

import com.example.bakir_khata.dto.PaymentInitiationDTO;
import com.example.bakir_khata.dto.TransactionDTO;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Transaction;
import com.example.bakir_khata.model.PaymentAccount;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.TransactionMethod;
import com.example.bakir_khata.repository.PaymentAccountRepository;
import com.example.bakir_khata.repository.TransactionRepository;
import com.example.bakir_khata.service.FinancialPolicyService;
import com.example.bakir_khata.service.LoanService;
import com.example.bakir_khata.service.TransactionService;
import com.example.bakir_khata.service.SslCommerzSandboxGateway;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final TransactionService transactionService;
    private final LoanService loanService;
    private final PaymentAccountRepository paymentAccountRepository;
    private final FinancialPolicyService financialPolicyService;
    private final SslCommerzSandboxGateway sandboxGateway;
    private final TransactionRepository transactionRepository;

    @Value("${app.payments.mode:demo}") private String paymentMode;
    @Value("${spring.profiles.active:dev}") private String activeProfile;

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "loanId", required = false) Long loanId,
                          @AuthenticationPrincipal User user, Model model) {
        if (!model.containsAttribute("paymentDTO")) {
            model.addAttribute("paymentDTO",
                    new PaymentInitiationDTO(loanId, null, TransactionMethod.CASH, null, null, null, false));
        }
        addPaymentModel(user, model);
        return "payments/form";
    }

    @PostMapping("/start")
    public String start(@Valid @ModelAttribute("paymentDTO") PaymentInitiationDTO dto,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal User user,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (dto.loanId() != null && dto.amount() != null) {
            Loan loan = loanService.getLoanById(dto.loanId(), user);
            if (dto.amount().compareTo(loan.getRemainingAmount()) > 0) {
                bindingResult.rejectValue("amount", "exceeds",
                        "Payment cannot exceed the remaining principal of " + loan.getRemainingAmount());
            }
        }

        if (dto.method() == TransactionMethod.BANK) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Bank Payment - Under Construction. Bank ownership verification is not connected; sandbox merchant checkout does not verify an individual's bank account.");
            return "redirect:/payments/new" + (dto.loanId() == null ? "" : "?loanId=" + dto.loanId());
        }

        if (dto.method() != TransactionMethod.CASH && dto.paymentAccountId() == null) {
            bindingResult.rejectValue("paymentAccountId", "required",
                    "No lender payment destination is available for the selected method.");
        }

        if (bindingResult.hasErrors()) {
            addPaymentModel(user, model);
            return "payments/form";
        }

        TransactionDTO payment = transactionService.initiatePayment(dto, user);
        if (dto.method() == TransactionMethod.CASH) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cash payment request sent. The lender must accept it before the payment becomes successful.");
            return "redirect:/transactions";
        }

        if (payment.paymentProvider() != null && payment.paymentProvider().startsWith("DEMO - ")) {
            // bKash can use hosted sandbox, but Nagad/Rocket remain local test simulations.
            return "redirect:/payments/demo/" + payment.id();
        }

        Transaction transaction = transactionRepository.findById(payment.id())
                .orElseThrow(() -> new BusinessRuleException("Payment transaction was not saved."));
        try {
            String hostedPage = sandboxGateway.createHostedCheckout(transaction, user);
            return "redirect:" + hostedPage;
        } catch (RuntimeException ex) {
            transactionService.failSandboxInitiation(payment.id(), user);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sandbox checkout unavailable. No money was transferred. Check the sandbox configuration.");
            return "redirect:/transactions";
        }
    }

    @GetMapping("/demo/{id}")
    public String demoPayment(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        TransactionDTO tx = transactionService.getDemoPayment(id, user);
        model.addAttribute("demoTransaction", tx);
        return "payments/demo";
    }

    @PostMapping("/demo/{id}/complete")
    public String completeDemo(@PathVariable Long id, @RequestParam("outcome") String outcome,
                               @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        if (!"APPROVE".equals(outcome) && !"FAIL".equals(outcome)) {
            throw new BusinessRuleException("Choose a valid test outcome.");
        }
        TransactionDTO tx = transactionService.completeDemoPayment(id, user, "APPROVE".equals(outcome));
        redirectAttributes.addFlashAttribute(tx.status() == com.example.bakir_khata.model.enums.TransactionStatus.SUCCESS
                        ? "successMessage" : "errorMessage",
                tx.status() == com.example.bakir_khata.model.enums.TransactionStatus.SUCCESS
                        ? "DEMO payment succeeded. No real money was transferred."
                        : "DEMO payment failed. No loan balance was changed.");
        return "redirect:/transactions";
    }

    /** Public provider-to-server notification; never trust a callback without independent validation. */
    @PostMapping("/sslcommerz/ipn")
    @ResponseBody
    public ResponseEntity<String> sandboxIpn(@RequestParam(value = "tran_id", required = false) String reference,
                                             @RequestParam(value = "val_id", required = false) String validationId,
                                             @RequestParam(value = "status", required = false) String status) {
        if ("VALID".equalsIgnoreCase(status)) {
            transactionService.confirmSandboxCallback(reference, validationId);
        }
        return ResponseEntity.accepted().body("Sandbox notification received");
    }

    /** Hosted checkout may return without an app login; do not reveal transaction details. */
    @RequestMapping(value = "/sslcommerz/result", method = {RequestMethod.GET, RequestMethod.POST})
    public String sandboxResult(@RequestParam(value = "tran_id", required = false) String reference,
                                @RequestParam(value = "val_id", required = false) String validationId,
                                @RequestParam(value = "status", required = false) String status,
                                Model model) {
        if ("VALID".equalsIgnoreCase(status)) {
            transactionService.confirmSandboxCallback(reference, validationId);
        }
        model.addAttribute("sandboxMessage",
                "This was a SANDBOX checkout (no real money moved). Sign in to check the transaction status.");
        return "payments/sandbox-result";
    }

    private void addPaymentModel(User user, Model model) {
        List<Loan> loans = loanService.getAllLoans(user).stream()
                .filter(l -> l.getRemainingAmount().signum() > 0)
                .toList();

        for (Loan loan : loans) {
            loan.setOverdueDays(financialPolicyService.overdueDays(loan, LocalDate.now()));
            loan.setOverduePenalty(financialPolicyService.calculateOverduePenalty(loan, LocalDate.now()));
        }

        Set<Long> lenderUserIds = new HashSet<>();
        for (Loan loan : loans) {
            if (loan.getLender().getLinkedUser() != null) {
                lenderUserIds.add(loan.getLender().getLinkedUser().getId());
            }
        }

        List<PaymentAccount> accounts = lenderUserIds.stream()
                .flatMap(id -> paymentAccountRepository
                        .findByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(id)
                        .stream())
                .filter(a -> a.getType() == com.example.bakir_khata.model.enums.PaymentAccountType.MOBILE
                        && a.isPhoneControlVerified()
                        && (!a.isDemoPhoneProof() || ("demo".equalsIgnoreCase(paymentMode)
                                && !activeProfile.toLowerCase().contains("prod"))))
                .toList();

        model.addAttribute("loans", loans);
        model.addAttribute("paymentAccounts", accounts);
        model.addAttribute("serviceFeePercent", financialPolicyService.getServiceFeePercent());
        // A merchant checkout sandbox is not proof of a recipient's bank ownership.
        model.addAttribute("bankSandboxAvailable", false);
        model.addAttribute("sandboxMode", sandboxGateway.isEnabled());
    }
}
