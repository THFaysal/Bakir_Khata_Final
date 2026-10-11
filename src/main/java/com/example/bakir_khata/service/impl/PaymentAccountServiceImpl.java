package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.PaymentAccount;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.MobileProvider;
import com.example.bakir_khata.model.enums.PaymentAccountType;
import com.example.bakir_khata.model.enums.TransactionStatus;
import com.example.bakir_khata.repository.PaymentAccountRepository;
import com.example.bakir_khata.repository.TransactionRepository;
import com.example.bakir_khata.security.PhoneNumbers;
import com.example.bakir_khata.service.OtpService;
import com.example.bakir_khata.service.PaymentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mobile OTP demonstrates control of a SIM. It does NOT establish that a bKash,
 * Nagad or Rocket account exists or belongs to the user. Bank account name and
 * ownership verification requires a separate licensed/authorized bank provider.
 */
@Service
@RequiredArgsConstructor
public class PaymentAccountServiceImpl implements PaymentAccountService {
    private static final List<TransactionStatus> UNFINISHED_TRANSACTIONS = List.of(
            TransactionStatus.PENDING, TransactionStatus.PROCESSING,
            TransactionStatus.OTP_REQUIRED, TransactionStatus.OTP_VERIFIED,
            TransactionStatus.AWAITING_LENDER_CONFIRMATION);

    private final PaymentAccountRepository repository;
    private final TransactionRepository transactions;
    private final OtpService otpService;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAccount> listForUser(User user) {
        return repository.findByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(user.getId());
    }

    @Override
    @Transactional
    public PaymentAccount addBank(User user, String bankName, String holder, String number,
                                  String branch, String routing) {
        BankDetails validated = validateBank(bankName, holder, number, branch, routing);
        requireSlot(user);
        PaymentAccount account = PaymentAccount.builder()
                .user(user).type(PaymentAccountType.BANK).build();
        applyBank(account, validated);
        return repository.save(account);
    }

    @Override
    @Transactional
    public PaymentAccount updateBank(Long id, User user, String bankName, String holder, String number,
                                     String branch, String routing) {
        PaymentAccount account = owned(id, user);
        if (account.getType() != PaymentAccountType.BANK) throw new BusinessRuleException("This is not a bank destination.");
        assertNotInUse(account);
        applyBank(account, validateBank(bankName, holder, number, branch, routing));
        return repository.save(account);
    }

    private void applyBank(PaymentAccount account, BankDetails d) {
        account.setBankName(d.name());
        account.setAccountHolderName(d.holder());
        account.setAccountNumber(d.number());
        account.setBranchName(d.branch());
        account.setRoutingNumber(d.routing());
        // Do not confuse user-entered bank details with ownership verification.
        account.clearVerification();
    }

    @Override
    @Transactional
    public PaymentAccount addMobile(User user, MobileProvider provider, String mobileNumber) {
        String phone = validateMobile(provider, mobileNumber);
        requireSlot(user);
        requireUniqueMobile(user, provider, phone, null);
        return repository.save(PaymentAccount.builder()
                .user(user).type(PaymentAccountType.MOBILE)
                .mobileProvider(provider).mobileNumber(phone)
                .verified(false).build());
    }

    @Override
    @Transactional
    public PaymentAccount updateMobile(Long id, User user, MobileProvider provider, String mobileNumber) {
        String phone = validateMobile(provider, mobileNumber);
        PaymentAccount account = owned(id, user);
        if (account.getType() != PaymentAccountType.MOBILE) throw new BusinessRuleException("This is not a mobile destination.");
        if (account.getMobileProvider() == provider && phone.equals(account.getMobileNumber())) return account;
        assertNotInUse(account);
        requireUniqueMobile(user, provider, phone, id);
        account.setMobileProvider(provider);
        account.setMobileNumber(phone);
        account.setVerificationGeneration((account.getVerificationGeneration() == null
                ? 0L : account.getVerificationGeneration()) + 1L);
        account.clearVerification(); // Changing a provider or number always removes earlier verification.
        return repository.save(account);
    }

    @Override
    @Transactional
    public OtpService.OtpDelivery sendMobileVerification(Long id, User user) {
        PaymentAccount account = owned(id, user);
        if (account.getType() != PaymentAccountType.MOBILE) throw new BusinessRuleException("Select a mobile payment destination.");
        if (account.isPhoneControlVerified()) throw new BusinessRuleException("This number has already completed its OTP check.");
        account.clearVerification(); // Invalidate any misleading legacy verified=true flag.
        repository.save(account);
        return otpService.sendToPhone(user, account.getMobileNumber(), purpose(account));
    }

