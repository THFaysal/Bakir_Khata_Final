package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="community_profiles") @Getter @Setter
public class CommunityProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false,unique=true) private User user;
    @Column(nullable=false,unique=true,length=32) private String messengerId;
    @Column(nullable=false,length=60) private String displayName;
    @Column(length=512) private String avatarUrl;
    @Column(length=512) private String publicKey;
    @Column(nullable=false) private LocalDateTime joinedAt=LocalDateTime.now();
}
