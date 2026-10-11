package com.example.bakir_khata.messenger;

import com.example.bakir_khata.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="blocked_messenger_contacts",uniqueConstraints=@UniqueConstraint(name="uq_block_pair",columnNames={"owner_id","target_id"}))
@Getter @Setter
public class BlockedContact {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id") private User owner;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="target_id") private User target;
    private LocalDateTime blockedAt=LocalDateTime.now();
}
