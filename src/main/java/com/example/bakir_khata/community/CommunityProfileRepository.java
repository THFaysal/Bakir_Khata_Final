package com.example.bakir_khata.community;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
public interface CommunityProfileRepository extends JpaRepository<CommunityProfile,Long> {
    Optional<CommunityProfile> findByUserId(Long userId);
    Optional<CommunityProfile> findByMessengerId(String messengerId);
    boolean existsByMessengerId(String messengerId);
    List<CommunityProfile> findByUserIdIn(List<Long> userIds);
}
