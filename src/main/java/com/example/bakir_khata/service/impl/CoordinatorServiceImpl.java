package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.dto.CoordinatorApplicationDTO;
import com.example.bakir_khata.dto.CoordinatorDTO;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.exception.InvalidTransactionException;
import com.example.bakir_khata.model.Coordinator;
import com.example.bakir_khata.model.CoordinatorApplication;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.ApplicationStatus;
import com.example.bakir_khata.model.enums.Role;
import com.example.bakir_khata.repository.CoordinatorApplicationRepository;
import com.example.bakir_khata.repository.CoordinatorRepository;
import com.example.bakir_khata.repository.UserRepository;
import com.example.bakir_khata.service.CoordinatorService;
import com.example.bakir_khata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoordinatorServiceImpl implements CoordinatorService {
    private final CoordinatorApplicationRepository applicationRepository;
    private final CoordinatorRepository coordinatorRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override @Transactional
    public CoordinatorApplicationDTO apply(User user, String reason) {
        if (!user.isCoordinatorEligible()) throw new BusinessRuleException("You are not currently eligible to apply for coordinator access.");
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("Please explain why you want to become a coordinator.");
        if (hasPendingApplication(user)) throw new InvalidTransactionException("You already have a pending coordinator application.");
        if (isCurrentlyCoordinator(user)) throw new InvalidTransactionException("You are already a coordinator.");
        CoordinatorApplication application = applicationRepository.save(CoordinatorApplication.builder()
                .user(user).reason(reason.trim()).status(ApplicationStatus.PENDING).build());
        return toDTO(application);
    }

    @Override @Transactional
    public void approve(Long applicationId, User admin) {
        CoordinatorApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new InvalidTransactionException("Application not found."));
        if (application.getStatus() != ApplicationStatus.PENDING) throw new BusinessRuleException("This application has already been reviewed.");
        User applicant = application.getUser();
        if (!applicant.isCoordinatorEligible()) throw new BusinessRuleException("This user is no longer eligible for coordinator access.");

        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);

        applicant.setRole(Role.COORDINATOR);
        applicant.setTokenVersion(applicant.getTokenVersion() + 1);
        applicant.setCoordinatorEligible(false);
        userRepository.save(applicant);
        if (coordinatorRepository.findByUserId(applicant.getId()).isEmpty()) {
            coordinatorRepository.save(Coordinator.builder().user(applicant).approvedBy(admin).active(true).build());
        }
        notificationService.notify(applicant, "COORDINATOR_APPROVED",
                "Your coordinator application was approved. Sign in again to refresh your coordinator permissions.", null);
        notificationService.disconnect(applicant.getId());
    }

    @Override @Transactional
    public void reject(Long applicationId, User admin) {
        CoordinatorApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new InvalidTransactionException("Application not found."));
        if (application.getStatus() != ApplicationStatus.PENDING) throw new BusinessRuleException("This application has already been reviewed.");
        application.setStatus(ApplicationStatus.REJECTED);
        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);
        User applicant = application.getUser();
        applicant.setCoordinatorEligible(false);
        userRepository.save(applicant);
        notificationService.notify(applicant, "COORDINATOR_REJECTED",
                "Your coordinator application was not approved. A new admin invitation is required before applying again.", null);
    }

    @Override
    public List<CoordinatorDTO> listCoordinators() {
        return coordinatorRepository.findAllForAdmin().stream().map(c -> new CoordinatorDTO(
                c.getId(), c.getUser().getId(), c.getUser().getName(), c.getUser().getEmail(),
                c.getApprovedBy().getName(), c.getApprovedAt(), c.isActive(), c.getMonthlySalary())).toList();
    }

    @Override public List<CoordinatorApplicationDTO> listApplications() {
        return applicationRepository.findAllForAdmin().stream().map(this::toDTO).toList();
    }
    @Override public boolean isCurrentlyCoordinator(User user) {
        return coordinatorRepository.findByUserId(user.getId()).filter(Coordinator::isActive).isPresent();
    }
    @Override public boolean hasPendingApplication(User user) {
        return applicationRepository.findFirstByUserIdAndStatus(user.getId(), ApplicationStatus.PENDING).isPresent();
    }

    @Override @Transactional
    public void setEligibility(Long userId, boolean eligible, User admin) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessRuleException("User not found."));
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.COORDINATOR) throw new BusinessRuleException("Only normal users can be marked coordinator-eligible.");
        user.setCoordinatorEligible(eligible);
        userRepository.save(user);
        notificationService.notify(user, eligible ? "COORDINATOR_ELIGIBLE" : "COORDINATOR_ELIGIBILITY_REMOVED",
                eligible ? "Admin has invited you to apply as a coordinator. The Become Coordinator option is now available." : "Your coordinator eligibility has been removed.", null);
    }

    @Override @Transactional
    public void revokeCoordinator(Long coordinatorId, User admin) {
        Coordinator coordinator = coordinatorRepository.findById(coordinatorId).orElseThrow(() -> new BusinessRuleException("Coordinator not found."));
        coordinator.setActive(false);
        coordinatorRepository.save(coordinator);
        User user = coordinator.getUser();
        user.setRole(Role.USER);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setCoordinatorEligible(false);
        userRepository.save(user);
        notificationService.notify(user, "COORDINATOR_REVOKED", "Your coordinator access has been removed by an administrator.", null);
        notificationService.disconnect(user.getId());
    }

    @Override @Transactional
    public void updateMonthlySalary(Long coordinatorId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new BusinessRuleException("Salary cannot be negative.");
        Coordinator coordinator = coordinatorRepository.findById(coordinatorId).orElseThrow(() -> new BusinessRuleException("Coordinator not found."));
        coordinator.setMonthlySalary(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        coordinatorRepository.save(coordinator);
    }

    private CoordinatorApplicationDTO toDTO(CoordinatorApplication a) {
        return new CoordinatorApplicationDTO(a.getId(), a.getUser().getId(), a.getUser().getName(), a.getUser().getEmail(),
                a.getReason(), a.getStatus(), a.getAppliedAt(), a.getReviewedAt());
    }
}
