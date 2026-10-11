package com.example.bakir_khata.controller;

import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.PaymentMethod;
import com.example.bakir_khata.model.enums.Role;
import com.example.bakir_khata.repository.PaymentRepository;
import com.example.bakir_khata.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final PaymentRepository paymentRepository;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal User user, Model model) {



        var summary = dashboardService.buildSummary(user);
        model.addAttribute("summary", summary);
        List<Payment> payments = paymentRepository.findRecentByUser(user);

        List<String> monthLabels = new ArrayList<>();
        List<BigDecimal> monthValues = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy");
        for (int i = 5; i >= 0; i--) {
            YearMonth month = YearMonth.now().minusMonths(i);
            monthLabels.add(month.format(fmt));
            BigDecimal total = payments.stream()
                    .filter(p -> YearMonth.from(p.getPaymentDate()).equals(month))
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            monthValues.add(total);
        }
        model.addAttribute("repaymentLabels", monthLabels);
        model.addAttribute("repaymentValues", monthValues);
        model.addAttribute("balanceLabels", List.of("Paid", "Remaining"));
        model.addAttribute("balanceValues", List.of(summary.totalPaid(), summary.remainingBalance()));
        model.addAttribute("methodLabels", List.of("Cash", "Bank", "Mobile"));
        model.addAttribute("methodValues", List.of(
                payments.stream().filter(p -> p.getPaymentMethod() == PaymentMethod.CASH).count(),
                payments.stream().filter(p -> p.getPaymentMethod() == PaymentMethod.BANK).count(),
                payments.stream().filter(p -> p.getPaymentMethod() == PaymentMethod.MOBILE_BANKING).count()));
        return "dashboard/index";
    }
}
