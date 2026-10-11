package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.Loan;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.LoanStatus;
import com.example.bakir_khata.model.enums.Priority;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @EntityGraph(attributePaths = {"user", "lender", "lender.linkedUser"})
    List<Loan> findByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "lender", "lender.linkedUser"})
    List<Loan> findByLender_LinkedUser_IdOrderByCreatedAtDesc(Long lenderUserId);

    @EntityGraph(attributePaths = {
            "lender",
            "lender.linkedUser"
    })
    Optional<Loan> findByIdAndUser(Long id, User user);

    @EntityGraph(attributePaths = {
            "lender",
            "lender.linkedUser"
    })
    List<Loan> findByUserOrderByDueDateAsc(User user);

    List<Loan> findByUserAndStatusOrderByDueDateAsc(
            User user,
            LoanStatus status
    );

    List<Loan> findByUserAndPriorityOrderByDueDateAsc(
            User user,
            Priority priority
    );

    List<Loan> findByUserAndDueDateOrderByDueDateAsc(
            User user,
            LocalDate dueDate
    );

    List<Loan> findByUserAndDueDateBetweenOrderByDueDateAsc(
            User user,
            LocalDate start,
            LocalDate end
    );

    List<Loan> findByUserAndStatusNotOrderByDueDateAsc(
            User user,
            LoanStatus status
    );

    @Query("""
           select l from Loan l
           where l.user = :user
             and (lower(l.lender.name) like lower(concat('%', :term, '%'))
                  or l.lender.phone like concat('%', :term, '%')
                  or lower(coalesce(l.purpose, '')) like lower(concat('%', :term, '%'))
                  or str(l.id) like concat('%', :term, '%')
                  or str(l.status) like upper(concat('%', :term, '%'))
                  or str(l.priority) like upper(concat('%', :term, '%')))
           order by l.dueDate asc
           """)
    List<Loan> search(
            @Param("user") User user,
            @Param("term") String term
    );

    List<Loan> findByLender_IdAndUser(
            Long lenderId,
            User user
    );

    @Query("""
           select l from Loan l
           where l.status <>
           com.example.bakir_khata.model.enums.LoanStatus.PAID
           """)
    List<Loan> findAllUnpaidForScheduler();
}