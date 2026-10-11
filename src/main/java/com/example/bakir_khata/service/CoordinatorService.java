package com.example.bakir_khata.service;

import com.example.bakir_khata.dto.CoordinatorApplicationDTO;
import com.example.bakir_khata.dto.CoordinatorDTO;
import com.example.bakir_khata.model.User;

import java.math.BigDecimal;
import java.util.List;

public interface CoordinatorService {
    CoordinatorApplicationDTO apply(User user, String reason);
    void approve(Long applicationId, User admin);
    void reject(Long applicationId, User admin);
    List<CoordinatorDTO> listCoordinators();
    List<CoordinatorApplicationDTO> listApplications();
    boolean isCurrentlyCoordinator(User user);
    boolean hasPendingApplication(User user);
    void setEligibility(Long userId, boolean eligible, User admin);
    void revokeCoordinator(Long coordinatorId, User admin);
    void updateMonthlySalary(Long coordinatorId, BigDecimal amount);
}
