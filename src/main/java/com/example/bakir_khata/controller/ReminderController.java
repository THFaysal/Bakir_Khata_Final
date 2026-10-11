package com.example.bakir_khata.controller;


import com.example.bakir_khata.model.User;
import com.example.bakir_khata.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ReminderController {

    private final LoanService loanService;

    @GetMapping("/reminders")
    public String reminders(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("dueToday", loanService.getDueToday(user));
        model.addAttribute("dueTomorrow", loanService.getDueTomorrow(user));
        model.addAttribute("overdue", loanService.getOverdueLoans(user));
        return "reminders/index";
    }
}
