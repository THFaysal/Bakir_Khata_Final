package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.Coordinator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CoordinatorRepository extends JpaRepository<Coordinator, Long> {

    @Query("select distinct c from Coordinator c left join fetch c.user left join fetch c.approvedBy order by c.approvedAt desc")
    List<Coordinator> findAllForAdmin();

    Optional<Coordinator> findByUserId(Long userId);
}
