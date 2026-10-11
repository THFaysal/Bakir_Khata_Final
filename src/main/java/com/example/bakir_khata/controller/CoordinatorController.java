package com.example.bakir_khata.controller;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.CoordinatorSalary;
import com.example.bakir_khata.model.enums.SalaryStatus;
import com.example.bakir_khata.model.enums.TransactionStatus;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.repository.TransactionRepository;
import com.example.bakir_khata.repository.UserRepository;
import com.example.bakir_khata.repository.RevenueLedgerRepository;
import com.example.bakir_khata.repository.CoordinatorSalaryRepository;
import com.example.bakir_khata.service.CoordinatorService;
import com.example.bakir_khata.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/coordinator")
public class CoordinatorController {
    private final CoordinatorService coordinatorService;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final RevenueLedgerRepository revenueLedgerRepository;
    private final CoordinatorSalaryRepository coordinatorSalaryRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    public String dashboard(Model model) {
        var transactions = transactionRepository.findAllForReview();
        model.addAttribute("transactionCount", transactions.size());
        model.addAttribute("pendingCount", transactions.stream().filter(t -> t.getStatus() == TransactionStatus.AWAITING_LENDER_CONFIRMATION || t.getStatus() == TransactionStatus.OTP_REQUIRED).count());
        model.addAttribute("failedCount", transactions.stream().filter(t -> t.getStatus() == TransactionStatus.FAILED || t.getStatus() == TransactionStatus.REJECTED).count());
        model.addAttribute("overdueCount", loanRepository.findAllUnpaidForScheduler().stream().filter(l -> l.getDueDate().isBefore(LocalDate.now())).count());
        model.addAttribute("activeUsers", userRepository.findAll().stream().filter(User::isEnabled).count());
        BigDecimal grossRevenue = revenueLedgerRepository.findAll().stream()
                .map(r -> r.getGrossRevenue() == null ? BigDecimal.ZERO : r.getGrossRevenue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidSalaryExpense = coordinatorSalaryRepository.findByStatus(SalaryStatus.PAID).stream()
                .map(CoordinatorSalary::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("grossRevenue", grossRevenue);
        model.addAttribute("netProfit", grossRevenue.subtract(paidSalaryExpense));
        model.addAttribute("flaggedCount", transactions.stream().filter(t -> t.isFlagged()).count());
        model.addAttribute("transactions", transactionService.getAllForReview().stream().limit(8).toList());

        List<String> days = new ArrayList<>();
        List<BigDecimal> volumes = new ArrayList<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            days.add(day.format(df));
            BigDecimal total = transactions.stream().filter(t -> t.getCreatedAt().toLocalDate().equals(day) && (t.getStatus() == TransactionStatus.SUCCESS || t.getStatus() == TransactionStatus.ACCEPTED))
                    .map(t -> t.getAmount() == null ? BigDecimal.ZERO : t.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            volumes.add(total);
        }
        model.addAttribute("volumeLabels", days);
        model.addAttribute("volumeValues", volumes);
        model.addAttribute("statusLabels", List.of("Success", "Awaiting", "OTP", "Rejected/Failed"));
        model.addAttribute("statusValues", List.of(
                transactions.stream().filter(t -> (t.getStatus() == TransactionStatus.SUCCESS || t.getStatus() == TransactionStatus.ACCEPTED)).count(),
                transactions.stream().filter(t -> t.getStatus() == TransactionStatus.AWAITING_LENDER_CONFIRMATION).count(),
                transactions.stream().filter(t -> t.getStatus() == TransactionStatus.OTP_REQUIRED || t.getStatus() == TransactionStatus.PROCESSING).count(),
                transactions.stream().filter(t -> t.getStatus() == TransactionStatus.REJECTED || t.getStatus() == TransactionStatus.FAILED).count()));
        model.addAttribute("methodLabels", List.of("Cash", "Bank", "Mobile"));
        model.addAttribute("methodValues", List.of(
                transactions.stream().filter(t -> t.getMethod().name().equals("CASH")).count(),
                transactions.stream().filter(t -> t.getMethod().name().equals("BANK")).count(),
                transactions.stream().filter(t -> t.getMethod().name().equals("MOBILE_BANKING")).count()));
        return "coordinator/dashboard";
    }

    @GetMapping("/apply")
    @PreAuthorize("hasRole('USER')")
    public String applyForm(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("eligible", user.isCoordinatorEligible());
        model.addAttribute("alreadyCoordinator", coordinatorService.isCurrentlyCoordinator(user));
        model.addAttribute("hasPending", coordinatorService.hasPendingApplication(user));
        return "coordinator/apply";
    }

    @PostMapping("/apply")
    @PreAuthorize("hasRole('USER')")
    public String apply(@RequestParam String reason, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        coordinatorService.apply(user, reason);
        redirectAttributes.addFlashAttribute("successMessage", "Coordinator application submitted for admin review.");
        return "redirect:/coordinator/apply";
    }

    @GetMapping("/applications") @PreAuthorize("hasRole('ADMIN')")
    public String listApplications(Model model) {
        model.addAttribute("applications", coordinatorService.listApplications());
        return "coordinator/applications";
    }
    @PostMapping("/applications/{id}/approve") @PreAuthorize("hasRole('ADMIN')")
    public String approve(@PathVariable Long id, @AuthenticationPrincipal User admin) { coordinatorService.approve(id, admin); return "redirect:/coordinator/applications?approved=true"; }
    @PostMapping("/applications/{id}/reject") @PreAuthorize("hasRole('ADMIN')")
    public String reject(@PathVariable Long id, @AuthenticationPrincipal User admin) { coordinatorService.reject(id, admin); return "redirect:/coordinator/applications?rejected=true"; }
    @GetMapping("/list") @PreAuthorize("hasRole('ADMIN')")
    public String listCoordinators(Model model) { model.addAttribute("coordinators", coordinatorService.listCoordinators()); return "coordinator/list"; }
}
