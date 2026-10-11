package com.example.bakir_khata.community;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface LoanBroadcastRepository extends JpaRepository<LoanBroadcast,Long> {
    List<LoanBroadcast> findByStatusOrderByCreatedAtDesc(LoanBroadcast.Status status);
    List<LoanBroadcast> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    long countByOwnerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId,LocalDateTime start,LocalDateTime end);
}
