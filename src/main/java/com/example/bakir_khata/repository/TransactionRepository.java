package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.Transaction;
import com.example.bakir_khata.model.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByPayment_Id(Long paymentId);

    // Full list for admin/coordinator - eager fetch to avoid open-session-in-view reliance
    @Query("""
        select distinct t from Transaction t
        left join fetch t.loan
        left join fetch t.initiatedBy
        left join fetch t.counterpartyUser
        order by t.createdAt desc
    """)
    List<Transaction> findAllForReview();

    @Query("""
        select distinct t from Transaction t
        left join fetch t.loan
        left join fetch t.initiatedBy
        left join fetch t.counterpartyUser
        where t.counterpartyUser.id = :userId and t.status = :status
        order by t.createdAt desc
    """)
    List<Transaction> findPendingForCounterparty(@Param("userId") Long userId, @Param("status") TransactionStatus status);

    @Query("""
        select distinct t from Transaction t
        left join fetch t.loan
        left join fetch t.initiatedBy
        left join fetch t.counterpartyUser
        where t.counterpartyUser.id = :userId and t.status in :statuses
        order by t.createdAt desc
    """)
    List<Transaction> findForCounterpartyByStatuses(@Param("userId") Long userId, @Param("statuses") List<TransactionStatus> statuses);

    @Query("""
        select distinct t from Transaction t
        left join fetch t.loan
        left join fetch t.initiatedBy
        left join fetch t.counterpartyUser
        where t.initiatedBy.id = :userId
        order by t.createdAt desc
    """)
    List<Transaction> findAllInitiatedBy(@Param("userId") Long userId);

    Optional<Transaction> findByTransactionReferenceId(String transactionReferenceId);

    long countByStatus(TransactionStatus status);

    boolean existsByLoan_IdAndInitiatedBy_IdAndStatusIn(Long loanId, Long initiatedById, List<TransactionStatus> statuses);
    boolean existsByPaymentAccount_IdAndStatusIn(Long paymentAccountId, List<TransactionStatus> statuses);
}
