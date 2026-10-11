package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.PaymentAccount;
import com.example.bakir_khata.model.enums.PaymentAccountType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long> {

    @EntityGraph(attributePaths = {"user"})
    List<PaymentAccount> findByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<PaymentAccount> findByUserIdAndTypeAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(
            Long userId, PaymentAccountType type);
}
