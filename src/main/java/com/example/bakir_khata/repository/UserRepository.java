package com.example.bakir_khata.repository;


import com.example.bakir_khata.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.example.bakir_khata.model.enums.Role;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByRole(Role role);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(Long id);

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<User> findAllByOrderByCreatedAtDesc();
}