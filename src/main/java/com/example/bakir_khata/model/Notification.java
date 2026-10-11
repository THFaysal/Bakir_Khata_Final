package com.example.bakir_khata.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Column(nullable = false, length = 500)
    private String message;

    // e.g. "TRANSACTION_REQUEST", "TRANSACTION_ACCEPTED", "TRANSACTION_REJECTED",
    // "COORDINATOR_APPROVED", "COORDINATOR_REJECTED"
    @Column(nullable = false, length = 40)
    private String type;

    // Optional pointer to the related transaction, for a "view" link
    private Long relatedTransactionId;

    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
