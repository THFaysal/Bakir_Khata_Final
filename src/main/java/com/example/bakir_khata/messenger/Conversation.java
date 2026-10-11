package com.example.bakir_khata.messenger;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Document("conversations") @CompoundIndex(name="members_unique",def="{'memberA':1,'memberB':1}",unique=true)
@Getter @Setter
public class Conversation {
    @Id private String id;
    private Long memberA;
    private Long memberB;
    private Instant createdAt=Instant.now();
}
