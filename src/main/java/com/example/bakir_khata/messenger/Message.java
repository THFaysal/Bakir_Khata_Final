package com.example.bakir_khata.messenger;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Document("messages") @Getter @Setter
public class Message {
    @Id private String id;
    @Indexed private String conversationId;
    @Indexed(unique=true) private String clientMessageId;
    private Long senderId;
    private Long recipientId;
    // Ciphertext is produced on the browser device and is never decrypted by the server.
    private String ciphertext;
    private String iv;
    private Instant createdAt=Instant.now();
    private Instant readAt;
}
