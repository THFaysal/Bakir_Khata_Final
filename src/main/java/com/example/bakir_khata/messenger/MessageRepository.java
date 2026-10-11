package com.example.bakir_khata.messenger;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
public interface MessageRepository extends MongoRepository<Message,String> {
    List<Message> findTop100ByConversationIdOrderByCreatedAtDesc(String conversationId);
    Optional<Message> findByClientMessageId(String clientMessageId);
}
