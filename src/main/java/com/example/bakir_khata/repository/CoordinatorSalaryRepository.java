package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.CoordinatorSalary;
import com.example.bakir_khata.model.enums.SalaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoordinatorSalaryRepository extends JpaRepository<CoordinatorSalary, Long> {
    List<CoordinatorSalary> findAllByOrderBySalaryMonthDescCreatedAtDesc();
    Optional<CoordinatorSalary> findByCoordinator_IdAndSalaryMonth(Long coordinatorId, String salaryMonth);
    List<CoordinatorSalary> findByStatus(SalaryStatus status);
}
