package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.dto.PaymentInitiationDTO;
import com.example.bakir_khata.dto.TransactionDTO;
import com.example.bakir_khata.dto.TransactionSubmitDTO;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.exception.CounterpartyNotRegisteredException;
import com.example.bakir_khata.exception.InvalidTransactionException;
import com.example.bakir_khata.exception.UnauthorizedActionException;
import com.example.bakir_khata.model.*;
import com.example.bakir_khata.model.enums.*;
import com.example.bakir_khata.repository.*;
import com.example.bakir_khata.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final RevenueLedgerRepository revenueLedgerRepository;
    private final NotificationService notificationService;
    private final FinancialPolicyService financialPolicyService;
    private final LoanService loanService;
    private final SimulatedPaymentGateway simulatedPaymentGateway;
    private final SslCommerzSandboxGateway sandboxGateway;

    @Value("${app.payments.mode:demo}") private String paymentMode;
    @Value("${spring.profiles.active:dev}") private String activeProfile;

    @Override
    @Transactional
    public TransactionDTO initiatePayment(PaymentInitiationDTO dto, User borrower) {
        if (!dto.holdConfirmed()) {
            throw new BusinessRuleException("Hold the payment button for 3 seconds to confirm the transaction.");
        }
        Loan loan = ownedLoan(dto.loanId(), borrower);
        validateAmount(loan, dto.amount());
        User counterparty = requireLinkedLender(loan, borrower);
        List<TransactionStatus> activeStatuses = List.of(
                TransactionStatus.PENDING,
                TransactionStatus.PROCESSING,
                TransactionStatus.AWAITING_LENDER_CONFIRMATION);
        if (transactionRepository.existsByLoan_IdAndInitiatedBy_IdAndStatusIn(loan.getId(), borrower.getId(), activeStatuses)) {
            throw new BusinessRuleException("This loan already has a payment waiting for confirmation or verification. Finish or reject it before starting another.");
        }

        PaymentAccount destination = null;
        if (dto.method() != TransactionMethod.CASH) {
            destination = validatePaymentAccount(dto.paymentAccountId(), counterparty, dto.method());
            // This is a merchant checkout sandbox, NOT a real transfer to the lender.
            if (dto.method() == TransactionMethod.BANK) {
                throw new BusinessRuleException("Bank Payment - Under Construction. No verified bank ownership API is configured.");
            }
        }

        BigDecimal serviceFee = financialPolicyService.calculateServiceFee(dto.amount());
        BigDecimal penalty = financialPolicyService.calculateOverduePenalty(loan, LocalDate.now());
        loan.setOverduePenalty(penalty);
        loan.setOverdueDays(financialPolicyService.overdueDays(loan, LocalDate.now()));
        loanRepository.save(loan);
        BigDecimal platformPenalty = financialPolicyService.platformPenaltyShare(penalty);
        BigDecimal lenderPenalty = financialPolicyService.lenderPenaltyShare(penalty);
        BigDecimal totalPayable = money(dto.amount().add(serviceFee).add(penalty));

        Transaction.TransactionBuilder builder = Transaction.builder()
                .loan(loan)
                .initiatedBy(borrower)
                .counterpartyUser(counterparty)
                .amount(money(dto.amount()))
                .serviceFee(serviceFee)
                .overduePenaltyAmount(penalty)
                .penaltyPlatformShare(platformPenalty)
                .penaltyLenderShare(lenderPenalty)
                .totalPayable(totalPayable)
                .method(dto.method())
                .note(dto.note());

        if (dto.method() == TransactionMethod.CASH) {
            builder.status(TransactionStatus.AWAITING_LENDER_CONFIRMATION)
                    .paymentProvider("Cash")
                    .recipientMasked(counterparty.getName());
        } else {
            boolean hostedSandbox = sandboxGateway.supportsHostedCheckout(dto.method(), destination.getMobileProvider());
            String provider = providerName(destination);
            if (hostedSandbox && (totalPayable.compareTo(new BigDecimal("10.00")) < 0
                    || totalPayable.compareTo(new BigDecimal("500000.00")) > 0)) {
                throw new BusinessRuleException("Sandbox checkout total must be between ৳10 and ৳500,000.");
            }
            builder.status(hostedSandbox ? TransactionStatus.PROCESSING : TransactionStatus.PENDING)
                    .paymentProvider((hostedSandbox ? "SSLCOMMERZ SANDBOX - " : "DEMO - ") + provider)
                    .paymentAccount(destination)
                    .mobileProvider(destination.getMobileProvider())
                    .recipientMasked(destination.getMaskedDestination())
                    .transactionReferenceId("BK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase());
        }

        Transaction transaction = transactionRepository.save(builder.build());

        if (dto.method() == TransactionMethod.CASH) {
            notificationService.notify(counterparty, "CASH_PAYMENT_REQUEST",
                    borrower.getName() + " says they paid ৳" + transaction.getAmount() + " in cash for loan #" + loan.getId() + ". Please accept or reject it.",
                    transaction.getId());
        }

        return toDTO(transaction);
    }
    
    @Override
    @Transactional
    public TransactionDTO submit(TransactionSubmitDTO dto, User borrower) {
        PaymentInitiationDTO modern =
                new PaymentInitiationDTO(
                        dto.loanId(),
                        dto.amount(),
                        dto.method(),
                        null,
                        null,
                        dto.note(),
                        true
                );

        return initiatePayment(modern, borrower);
    }
    @Override
    @Transactional(readOnly = true)
    public TransactionDTO getDemoPayment(Long transactionId, User borrower) {
        Transaction tx = loadOwnedByInitiator(transactionId, borrower);
        if (tx.getMethod() == TransactionMethod.CASH || tx.getPaymentProvider() == null
                || !tx.getPaymentProvider().startsWith("DEMO - ")) {
            throw new BusinessRuleException("This transaction is not a local digital-payment demo.");
        }
        return toDTO(tx);
    }

    @Override
    @Transactional
    public TransactionDTO completeDemoPayment(Long transactionId, User borrower, boolean approved) {
        Transaction tx = loadOwnedByInitiator(transactionId, borrower);
        if (tx.getStatus() != TransactionStatus.PENDING || tx.getMethod() != TransactionMethod.MOBILE_BANKING
                || tx.getPaymentProvider() == null || !tx.getPaymentProvider().startsWith("DEMO - ")) {
            throw new BusinessRuleException("This demo transaction has already been completed or is unavailable.");
        }
        if (approved) {
            SimulatedPaymentGateway.GatewayResult result = simulatedPaymentGateway.process(tx);
            if (!result.success()) throw new BusinessRuleException("Demo gateway did not approve this payment.");
            tx.setGatewayReference(result.reference());
            return finalizeSuccessfulTransaction(tx, false);
        }
        tx.setStatus(TransactionStatus.FAILED);
        tx.setRespondedAt(LocalDateTime.now());
        transactionRepository.save(tx);
        return toDTO(tx);
    }

    @Override
    @Transactional
    public void confirmSandboxCallback(String reference, String validationId) {
        if (reference == null || validationId == null || !sandboxGateway.isEnabled()) return;
        Transaction tx = transactionRepository.findByTransactionReferenceId(reference).orElse(null);
        if (tx == null || tx.getMethod() == TransactionMethod.CASH
                || tx.getPaymentProvider() == null || !tx.getPaymentProvider().startsWith("SSLCOMMERZ SANDBOX")) return;
        if (tx.getStatus() == TransactionStatus.SUCCESS) return; // idempotent notification
        if (tx.getStatus() != TransactionStatus.PROCESSING) return;
        if (!sandboxGateway.verifyPayment(validationId, tx.getTransactionReferenceId(), tx.getTotalPayable(), tx.getMethod())) {
            return; // An unverified or forged callback MUST NOT reduce the loan balance.
        }
        tx.setGatewayReference(validationId);
        finalizeSuccessfulTransaction(tx, false);
    }

    @Override
    @Transactional
    public void failSandboxInitiation(Long id, User borrower) {
        Transaction tx = loadOwnedByInitiator(id, borrower);
        if (tx.getStatus() == TransactionStatus.PROCESSING && tx.getPaymentProvider() != null
                && tx.getPaymentProvider().startsWith("SSLCOMMERZ SANDBOX")) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setRespondedAt(LocalDateTime.now());
            transactionRepository.save(tx);
        }
    }

    @Override
    @Transactional
    public TransactionDTO accept(Long transactionId, User lenderUser) {
        Transaction transaction = loadOwnedByCounterparty(transactionId, lenderUser);
        if (transaction.getStatus() != TransactionStatus.AWAITING_LENDER_CONFIRMATION
                && transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidTransactionException("This cash payment has already been handled.");
        }
        if (transaction.getMethod() != TransactionMethod.CASH) {
            throw new InvalidTransactionException("Only cash payments require lender confirmation.");
        }
        return finalizeSuccessfulTransaction(transaction, true);
    }

    @Override
    @Transactional
    public TransactionDTO reject(Long transactionId, User lenderUser) {
        Transaction transaction = loadOwnedByCounterparty(transactionId, lenderUser);
        if (transaction.getStatus() != TransactionStatus.AWAITING_LENDER_CONFIRMATION
                && transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidTransactionException("This transaction has already been handled.");
        }
        transaction.setStatus(TransactionStatus.REJECTED);
        transaction.setRespondedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        notificationService.notify(transaction.getInitiatedBy(), "TRANSACTION_REJECTED",
                "Your cash payment request of ৳" + transaction.getAmount() + " for loan #" + transaction.getLoan().getId() + " was rejected.",
                transaction.getId());
        return toDTO(transaction);
    }

    @Override
    @Transactional
    public TransactionDTO cancel(Long transactionId, User borrower) {
        Transaction transaction = loadOwnedByInitiator(transactionId, borrower);
        if (transaction.getStatus() == TransactionStatus.PROCESSING
                && transaction.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(15))) {
            throw new BusinessRuleException("Sandbox checkout is still processing. You can cancel it after 15 minutes if it remains unverified.");
        }
        if (transaction.getStatus() != TransactionStatus.OTP_REQUIRED
                && transaction.getStatus() != TransactionStatus.AWAITING_LENDER_CONFIRMATION
                && transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.PROCESSING) {
            throw new InvalidTransactionException("Only a payment still waiting for processing or confirmation can be cancelled.");
        }
        transaction.setStatus(TransactionStatus.CANCELLED);
        transaction.setRespondedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        if (transaction.getCounterpartyUser() != null) {
            notificationService.notify(transaction.getCounterpartyUser(), "PAYMENT_CANCELLED",
                    borrower.getName() + " cancelled payment request #" + transaction.getId() + ".", transaction.getId());
        }
        return toDTO(transaction);
    }

    @Override
    public List<TransactionDTO> getPendingForCurrentUser(User lenderUser) {
        return transactionRepository.findForCounterpartyByStatuses(lenderUser.getId(),
                        List.of(TransactionStatus.AWAITING_LENDER_CONFIRMATION, TransactionStatus.PENDING))
                .stream().filter(t -> t.getMethod() == TransactionMethod.CASH)
                .map(this::toDTO).toList();
    }

    @Override
    public List<TransactionDTO> getSubmittedByCurrentUser(User borrower) {
        return transactionRepository.findAllInitiatedBy(borrower.getId()).stream().map(this::toDTO).toList();
    }

    @Override
    public List<TransactionDTO> getAllForReview() {
        return transactionRepository.findAllForReview().stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public void setReviewFlag(Long transactionId, boolean flagged, String reviewNote, User reviewer) {
        if (reviewer.getRole() != Role.ADMIN && reviewer.getRole() != Role.COORDINATOR) {
            throw new UnauthorizedActionException("Only admin or coordinator can review system transactions.");
        }
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new InvalidTransactionException("Transaction not found."));
        transaction.setFlagged(flagged);
        transaction.setReviewNote(reviewNote == null || reviewNote.isBlank() ? null : reviewNote.trim());
        transaction.setReviewedBy(reviewer);
        transaction.setReviewedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    private TransactionDTO finalizeSuccessfulTransaction(Transaction transaction, boolean lenderConfirmed) {
        Loan loan = transaction.getLoan();
        if (transaction.getAmount().compareTo(loan.getRemainingAmount()) > 0) {
            throw new BusinessRuleException("The loan balance changed and this payment now exceeds the remaining principal.");
        }

        Payment payment = Payment.builder()
                .loan(loan)
                .amount(transaction.getAmount())
                .serviceFee(transaction.getServiceFee())
                .penaltyAmount(transaction.getOverduePenaltyAmount())
                .totalCharged(transaction.getTotalPayable())
                .remainingPrincipalBefore(loan.getRemainingAmount())
                .remainingPrincipalAfter(loan.getRemainingAmount().subtract(transaction.getAmount()))
                .borrowerNameAtPayment(transaction.getInitiatedBy().getName())
                .lenderNameAtPayment(transaction.getCounterpartyUser().getName())
                .paymentDate(LocalDate.now())
                .paymentMethod(PaymentMethod.valueOf(transaction.getMethod().name()))
                .notes(transaction.getNote())
                .build();

        loanService.applyPayment(loan, transaction.getAmount());
        BigDecimal previouslyPaidPenalty = loan.getTotalPenaltyPaid() == null ? BigDecimal.ZERO : loan.getTotalPenaltyPaid();
        loan.setTotalPenaltyPaid(money(previouslyPaidPenalty.add(transaction.getOverduePenaltyAmount())));
        if (transaction.getOverduePenaltyAmount() != null && transaction.getOverduePenaltyAmount().signum() > 0) {
            loan.setPenaltySettledThrough(transaction.getCreatedAt().toLocalDate());
        }
        loan.setOverdueDays(financialPolicyService.overdueDays(loan, LocalDate.now()));
        loan.setOverduePenalty(financialPolicyService.calculateOverduePenalty(loan, LocalDate.now()));
        loanRepository.save(loan);
        payment = paymentRepository.save(payment);

        transaction.setPayment(payment);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setRespondedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        if (!revenueLedgerRepository.existsByTransaction_Id(transaction.getId())) {
            revenueLedgerRepository.save(RevenueLedger.builder()
                    .transaction(transaction)
                    .serviceFeeRevenue(transaction.getServiceFee())
                    .penaltyRevenue(transaction.getPenaltyPlatformShare())
                    .grossRevenue(transaction.getServiceFee().add(transaction.getPenaltyPlatformShare()))
                    .build());
        }

        String verification = lenderConfirmed ? "confirmed by the lender"
                : "simulated in a test environment; no real money was transferred";
        notificationService.notify(transaction.getInitiatedBy(), "PAYMENT_SUCCESS",
                "Payment of ৳" + transaction.getAmount() + " for loan #" + loan.getId() + " succeeded (" + verification + "). Download its PDF under My Receipts.",
                transaction.getId());
        notificationService.notify(transaction.getCounterpartyUser(), "PAYMENT_RECEIVED",
                "A payment of ৳" + transaction.getAmount() + " for loan #" + loan.getId() + " was completed. Your PDF receipt is under My Receipts.",
                transaction.getId());
        if (loan.getStatus() == LoanStatus.PAID && loan.getTotalOutstanding().signum() == 0) {
            notificationService.notify(transaction.getInitiatedBy(), "LOAN_SETTLED",
                    "Loan #" + loan.getId() + " is fully settled. Download your settlement certificate under My Receipts.", loan.getId());
            notificationService.notify(transaction.getCounterpartyUser(), "LOAN_SETTLED",
                    "Loan #" + loan.getId() + " is fully settled. The settlement certificate is available under My Receipts.", loan.getId());
        }
        return toDTO(transaction);
    }

    private Loan ownedLoan(Long loanId, User borrower) {
        if (loanId == null) throw new BusinessRuleException("Loan is required.");
        Loan loan = loanRepository.findById(loanId).orElseThrow(() -> new InvalidTransactionException("Loan not found."));
        if (!loan.getUser().getId().equals(borrower.getId())) throw new UnauthorizedActionException("You do not own this loan.");
        return loan;
    }

    private User requireLinkedLender(Loan loan, User borrower) {
        User counterparty = loan.getLender().getLinkedUser();
        if (counterparty == null) throw new CounterpartyNotRegisteredException("This lender must have a linked Bakir Khata account before using confirmed payments.");
        if (counterparty.getId().equals(borrower.getId())) throw new InvalidTransactionException("You cannot pay yourself.");
        if (counterparty.getAccountStatus() != AccountStatus.ACTIVE) throw new BusinessRuleException("The lender account is not active.");
        return counterparty;
    }

    private void validateAmount(Loan loan, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new BusinessRuleException("Amount must be greater than zero.");
        if (amount.compareTo(loan.getRemainingAmount()) > 0) throw new BusinessRuleException("Payment cannot exceed the remaining principal of ৳" + loan.getRemainingAmount() + ".");
    }

    private PaymentAccount validatePaymentAccount(Long accountId, User counterparty, TransactionMethod method) {
        if (accountId == null) throw new BusinessRuleException("Select one of the lender's verified payment accounts.");
        PaymentAccount account = paymentAccountRepository.findById(accountId).orElseThrow(() -> new BusinessRuleException("Payment account not found."));
        if (!account.getUser().getId().equals(counterparty.getId()) || !account.isActive()) throw new BusinessRuleException("That payment destination is not available for this lender.");
        if (method == TransactionMethod.BANK) {
            throw new BusinessRuleException("Bank account verification is under construction. No verified bank payment destination is available.");
        }
        if (method == TransactionMethod.MOBILE_BANKING) {
            if (account.getType() != PaymentAccountType.MOBILE) {
                throw new BusinessRuleException("Select a mobile payment account.");
            }
            if (!account.isPhoneControlVerified()) {
                throw new BusinessRuleException("The lender must confirm this mobile number by OTP before it can be used in a test payment.");
            }
            if (account.isDemoPhoneProof() && (!"demo".equalsIgnoreCase(paymentMode)
                    || activeProfile.toLowerCase().contains("prod"))) {
                throw new BusinessRuleException("Demo OTP verification cannot authorize this payment mode. Ask the lender to complete real SMS OTP verification.");
            }
        }
        return account;
    }

    private String providerName(PaymentAccount account) {
        if (account.getType() == PaymentAccountType.BANK) return account.getBankName();
        return account.getMobileProvider() == null ? "Mobile Banking" : account.getMobileProvider().getDisplayName();
    }

    private Transaction loadOwnedByCounterparty(Long transactionId, User lenderUser) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new InvalidTransactionException("Transaction not found."));
        if (!transaction.getCounterpartyUser().getId().equals(lenderUser.getId())) throw new UnauthorizedActionException("This transaction was not sent to you.");
        return transaction;
    }

    private Transaction loadOwnedByInitiator(Long transactionId, User borrower) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new InvalidTransactionException("Transaction not found."));
        if (!transaction.getInitiatedBy().getId().equals(borrower.getId())) throw new UnauthorizedActionException("This transaction does not belong to you.");
        return transaction;
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String clean = phone.trim();
        if (clean.length() <= 4) return "****";
        return "*".repeat(Math.max(4, clean.length() - 4)) + clean.substring(clean.length() - 4);
    }

    private TransactionDTO toDTO(Transaction t) {
        return new TransactionDTO(t.getId(), t.getLoan().getId(), t.getInitiatedBy().getName(), t.getCounterpartyUser().getName(),
                t.getAmount(), t.getServiceFee(), t.getOverduePenaltyAmount(), t.getTotalPayable(), t.getMethod(),
                t.getPaymentProvider(), t.getRecipientMasked(), t.getTransactionReferenceId(), t.getGatewayReference(),
                maskPhone(t.getVerificationPhone()), t.getStatus(), t.isOtpVerified(), t.isFlagged(), t.getReviewNote(), t.getCreatedAt(), t.getRespondedAt());
    }
}
