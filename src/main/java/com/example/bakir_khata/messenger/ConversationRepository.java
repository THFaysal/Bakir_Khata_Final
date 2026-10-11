package com.example.bakir_khata.messenger;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
public interface ConversationRepository extends MongoRepository<Conversation,String> {
    Optional<Conversation> findByMemberAAndMemberB(Long memberA,Long memberB);
    List<Conversation> findByMemberAOrMemberB(Long memberA,Long memberB);
}
