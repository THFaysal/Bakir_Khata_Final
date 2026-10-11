package com.example.bakir_khata.community;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface CommunityConnectionRepository extends JpaRepository<CommunityConnection,Long> {
    long countByBorrowerIdAndActiveTrue(Long borrowerId);
    Optional<CommunityConnection> findByBorrowerIdAndLenderId(Long borrowerId,Long lenderId);
    boolean existsByBorrowerIdAndLenderIdAndActiveTrue(Long borrowerId,Long lenderId);
    List<CommunityConnection> findByBorrowerIdOrLenderId(Long borrowerId,Long lenderId);
}
