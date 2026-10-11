package com.example.bakir_khata.messenger;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface BlockedContactRepository extends JpaRepository<BlockedContact,Long> {
    boolean existsByOwnerIdAndTargetId(Long ownerId,Long targetId);
    Optional<BlockedContact> findByOwnerIdAndTargetId(Long ownerId,Long targetId);
}