    @Override
    @Transactional
    public boolean confirmMobileVerification(Long id, User user, String code) {
        PaymentAccount account = owned(id, user);
        if (account.getType() != PaymentAccountType.MOBILE) throw new BusinessRuleException("Select a mobile payment destination.");
        if (account.isPhoneControlVerified()) return true;
        if (code == null || !code.matches("[0-9]{4,10}")) return false;
        OtpService.VerificationResult result = otpService.verifyForPhone(
                user, purpose(account), account.getMobileNumber(), code);
        if (!result.approved()) return false; // Do not roll back OTP attempt counts.
        if (!"TWILIO".equals(result.deliveryMode()) && !"DEMO".equals(result.deliveryMode())) {
            throw new BusinessRuleException("Unrecognized OTP provider response.");
        }
        // Phone control is verified, not ownership/registration with the mobile wallet provider.
        account.setVerified(false);
        account.setVerifiedAt(LocalDateTime.now());
        account.setVerificationMethod("TWILIO".equals(result.deliveryMode()) ? "TWILIO_SMS_OTP" : "DEMO_OTP");
        repository.save(account);
        return true;
    }

    @Override
    @Transactional
    public void deactivate(Long id, User user) {
        PaymentAccount account = owned(id, user);
        assertNotInUse(account);
        account.setActive(false);
        account.clearVerification();
        repository.save(account);
    }

    private void assertNotInUse(PaymentAccount account) {
        if (transactions.existsByPaymentAccount_IdAndStatusIn(account.getId(), UNFINISHED_TRANSACTIONS)) {
            throw new BusinessRuleException("This destination has a pending payment. Finish or cancel the payment before changing it.");
        }
    }

    private String purpose(PaymentAccount account) {
        // The verifier additionally checks the challenge's phone equals the saved phone.
        return "PAYMENT_DESTINATION_" + account.getId() + "_V"
                + (account.getVerificationGeneration() == null ? 0L : account.getVerificationGeneration());
    }

    private void requireSlot(User user) {
        if (listForUser(user).size() >= 10) throw new BusinessRuleException("Maximum 10 active payment destinations per user.");
    }

    private void requireUniqueMobile(User user, MobileProvider provider, String phone, Long exceptId) {
        boolean duplicate = listForUser(user).stream().anyMatch(a ->
                a.getType() == PaymentAccountType.MOBILE && a.getMobileProvider() == provider
                        && existingMatches(a.getMobileNumber(), phone)
                        && (exceptId == null || !exceptId.equals(a.getId())));
        if (duplicate) throw new BusinessRuleException("This mobile destination is already saved for your account.");
    }

    private boolean existingMatches(String stored, String phone) {
        try { return PhoneNumbers.canonical(stored).equals(phone); }
        catch (BusinessRuleException ex) { return false; }
    }

    static String validateMobile(MobileProvider provider, String mobileNumber) {
        if (provider == null || provider == MobileProvider.OTHER) {
            throw new BusinessRuleException("Choose bKash, Nagad or Rocket.");
        }
        String phone = PhoneNumbers.canonical(mobileNumber);
        if (!phone.matches("\\+8801[3-9][0-9]{8}")) {
            throw new BusinessRuleException("Enter a Bangladesh mobile number, e.g. 01712345678.");
        }
        return phone;
    }

    private BankDetails validateBank(String bankName, String holder, String number, String branch, String routing) {
        String name = trim(bankName);
        String owner = trim(holder);
        String acct = trim(number);
        String branchText = trim(branch);
        String route = trim(routing);
        if (name == null || owner == null || acct == null) {
            throw new BusinessRuleException("Bank name, account holder and account number are required.");
        }
        if (name.length() > 80 || owner.length() > 120 || (branchText != null && branchText.length() > 100)) {
            throw new BusinessRuleException("Bank account text is too long.");
        }
        acct = acct.replace(" ", "").replace("-", "");
        if (!acct.matches("[0-9]{6,30}")) {
            throw new BusinessRuleException("Bank account number must be 6 to 30 digits.");
        }
        if (route != null && !route.matches("[0-9]{9}")) {
            throw new BusinessRuleException("Bangladesh bank routing number must contain 9 digits.");
        }
        return new BankDetails(name, owner, acct, branchText, route);
    }

    private PaymentAccount owned(Long id, User user) {
        PaymentAccount account = repository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Payment destination not found."));
        if (!account.isActive() || !account.getUser().getId().equals(user.getId())) {
            throw new BusinessRuleException("Payment destination not found.");
        }
        return account;
    }

    private String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private record BankDetails(String name, String holder, String number, String branch, String routing) { }
}
