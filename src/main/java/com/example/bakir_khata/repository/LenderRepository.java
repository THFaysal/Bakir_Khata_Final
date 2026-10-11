package com.example.bakir_khata.repository;

import com.example.bakir_khata.model.Lender;
import com.example.bakir_khata.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LenderRepository extends JpaRepository<Lender, Long> {

    Optional<Lender> findByIdAndUser(Long id, User user);
    Optional<Lender> findFirstByUserIdAndLinkedUserId(Long borrowerId, Long lenderId);

    @EntityGraph(attributePaths = {"loans"})
    List<Lender> findByUserOrderByNameAsc(User user);

    List<Lender> findByEmailIgnoreCaseOrPhone(String email, String phone);

    @EntityGraph(attributePaths = {"loans"})
    @Query("""
           select l from Lender l
           where l.user = :user
             and (lower(l.name) like lower(concat('%', :term, '%'))
                  or l.phone like concat('%', :term, '%')
                  or lower(coalesce(l.email, '')) like lower(concat('%', :term, '%')))
           order by l.name asc
           """)
    List<Lender> search(
            @Param("user") User user,
            @Param("term") String term
    );
}