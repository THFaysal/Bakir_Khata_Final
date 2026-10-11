package com.example.bakir_khata.security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest,Long> {
    Optional<PasswordResetRequest> findByTokenHashAndUsedFalse(String tokenHash);
    Optional<PasswordResetRequest> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
}
