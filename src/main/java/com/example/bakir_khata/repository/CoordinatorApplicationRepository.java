package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.enums.ApplicationStatus;
import com.example.bakir_khata.model.CoordinatorApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoordinatorApplicationRepository extends JpaRepository<CoordinatorApplication, Long> {

    @Query("""
        select distinct a from CoordinatorApplication a
        left join fetch a.user
        order by a.appliedAt desc
    """)
    List<CoordinatorApplication> findAllForAdmin();

    Optional<CoordinatorApplication> findFirstByUserIdAndStatus(Long userId, ApplicationStatus status);
}
