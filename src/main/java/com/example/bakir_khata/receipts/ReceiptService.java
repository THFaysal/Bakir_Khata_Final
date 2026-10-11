package com.example.bakir_khata.receipts;

import com.example.bakir_khata.community.CommunityProfileRepository;
import com.example.bakir_khata.community.LoanAgreement;
import com.example.bakir_khata.community.LoanAgreementRepository;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.exception.ResourceNotFoundException;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.Transaction;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.model.enums.TransactionMethod;
import com.example.bakir_khata.model.enums.TransactionStatus;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.repository.PaymentRepository;
import com.example.bakir_khata.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only receipt projections. No receipt download mutates the financial ledger,
 * and no receipt can be fetched unless its borrower or explicitly linked lender is
 * authenticated. The PDFs are generated on demand: never uploaded to public storage.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final String DEMO_NOTICE = "BAKIR KHATA - FINAL YEAR DEMONSTRATION. Mobile and sandbox "
            + "transactions are simulated; a PDF is a system record and is not evidence of bank settlement.";

    private final LoanRepository loans;
    private final PaymentRepository payments;
    private final TransactionRepository transactions;
    private final LoanAgreementRepository agreements;
    private final CommunityProfileRepository profiles;
    private final ReceiptSigner signer;

    public record LoanRow(Long id, String direction, String counterpart, BigDecimal amount,
                          boolean demoDisbursement, boolean settled) { }
    public record PaymentRow(Long id, Long loanId, String direction, String counterpart,
                             BigDecimal amount, LocalDate date, boolean confirmed) { }
    public record Overview(List<LoanRow> loans, List<PaymentRow> payments) { }

    /** Merges both sides without creating duplicate receipts for one loan. */
    public Overview overview(User actor) {
        if (actor == null) throw new ResourceNotFoundException("Account not found");
        Long id = actor.getId();
        LinkedHashMap<Long, Loan> accessible = new LinkedHashMap<>();
        loans.findByUser_IdOrderByCreatedAtDesc(id).forEach(l -> accessible.put(l.getId(), l));
        loans.findByLender_LinkedUser_IdOrderByCreatedAtDesc(id).forEach(l -> accessible.putIfAbsent(l.getId(), l));
        List<LoanRow> loanRows = new ArrayList<>();
        List<PaymentRow> paymentRows = new ArrayList<>();
        for (Loan l : accessible.values()) {
            boolean borrower = l.getUser().getId().equals(id);
            String direction = borrower ? "Borrowed" : "Lent";
            String otherName = borrower ? lenderName(l) : l.getUser().getName();
            loanRows.add(new LoanRow(l.getId(), direction, otherName, l.getAmount(),
                    isConfirmedCommunity(l.getId()), isSettled(l)));
            for (Payment payment : payments.findByLoan_IdOrderByCreatedAtAscIdAsc(l.getId())) {
                boolean confirmed = transactions.findByPayment_Id(payment.getId())
                        .map(t -> t.getStatus() == TransactionStatus.SUCCESS).orElse(false);
                paymentRows.add(new PaymentRow(payment.getId(), l.getId(),
                        borrower ? "Paid" : "Received", otherName, payment.getAmount(),
                        payment.getPaymentDate(), confirmed));
            }
        }
        loanRows.sort(Comparator.comparing(LoanRow::id).reversed());
        paymentRows.sort(Comparator.comparing(PaymentRow::id).reversed());
        return new Overview(List.copyOf(loanRows), List.copyOf(paymentRows));
    }

    public ReceiptDocument loanReceipt(Long loanId, User actor) {
        Loan l = authorizedLoan(loanId, actor);
        LoanAgreement agreement = agreements.findByLoan_Id(l.getId()).orElse(null);
        boolean confirmed = agreement != null && agreement.getStatus() == LoanAgreement.Status.ACTIVE;
        String title = confirmed ? "LOAN DISBURSEMENT RECEIPT" : "LOAN RECORD STATEMENT";
        String status = confirmed ? "DEMO DISBURSEMENT CONFIRMED" : "USER-RECORDED LOAN";
        List<ReceiptDocument.Field> details = new ArrayList<>(List.of(
                field("Borrower", l.getUser().getName()),
                field("Borrower Messenger ID", publicMessengerId(l.getUser().getId())),
                field("Lender", lenderName(l)),
                field("Lender Messenger ID", l.getLender().getLinkedUser() == null ? "Not linked" :
                        publicMessengerId(l.getLender().getLinkedUser().getId())),
                field("Loan date", format(l.getBorrowDate())),
                field("Repayment deadline", format(l.getDueDate())),
                field("Purpose", safeValue(l.getPurpose())),
                field("Loan reference", ReceiptKind.LOAN.reference(l.getId()))
        ));
        if (confirmed) {
            details.add(field("Mutually confirmed", format(agreement.getConfirmedAt())));
            details.add(field("Community agreement", "#" + agreement.getId()));
        }
        List<ReceiptDocument.Field> totals = List.of(
                field("Original principal", bdt(l.getAmount())),
                field("Current principal balance", bdt(l.getRemainingAmount())),
                field("Loan status", l.getStatus().name())
        );
        String description = confirmed
                ? "Borrower accepted the offer; lender confirmed a simulated disbursement."
                : "Entered in the borrower's personal ledger; disbursement is not independently verified.";
        return document(ReceiptKind.LOAN, l.getId(), title, status,
                "ORIGINAL LOAN AMOUNT", bdt(l.getAmount()), description, details, totals);
    }

    public ReceiptDocument paymentReceipt(Long paymentId, User actor) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt not found"));
        Loan l = payment.getLoan();
        verifyParticipant(l, actor);
        Transaction tx = transactions.findByPayment_Id(payment.getId()).orElse(null);
        boolean confirmed = tx != null && tx.getStatus() == TransactionStatus.SUCCESS;
        String status;
        if (!confirmed) {
            status = "USER-RECORDED PAYMENT";
        } else if (tx.getMethod() == TransactionMethod.CASH) {
            status = "LENDER-CONFIRMED CASH";
        } else if (tx.getPaymentProvider() != null && tx.getPaymentProvider().contains("SANDBOX")) {
            status = "SANDBOX PAYMENT SUCCESS";
        } else {
            status = "DEMO PAYMENT SUCCESS";
        }

        BigDecimal before = payment.getRemainingPrincipalBefore();
        BigDecimal after = payment.getRemainingPrincipalAfter();
        if (before == null || after == null) {
            // Backward compatible historical ledger reconstruction for older records.
            BigDecimal remaining = l.getAmount();
            for (Payment historic : payments.findByLoan_IdOrderByCreatedAtAscIdAsc(l.getId())) {
                BigDecimal previous = remaining;
                remaining = remaining.subtract(historic.getAmount());
                if (Objects.equals(historic.getId(), payment.getId())) {
                    if (before == null) before = previous;
                    if (after == null) after = remaining;
                    break;
                }
            }
        }
        BigDecimal principal = money(payment.getAmount());
        BigDecimal fee = money(payment.getServiceFee());
        BigDecimal penalty = money(payment.getPenaltyAmount());
        BigDecimal total = payment.getTotalCharged() == null || payment.getTotalCharged().signum() == 0
                ? principal.add(fee).add(penalty) : money(payment.getTotalCharged());
        String provider = confirmed && tx.getPaymentProvider() != null
                ? tx.getPaymentProvider() : payment.getPaymentMethod().name().replace('_', ' ');
        String txRef = tx == null ? "Manual ledger entry" :
                safeValue(tx.getGatewayReference() == null
                        ? (tx.getTransactionReferenceId() == null ? "BK-TX-" + tx.getId() : tx.getTransactionReferenceId())
                        : tx.getGatewayReference());
        List<ReceiptDocument.Field> details = List.of(
                field("Paid by", safeValue(payment.getBorrowerNameAtPayment(), l.getUser().getName())),
                field("Received by", safeValue(payment.getLenderNameAtPayment(), lenderName(l))),
                field("Associated loan", ReceiptKind.LOAN.reference(l.getId())),
                field("Payment date", format(payment.getCreatedAt())),
                field("Payment method", provider),
                field("Gateway / transaction ref", txRef),
                field("Receipt reference", ReceiptKind.PAYMENT.reference(payment.getId()))
        );
        List<ReceiptDocument.Field> totals = List.of(
                field("Principal repaid", bdt(principal)),
                field("Service fee", bdt(fee)),
                field("Overdue penalty", bdt(penalty)),
                field("TOTAL CHARGED", bdt(total)),
                field("Principal before payment", bdt(before)),
                field("Principal after payment", bdt(after))
        );
        String description = confirmed
                ? (tx.getMethod() == TransactionMethod.CASH
                    ? "Cash receipt confirmed by the lender within Bakir Khata."
                    : "Test checkout record; does not represent transfer of real money to the lender.")
                : "Personal ledger entry only; no two-party or gateway confirmation is recorded.";
        return document(ReceiptKind.PAYMENT, payment.getId(), "REPAYMENT RECEIPT", status,
                "TOTAL PAYMENT", bdt(total), description, details, totals);
    }

    public ReceiptDocument transactionReceipt(Long transactionId, User actor) {
        Transaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        verifyParticipant(tx.getLoan(), actor);
        if (tx.getStatus() != TransactionStatus.SUCCESS || tx.getPayment() == null) {
            throw new BusinessRuleException("Only successful transactions have payment receipts.");
        }
        return paymentReceipt(tx.getPayment().getId(), actor);
    }

    public ReceiptDocument settlementReceipt(Long loanId, User actor) {
        Loan loan = authorizedLoan(loanId, actor);
        if (!isSettled(loan)) throw new BusinessRuleException("A settlement certificate is available only when the full loan and penalties are cleared.");
        List<Payment> paid = payments.findByLoan_IdOrderByCreatedAtAscIdAsc(loanId);
        BigDecimal principal = paid.stream().map(Payment::getAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal fees = paid.stream().map(p -> money(p.getServiceFee()))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal penalty = paid.stream().map(p -> money(p.getPenaltyAmount()))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal allCharged = principal.add(fees).add(penalty);
        LocalDateTime lastPaid = paid.stream().map(Payment::getCreatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(loan.getCreatedAt());
        boolean unverifiedEntry = paid.stream().anyMatch(p -> transactions.findByPayment_Id(p.getId())
                .map(t -> t.getStatus() != TransactionStatus.SUCCESS).orElse(true));
        String status = unverifiedEntry ? "LEDGER SETTLED" : "PAYMENTS COMPLETED";
        String description = unverifiedEntry
                ? "The recorded balance is zero. One or more ledger entries are not gateway/lender verified."
                : "Loan principal and recorded penalties are fully repaid in the application's ledger.";
        List<ReceiptDocument.Field> details = List.of(
                field("Borrower", loan.getUser().getName()),
                field("Lender", lenderName(loan)),
                field("Original loan", ReceiptKind.LOAN.reference(loan.getId())),
                field("Loan date", format(loan.getBorrowDate())),
                field("Last payment recorded", format(lastPaid)),
                field("Payment installments", Integer.toString(paid.size())),
                field("Settlement certificate", ReceiptKind.SETTLEMENT.reference(loanId))
        );
        List<ReceiptDocument.Field> totals = List.of(
                field("Original principal", bdt(loan.getAmount())),
                field("Principal repaid", bdt(principal)),
                field("Service fees recorded", bdt(fees)),
                field("Penalties recorded", bdt(penalty)),
                field("Total charges recorded", bdt(allCharged)),
                field("OUTSTANDING BALANCE", "BDT 0.00")
        );
        return document(ReceiptKind.SETTLEMENT, loanId, "LOAN SETTLEMENT CERTIFICATE", status,
                "REMAINING BALANCE", "BDT 0.00", description, details, totals);
    }

    public boolean settlementAvailable(Long loanId, User actor) {
        return isSettled(authorizedLoan(loanId, actor));
    }

    public boolean confirmedCommunityLoan(Long loanId, User actor) {
        authorizedLoan(loanId, actor);
        return isConfirmedCommunity(loanId);
    }

    /** Public QR verification returns only document type and status, never identity data. */
    public String publicVerificationStatus(ReceiptKind kind, Long sourceId) {
        if (kind == null || sourceId == null || sourceId < 1) return "NOT FOUND";
        return switch (kind) {
            case LOAN -> loans.findById(sourceId)
                    .map(l -> isConfirmedCommunity(l.getId()) ? "DEMO DISBURSEMENT CONFIRMED"
                            : "USER-RECORDED LOAN").orElse("NOT FOUND");
            case PAYMENT -> payments.findById(sourceId).map(p -> transactions.findByPayment_Id(p.getId())
                    .map(tx -> tx.getStatus() == TransactionStatus.SUCCESS ? "PAYMENT RECORD CONFIRMED"
                            : "NOT CONFIRMED").orElse("USER-RECORDED PAYMENT")).orElse("NOT FOUND");
            case SETTLEMENT -> loans.findById(sourceId)
                    .map(l -> isSettled(l) ? "LEDGER SETTLED" : "NOT SETTLED").orElse("NOT FOUND");
        };
    }

    private ReceiptDocument document(ReceiptKind kind, Long id, String title, String status,
                                     String amountLabel, String amount, String description,
                                     List<ReceiptDocument.Field> details, List<ReceiptDocument.Field> totals) {
        return new ReceiptDocument(kind, id, title, kind.reference(id), status,
                amountLabel, amount, description, List.copyOf(details), List.copyOf(totals),
                DEMO_NOTICE, signer.verifyUrl(kind, id));
    }

    private Loan authorizedLoan(Long id, User actor) {
        Loan loan = loans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
        verifyParticipant(loan, actor);
        return loan;
    }

    private void verifyParticipant(Loan loan, User actor) {
        Long userId = actor == null ? null : actor.getId();
        boolean borrower = userId != null && loan.getUser().getId().equals(userId);
        boolean lender = userId != null && loan.getLender().getLinkedUser() != null
                && loan.getLender().getLinkedUser().getId().equals(userId);
        if (!borrower && !lender) {
            throw new ResourceNotFoundException("Receipt not found");
        }
    }

    private boolean isConfirmedCommunity(Long loanId) {
        return agreements.findByLoan_Id(loanId)
                .map(a -> a.getStatus() == LoanAgreement.Status.ACTIVE).orElse(false);
    }

    private boolean isSettled(Loan loan) {
        // PAID alone is not sufficient if a stored outstanding penalty remains.
        return loan.getStatus() == LoanStatus.PAID
                && loan.getRemainingAmount() != null && loan.getRemainingAmount().signum() == 0
                && (loan.getOverduePenalty() == null || loan.getOverduePenalty().signum() == 0);
    }

    private String publicMessengerId(Long accountId) {
        return profiles.findByUserId(accountId)
                .map(p -> p.getMessengerId()).orElse("Not linked");
    }
    private String lenderName(Loan l) {
        return l.getLender().getLinkedUser() == null ? l.getLender().getName()
                : l.getLender().getLinkedUser().getName();
    }
    private ReceiptDocument.Field field(String label, String value) {
        return new ReceiptDocument.Field(label, safeValue(value));
    }
    private String safeValue(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }
    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? safeValue(fallback) : value;
    }
    private String format(LocalDate date) { return date == null ? "Not provided" : DATE.format(date); }
    private String format(LocalDateTime time) { return time == null ? "Not provided" : TIME.format(time); }
    private BigDecimal money(BigDecimal amount) { return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP); }
    private String bdt(BigDecimal amount) { return "BDT " + String.format(Locale.US, "%,.2f", money(amount)); }
}
