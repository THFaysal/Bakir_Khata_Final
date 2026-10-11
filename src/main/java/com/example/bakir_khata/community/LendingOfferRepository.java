package com.example.bakir_khata.community;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LendingOfferRepository extends JpaRepository<LendingOffer,Long> {
    List<LendingOffer> findByBroadcastIdOrderByCreatedAtDesc(Long broadcastId);
    List<LendingOffer> findByLenderIdOrderByCreatedAtDesc(Long lenderId);
    Optional<LendingOffer> findByBroadcastIdAndLenderId(Long broadcastId,Long lenderId);
}
