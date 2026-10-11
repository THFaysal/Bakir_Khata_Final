package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.dto.PaymentDTO;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.PaymentRepository;
import com.example.bakir_khata.service.FileStorageService;
import com.example.bakir_khata.service.LoanService;
import com.example.bakir_khata.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final LoanService loanService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public Payment recordPayment(PaymentDTO dto, User user) {
        Loan loan = loanService.getLoanById(dto.loanId(), user);

        // Defense in depth: the controller already checks this for a nice inline form error,
        // but the service must never persist an impossible payment even if called another way.
        if (dto.paymentDate() != null) {
            if (dto.paymentDate().isBefore(loan.getBorrowDate())) {
                throw new BusinessRuleException(
                        "Payment date cannot be before the loan's issue date (" + loan.getBorrowDate() + ").");
            }
            if (dto.paymentDate().isAfter(LocalDate.now())) {
                throw new BusinessRuleException("Payment date cannot be in the future.");
            }
        }

        Payment payment = new Payment();
        payment.setLoan(loan);
        payment.setAmount(dto.amount());
        payment.setPaymentDate(dto.paymentDate());
        payment.setPaymentMethod(dto.paymentMethod());
        payment.setNotes(dto.notes());

        if (dto.proofFile() != null && !dto.proofFile().isEmpty()) {
            String path = fileStorageService.store(dto.proofFile(), "payments");
            payment.setProofFilePath(path);
        }

        // Snapshot the ledger balance on recording; it must not change on subsequent repayments.
        payment.setRemainingPrincipalBefore(loan.getRemainingAmount());
        payment.setBorrowerNameAtPayment(loan.getUser().getName());
        payment.setLenderNameAtPayment(loan.getLender().getName());
        // Deduct from the loan first so an over-payment is rejected before the payment row is persisted.
        loanService.applyPayment(loan, dto.amount());
        payment.setRemainingPrincipalAfter(loan.getRemainingAmount());

        return paymentRepository.save(payment);
    }

    @Override
    public List<Payment> getPaymentsForLoan(Long loanId, User user) {
        Loan loan = loanService.getLoanById(loanId, user);
        return paymentRepository.findByLoanOrderByPaymentDateDescCreatedAtDesc(loan);
    }
}