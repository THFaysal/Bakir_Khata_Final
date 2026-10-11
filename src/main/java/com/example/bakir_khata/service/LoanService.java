package com.example.bakir_khata.service;



import com.example.bakir_khata.dto.LoanDTO;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.model.enums.Priority;

import java.math.BigDecimal;
import java.util.List;

public interface LoanService {
    Loan saveLoan(LoanDTO dto, User user);
    /** Deducts a payment amount from the loan's remaining balance and recalculates its status. */
    void applyPayment(Loan loan, BigDecimal paymentAmount);
    List<Loan> getAllLoans(User user);
    List<Loan> searchLoans(User user, String term);
    List<Loan> filterByStatus(User user, LoanStatus status);
    List<Loan> filterByPriority(User user, Priority priority);
    List<Loan> getDueToday(User user);
    List<Loan> getDueTomorrow(User user);
    List<Loan> getDueThisWeek(User user);
    List<Loan> getOverdueLoans(User user);
    Loan getLoanById(Long id, User user);
    void deleteLoan(Long id, User user);
    void refreshOverdueStatuses();
}
