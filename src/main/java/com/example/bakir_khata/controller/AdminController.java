package com.example.bakir_khata.controller;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.CoordinatorSalary;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.SalaryStatus;
import com.example.bakir_khata.repository.*;
import com.example.bakir_khata.service.CoordinatorService;
import com.example.bakir_khata.service.FinancialPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository userRepository;
    private final CoordinatorRepository coordinatorRepository;
    private final TransactionRepository transactionRepository;
    private final RevenueLedgerRepository revenueLedgerRepository;
    private final CoordinatorSalaryRepository salaryRepository;
    private final CoordinatorService coordinatorService;
    private final FinancialPolicyService financialPolicyService;

    @GetMapping
    public String dashboard(Model model) {
        BigDecimal grossRevenue = revenueLedgerRepository.findAll().stream().map(r -> r.getGrossRevenue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salaryExpense = salaryRepository.findByStatus(SalaryStatus.PAID).stream().map(CoordinatorSalary::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal transactionVolume = transactionRepository.findAllForReview().stream()
                .filter(t -> t.getStatus().name().equals("SUCCESS") || t.getStatus().name().equals("ACCEPTED"))
                .map(t -> t.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("coordinatorCount", coordinatorRepository.findAllForAdmin().stream().filter(c -> c.isActive()).count());
        model.addAttribute("transactionCount", transactionRepository.count());
        model.addAttribute("transactionVolume", transactionVolume);
        model.addAttribute("grossRevenue", grossRevenue);
        model.addAttribute("salaryExpense", salaryExpense);
        model.addAttribute("netProfit", grossRevenue.subtract(salaryExpense));
        model.addAttribute("serviceFeePercent", financialPolicyService.getServiceFeePercent());
        model.addAttribute("dailyPenaltyPercent", financialPolicyService.getDailyOverduePercent());
        model.addAttribute("maxPenaltyPercent", financialPolicyService.getMaxPenaltyPercent());
        model.addAttribute("platformPenaltySharePercent", financialPolicyService.getPlatformPenaltySharePercent());
        model.addAttribute("recentUsers", userRepository.findAllByOrderByCreatedAtDesc().stream().limit(8).toList());
        return "admin/dashboard";
    }

    @PostMapping("/users/{id}/eligibility")
    public String eligibility(@PathVariable Long id, @RequestParam boolean eligible,
                              @AuthenticationPrincipal User admin, RedirectAttributes redirectAttributes) {
        coordinatorService.setEligibility(id, eligible, admin);
        redirectAttributes.addFlashAttribute("successMessage", eligible ? "User can now apply to become a coordinator." : "Coordinator eligibility removed.");
        return "redirect:/management/users";
    }

    @PostMapping("/coordinators/{id}/revoke")
    public String revoke(@PathVariable Long id, @AuthenticationPrincipal User admin, RedirectAttributes redirectAttributes) {
        coordinatorService.revokeCoordinator(id, admin);
        redirectAttributes.addFlashAttribute("successMessage", "Coordinator access revoked; the account remains as a normal user.");
        return "redirect:/coordinator/list";
    }

    @PostMapping("/coordinators/{id}/salary")
    public String salary(@PathVariable Long id, @RequestParam BigDecimal amount, RedirectAttributes redirectAttributes) {
        coordinatorService.updateMonthlySalary(id, amount);
        redirectAttributes.addFlashAttribute("successMessage", "Coordinator monthly salary updated.");
        return "redirect:/coordinator/list";
    }

    @PostMapping("/coordinators/{id}/pay-salary")
    public String paySalary(@PathVariable Long id, @RequestParam(required = false) String month,
                            @AuthenticationPrincipal User admin, RedirectAttributes redirectAttributes) {
        var coordinator = coordinatorRepository.findById(id).orElseThrow(() -> new BusinessRuleException("Coordinator not found."));
        String salaryMonth = (month == null || month.isBlank()) ? YearMonth.now().toString() : month;
        CoordinatorSalary salary = salaryRepository.findByCoordinator_IdAndSalaryMonth(id, salaryMonth)
                .orElseGet(() -> CoordinatorSalary.builder().coordinator(coordinator).salaryMonth(salaryMonth).amount(coordinator.getMonthlySalary()).build());
        if (salary.getStatus() == SalaryStatus.PAID) throw new BusinessRuleException("Salary for " + salaryMonth + " is already marked paid.");
        salary.setAmount(coordinator.getMonthlySalary());
        salary.setStatus(SalaryStatus.PAID);
        salary.setPaidDate(LocalDate.now());
        salary.setPaidBy(admin);
        salaryRepository.save(salary);
        redirectAttributes.addFlashAttribute("successMessage", "Salary for " + salaryMonth + " marked as paid.");
        return "redirect:/coordinator/list";
    }
}
