package com.example.bakir_khata.community;

import com.example.bakir_khata.model.Loan;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity @Table(name="community_loan_agreements") @Getter @Setter
public class LoanAgreement {
    public enum Status { AWAITING_LENDER, ACTIVE, DECLINED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="offer_id",unique=true) private LendingOffer offer;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="loan_id",unique=true) private Loan loan;
    @Column(nullable=false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private Status status=Status.AWAITING_LENDER;
    @Column(nullable=false) private LocalDateTime proposedAt=LocalDateTime.now();
    private LocalDateTime confirmedAt;
}
