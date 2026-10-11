package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="community_subscriptions") @Getter @Setter
public class Subscription {
    public enum Plan { FREE, PREMIUM }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",unique=true) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Plan plan=Plan.FREE;
    private LocalDateTime premiumUntil;
}
