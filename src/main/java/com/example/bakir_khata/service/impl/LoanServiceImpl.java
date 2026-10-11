package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.dto.LoanDTO;
import com.example.bakir_khata.config.CacheInvalidationService;
import com.example.bakir_khata.community.LoanAgreementRepository;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.exception.ResourceNotFoundException;
import com.example.bakir_khata.model.Lender;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.model.enums.Priority;
import com.example.bakir_khata.repository.LenderRepository;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.service.FinancialPolicyService;
import com.example.bakir_khata.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private final CacheInvalidationService cacheInvalidation;
    private final LoanRepository loanRepository;
    private final LenderRepository lenderRepository;
    private final FinancialPolicyService financialPolicyService;
    private final LoanAgreementRepository loanAgreementRepository;

    @Override
    public Loan saveLoan(LoanDTO dto, User user) {

        if (!dto.isDueDateValid()) {
            throw new BusinessRuleException(
                    "Due date cannot be before borrow date."
            );
        }

        if (!dto.isInstallmentDataValid()) {
            throw new BusinessRuleException(
                    "Installment count and expected installment amount are required for installment loans."
            );
        }

        boolean isNew = dto.id() == null;

        // =========================
        // CREATE NEW LOAN
        // =========================
        if (isNew) {

            Lender lender = lenderRepository
                    .findByIdAndUser(dto.lenderId(), user)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Lender not found"
                            )
                    );

            Loan loan = new Loan();

            loan.setLender(lender);
            loan.setUser(user);

            loan.setAmount(dto.amount());
            loan.setRemainingAmount(dto.amount());

            loan.setOverduePenalty(BigDecimal.ZERO);
            loan.setOverdueDays(0);
            loan.setTotalPenaltyPaid(BigDecimal.ZERO);
            loan.setPenaltySettledThrough(null);

            loan.setBorrowDate(dto.borrowDate());
            loan.setDueDate(dto.dueDate());

            loan.setPurpose(dto.purpose());
            loan.setPriority(dto.priority());
            loan.setTag(dto.tag());

            loan.setPaymentType(dto.paymentType());
            loan.setInstallmentCount(dto.installmentCount());
            loan.setExpectedInstallmentAmount(
                    dto.expectedInstallmentAmount()
            );

            loan.setNotes(dto.notes());

            loan.setStatus(LoanStatus.PENDING);

            Loan saved = loanRepository.save(loan);
            cacheInvalidation.borrowerRatingChanged(user.getId());
            return saved;
        }

        // =========================
        // UPDATE EXISTING LOAN
        // =========================
        Loan loan = getLoanById(dto.id(), user);

        // Once both parties confirm a community agreement its loan terms become immutable.
        // A new PDF download must never reflect silently edited original terms.
        if (loanAgreementRepository.existsByLoan_Id(loan.getId())) {
            throw new BusinessRuleException("Confirmed community loans cannot be edited after disbursement.");
        }

        if (loan.isFullyLocked()) {
            throw new BusinessRuleException(
                    "This loan is fully settled and can no longer be edited."
            );
        }

        if (loan.isUntouched()) {

            loan.setAmount(dto.amount());
            loan.setDueDate(dto.dueDate());
            loan.setPurpose(dto.purpose());
            loan.setPriority(dto.priority());
            loan.setTag(dto.tag());

            loan.setPaymentType(dto.paymentType());
            loan.setInstallmentCount(dto.installmentCount());
            loan.setExpectedInstallmentAmount(
                    dto.expectedInstallmentAmount()
            );

            loan.setNotes(dto.notes());

            loan.setRemainingAmount(dto.amount());

            recalculateStatus(loan);

        } else {

            if (dto.amount().compareTo(loan.getAmount()) != 0) {
                throw new BusinessRuleException(
                        "Loan amount cannot be changed after a payment has been recorded."
                );
            }

            if (dto.paymentType() != loan.getPaymentType()
                    || !java.util.Objects.equals(
                    dto.installmentCount(),
                    loan.getInstallmentCount()
            )
                    || !java.util.Objects.equals(
                    dto.expectedInstallmentAmount(),
                    loan.getExpectedInstallmentAmount()
            )) {

                throw new BusinessRuleException(
                        "Installment plan cannot be changed after a payment has been recorded."
                );
            }

            loan.setDueDate(dto.dueDate());
            loan.setPurpose(dto.purpose());
            loan.setPriority(dto.priority());
            loan.setTag(dto.tag());
            loan.setNotes(dto.notes());

            recalculateStatus(loan);
        }

        Loan saved = loanRepository.save(loan);
        cacheInvalidation.borrowerRatingChanged(user.getId());
        return saved;
    }

    @Override
    public List<Loan> getAllLoans(User user) {
        return loanRepository.findByUserOrderByDueDateAsc(user);
    }

    @Override
    public List<Loan> searchLoans(User user, String term) {

        if (!StringUtils.hasText(term)) {
            return getAllLoans(user);
        }

        return loanRepository.search(
                user,
                term.trim()
        );
    }

    @Override
    public List<Loan> filterByStatus(
            User user,
            LoanStatus status
    ) {
        return loanRepository
                .findByUserAndStatusOrderByDueDateAsc(
                        user,
                        status
                );
    }

    @Override
    public List<Loan> filterByPriority(
            User user,
            Priority priority
    ) {
        return loanRepository
                .findByUserAndPriorityOrderByDueDateAsc(
                        user,
                        priority
                );
    }

    @Override
    public List<Loan> getDueToday(User user) {

        return loanRepository
                .findByUserAndDueDateOrderByDueDateAsc(
                        user,
                        LocalDate.now()
                );
    }

    @Override
    public List<Loan> getDueTomorrow(User user) {

        return loanRepository
                .findByUserAndDueDateOrderByDueDateAsc(
                        user,
                        LocalDate.now().plusDays(1)
                );
    }

    @Override
    public List<Loan> getDueThisWeek(User user) {

        LocalDate today = LocalDate.now();

        return loanRepository
                .findByUserAndDueDateBetweenOrderByDueDateAsc(
                        user,
                        today,
                        today.plusDays(7)
                );
    }

    @Override
    public List<Loan> getOverdueLoans(User user) {

        return loanRepository
                .findByUserAndStatusOrderByDueDateAsc(
                        user,
                        LoanStatus.OVERDUE
                );
    }


    @Override
    public Loan getLoanById(Long id, User user) {

        return loanRepository
                .findByIdAndUser(id, user)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Loan not found"
                        )
                );
    }

    @Override
    public void deleteLoan(Long id, User user) {

        Loan loan = getLoanById(id, user);

        if (loanAgreementRepository.existsByLoan_Id(id)) {
            throw new BusinessRuleException("A confirmed community agreement cannot be deleted from the ledger.");
        }
        if (!loan.isDeletable()) {
            throw new BusinessRuleException(
                    "This loan cannot be deleted because payments have already been recorded against it."
            );
        }

        loanRepository.delete(loan);
        cacheInvalidation.borrowerRatingChanged(user.getId());
    }

    @Override
    public void refreshOverdueStatuses() {

        LocalDate today = LocalDate.now();

        List<Loan> candidates =
                loanRepository.findAllUnpaidForScheduler();

        for (Loan loan : candidates) {

            if (loan.getDueDate().isBefore(today)
                    && loan.getRemainingAmount()
                    .compareTo(BigDecimal.ZERO) > 0) {

                loan.setStatus(LoanStatus.OVERDUE);

                loan.setOverdueDays(
                        financialPolicyService
                                .overdueDays(
                                        loan,
                                        today
                                )
                );

                loan.setOverduePenalty(
                        financialPolicyService
                                .calculateOverduePenalty(
                                        loan,
                                        today
                                )
                );

                loanRepository.save(loan);

            } else {

                loan.setOverdueDays(0);
                loan.setOverduePenalty(BigDecimal.ZERO);

                loanRepository.save(loan);
            }
        }
        // Overnight status changes affect many borrower ratings.
        cacheInvalidation.allBorrowerRatingsChanged();
    }

    @Override
    public void applyPayment(
            Loan loan,
            BigDecimal paymentAmount
    ) {

        BigDecimal newRemaining =
                loan.getRemainingAmount()
                        .subtract(paymentAmount);

        if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(
                    "Payment cannot exceed the remaining balance."
            );
        }

        loan.setRemainingAmount(newRemaining);

        recalculateStatus(loan);

        loanRepository.save(loan);
        cacheInvalidation.borrowerRatingChanged(loan.getUser().getId());
    }

    private void recalculateStatus(Loan loan) {

        if (loan.getRemainingAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            loan.setStatus(LoanStatus.PAID);
            loan.setRemainingAmount(BigDecimal.ZERO);

        } else if (loan.getDueDate()
                .isBefore(LocalDate.now())) {

            loan.setStatus(LoanStatus.OVERDUE);

        } else if (loan.getRemainingAmount()
                .compareTo(loan.getAmount()) < 0) {

            loan.setStatus(
                    LoanStatus.PARTIALLY_PAID
            );

        } else {

            loan.setStatus(LoanStatus.PENDING);
        }
    }
}