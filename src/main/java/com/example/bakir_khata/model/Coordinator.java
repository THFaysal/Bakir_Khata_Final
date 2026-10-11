package com.example.bakir_khata.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "coordinators")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coordinator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_admin_id", nullable = false)
    private User approvedBy;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime approvedAt = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "decimal(12,2) default 0.00")
    @Builder.Default
    private BigDecimal monthlySalary = BigDecimal.ZERO;
}
