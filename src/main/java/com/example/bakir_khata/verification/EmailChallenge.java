package com.example.bakir_khata.verification;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="email_verification_challenges", indexes={@Index(name="idx_email_challenge_user", columnList="user_id,created_at")})
@Getter @Setter
public class EmailChallenge {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(nullable=false) private String codeHash;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable=false) private LocalDateTime expiresAt;
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private boolean verified;
}
