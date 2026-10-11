package com.example.bakir_khata.security;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="password_reset_requests") @Getter @Setter
public class PasswordResetRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @Column(nullable=false,unique=true,length=64) private String tokenHash;
    @Column(nullable=false) private LocalDateTime expiresAt;
    @Column(nullable=false) private boolean used=false;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}
