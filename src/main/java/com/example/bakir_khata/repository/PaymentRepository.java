package com.example.bakir_khata.repository;


import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoan_IdOrderByCreatedAtAscIdAsc(Long loanId);

    List<Payment> findByLoanOrderByPaymentDateDescCreatedAtDesc(Loan loan);

    @EntityGraph(attributePaths = {"loan", "loan.lender"})
    @Query("""
           select p from Payment p
           where p.loan.user = :user
           order by p.createdAt desc
           """)
    List<Payment> findRecentByUser(@Param("user") User user);
}
