package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findFirstByUserIdAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(Long userId, String purpose);
    long countByUserIdAndCreatedAtAfter(Long userId, java.time.LocalDateTime after);
}
