package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.RevenueLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevenueLedgerRepository extends JpaRepository<RevenueLedger, Long> {
    boolean existsByTransaction_Id(Long transactionId);
}
