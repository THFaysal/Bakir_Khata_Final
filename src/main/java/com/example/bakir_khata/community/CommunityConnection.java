package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="community_connections",uniqueConstraints=@UniqueConstraint(name="uq_connection_members", columnNames={"borrower_id","lender_id"}))
@Getter @Setter
public class CommunityConnection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="borrower_id") private User borrower;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="lender_id") private User lender;
    @Column(nullable=false) private LocalDateTime connectedAt=LocalDateTime.now();
    @Column(nullable=false) private boolean active=true;
}
