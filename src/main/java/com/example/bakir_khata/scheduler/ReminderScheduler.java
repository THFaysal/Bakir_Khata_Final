package com.example.bakir_khata.scheduler;


import com.example.bakir_khata.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs every morning at 07:00 to flag any loan whose due date has passed as OVERDUE. */
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final LoanService loanService;

    @Scheduled(cron = "0 0 7 * * *")
    public void refreshOverdueLoans() {
        log.info("Running daily overdue-loan check...");
        loanService.refreshOverdueStatuses();
        log.info("Overdue-loan check complete.");
    }
}
