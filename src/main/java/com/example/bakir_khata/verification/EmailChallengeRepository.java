package com.example.bakir_khata.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailChallengeRepository extends JpaRepository<EmailChallenge,Long> {
    Optional<EmailChallenge> findFirstByUserIdAndVerifiedFalseOrderByCreatedAtDesc(Long userId);
}
