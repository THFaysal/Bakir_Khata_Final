package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="lending_offers", uniqueConstraints=@UniqueConstraint(name="uq_offer_broadcast_lender",columnNames={"broadcast_id","lender_id"}))
@Getter @Setter
public class LendingOffer {
    public enum Status { PENDING, ACCEPTED, REJECTED, WITHDRAWN }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="broadcast_id",nullable=false) private LoanBroadcast broadcast;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="lender_id",nullable=false) private User lender;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=600) private String note;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status=Status.PENDING;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}
