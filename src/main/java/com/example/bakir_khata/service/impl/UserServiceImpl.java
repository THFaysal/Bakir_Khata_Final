package com.example.bakir_khata.service.impl;


import com.example.bakir_khata.dto.RegistrationDTO;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.exception.ResourceNotFoundException;
import com.example.bakir_khata.model.Lender;
import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.LenderRepository;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.repository.UserRepository;
import com.example.bakir_khata.service.FileStorageService;
import com.example.bakir_khata.service.UserService;
import com.example.bakir_khata.service.NotificationService;
import com.example.bakir_khata.security.PhoneNumbers;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;
import com.example.bakir_khata.model.enums.AccountStatus;
import com.example.bakir_khata.model.enums.Role;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final LenderRepository lenderRepository;
    private final LoanRepository loanRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Override
    public User register(RegistrationDTO dto) {
        if (userRepository.existsByEmailIgnoreCase(dto.email().trim())) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        String canonicalPhone=PhoneNumbers.canonical(dto.phone());
        if (userRepository.existsByPhone(canonicalPhone)) {
            throw new BusinessRuleException("An account with this phone number already exists.");
        }
        if (!Objects.equals(dto.password(), dto.confirmPassword())) {
            throw new BusinessRuleException("Passwords do not match.");
        }
        User user = new User();
        user.setName(dto.name());
        user.setEmail(dto.email().trim().toLowerCase(java.util.Locale.ROOT));
        user.setPhone(canonicalPhone);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user = userRepository.save(user);
        // Community connections must be accepted explicitly. Never auto-link another
        // member using matching email/phone supplied by an unrelated person.
        return user;
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean phoneExists(String phone) {
        return userRepository.existsByPhone(PhoneNumbers.canonical(phone));
    }

    @Override
    public void updateProfilePicture(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose an image to upload.");
        }
        String path = fileStorageService.store(file, "users");
        user.setProfileImagePath(path);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteAccount(User user) {
        List<Loan> loans = loanRepository.findByUserOrderByDueDateAsc(user);
        BigDecimal outstanding = loans.stream()
                .map(Loan::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException(
                    "You still owe ৳ " + outstanding.setScale(2, java.math.RoundingMode.HALF_UP)
                            + " across your loans. Please clear all outstanding dues before deleting your profile.");
        }

        // Financial applications should preserve historical identities and ledger links.
        // "Delete profile" therefore becomes a permanent account disable rather than
        // physically deleting loans/payments/transactions and breaking audit history.
        user.setAccountStatus(AccountStatus.DISABLED);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        notificationService.disconnect(user.getId());
    }

    @Override
    public List<User> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public void setAccountStatus(Long userId, AccountStatus status, User actor) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getRole() != Role.USER) {
            throw new BusinessRuleException("Only normal user accounts are managed here. Use Admin Coordinator Management for coordinator access.");
        }
        if (target.getId().equals(actor.getId())) {
            throw new BusinessRuleException("You cannot change your own account status here.");
        }
        if (status == AccountStatus.DISABLED) {
            throw new BusinessRuleException("Permanent disable is not available from operational user management. Suspend the account instead.");
        }
        target.setAccountStatus(status);
        if (status != AccountStatus.ACTIVE) target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);
        if (status != AccountStatus.ACTIVE) {
            notificationService.disconnect(target.getId());
        }
    }
}