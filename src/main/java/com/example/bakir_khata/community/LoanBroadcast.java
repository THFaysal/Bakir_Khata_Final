package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity @Table(name="loan_broadcasts", indexes={@Index(name="idx_broadcast_created",columnList="created_at")}) @Getter @Setter
public class LoanBroadcast {
    public enum Status { OPEN, RESERVED, FUNDED, CLOSED, HIDDEN, EXPIRED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id") private User owner;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=150) private String purpose;
    @Column(nullable=false,length=1000) private String description;
    @Column(nullable=false) private int durationDays;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status=Status.OPEN;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
    @Column(nullable=false) private LocalDateTime expiresAt;
}
