package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="community_reports") @Getter @Setter
public class ModerationReport {
    public enum Status { OPEN, RESOLVED, DISMISSED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reporter_id") private User reporter;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="broadcast_id") private LoanBroadcast broadcast;
    @Column(nullable=false,length=400) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status=Status.OPEN;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}
