package com.example.bakir_khata.messenger;

import com.example.bakir_khata.community.*;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import com.example.bakir_khata.verification.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service @RequiredArgsConstructor
public class MessengerService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CommunityProfileRepository profiles;
    private final CommunityService community;
    private final VerificationService verification;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;
    private final BlockedContactRepository blocks;

    public record EncryptedPayload(String conversationId,String clientMessageId,String ciphertext,String iv) {}
    public record MessageView(String id,String conversationId,Long senderId,Long recipientId,
                              String ciphertext,String iv,String createdAt,boolean read) {}

    public CommunityProfile identity(User actor) { return community.profile(actor); }

    public CommunityProfile publicIdentity(String messengerId,User actor) {
        verification.requireCommunity(actor);
        return profiles.findByMessengerId(messengerId)
                .orElseThrow(()->new BusinessRuleException("Messenger identity not found."));
    }
    public CommunityProfile person(Long userId,User actor) {
        verification.requireCommunity(actor);
        if (!actor.getId().equals(userId) && !community.areConnected(actor.getId(),userId))
            throw new BusinessRuleException("This user is not an accepted connection.");
        return profiles.findByUserId(userId).orElseThrow(()->new BusinessRuleException("User has not joined Messenger."));
    }

    public void registerPublicKey(User actor,String base64Spki,boolean reset) {
        CommunityProfile p=identity(actor);
        if (base64Spki==null || base64Spki.length()>512 || !base64Spki.matches("[A-Za-z0-9+/=]+"))
            throw new BusinessRuleException("Invalid ECDH public key.");
        byte[] key;
        try { key=Base64.getDecoder().decode(base64Spki); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("Invalid public key encoding."); }
        if (key.length<50 || key.length>160) throw new BusinessRuleException("Invalid ECDH public key length.");
        if (p.getPublicKey()!=null && !p.getPublicKey().equals(base64Spki) && !reset)
            throw new BusinessRuleException("A key is already registered. Use explicit key reset; old chat history will not decrypt.");
        p.setPublicKey(base64Spki); profiles.save(p);
    }

    public boolean blocked(Long userId,Long peerId) {
        return blocks.existsByOwnerIdAndTargetId(userId,peerId) || blocks.existsByOwnerIdAndTargetId(peerId,userId);
    }
    public boolean blockedByMe(Long userId,Long peerId) {
        return blocks.existsByOwnerIdAndTargetId(userId,peerId);
    }
    @org.springframework.transaction.annotation.Transactional
    public void block(User actor,Long peerId) {
        verification.requireCommunity(actor);
        if (!community.areConnected(actor.getId(),peerId)) throw new BusinessRuleException("Not an approved contact.");
        if (blocks.existsByOwnerIdAndTargetId(actor.getId(),peerId)) return;
        BlockedContact block=new BlockedContact();block.setOwner(actor);
        block.setTarget(users.findById(peerId).orElseThrow());
        blocks.save(block);
    }
    @org.springframework.transaction.annotation.Transactional
    public void unblock(User actor,Long peerId) {
        blocks.findByOwnerIdAndTargetId(actor.getId(),peerId).ifPresent(blocks::delete);
    }
    public Conversation start(User actor,Long peerId) {
        verification.requireCommunity(actor);
        if (actor.getId().equals(peerId) || !community.areConnected(actor.getId(),peerId))
            throw new BusinessRuleException("A mutually accepted lender connection is required before messaging.");
        if (blocked(actor.getId(),peerId)) throw new BusinessRuleException("Messaging is blocked for this contact.");
        if (users.findById(peerId).filter(User::isCommunityVerified).isEmpty())
            throw new BusinessRuleException("Your contact has not completed verification.");
        Long a=Math.min(actor.getId(),peerId); Long b=Math.max(actor.getId(),peerId);
        return conversations.findByMemberAAndMemberB(a,b).orElseGet(()->{
            Conversation conversation=new Conversation();conversation.setMemberA(a);conversation.setMemberB(b);
            try { return conversations.save(conversation); }
            catch (DuplicateKeyException ex) { return conversations.findByMemberAAndMemberB(a,b).orElseThrow(); }
        });
    }
    public List<Conversation> inbox(User actor) {
        verification.requireCommunity(actor);
        return conversations.findByMemberAOrMemberB(actor.getId(),actor.getId());
    }
    public Conversation requireMember(User actor,String id) {
        verification.requireCommunity(actor);
        Conversation c=conversations.findById(id).orElseThrow(()->new BusinessRuleException("Conversation not found."));
        if (!actor.getId().equals(c.getMemberA()) && !actor.getId().equals(c.getMemberB()))
            throw new BusinessRuleException("Not a member of this conversation.");
        Long peer=peerId(actor,c);
        if (!community.areConnected(actor.getId(),peer)) throw new BusinessRuleException("Connection is no longer active.");
        return c;
    }
    public Long peerId(User actor,Conversation c) {
        return actor.getId().equals(c.getMemberA())?c.getMemberB():c.getMemberA();
    }
    public List<MessageView> history(User actor,String id) {
        requireMember(actor,id);
        List<MessageView> out=new ArrayList<>(messages.findTop100ByConversationIdOrderByCreatedAtDesc(id)
                .stream().map(this::view).toList());
        Collections.reverse(out); return out;
    }
    public MessageView send(User actor,EncryptedPayload payload) {
        if (payload==null || payload.conversationId()==null || payload.clientMessageId()==null ||
            payload.ciphertext()==null || payload.iv()==null) throw new BusinessRuleException("Invalid message.");
        Conversation conversation=requireMember(actor,payload.conversationId());
        if (blocked(actor.getId(),peerId(actor,conversation))) throw new BusinessRuleException("Messaging is blocked for this contact.");
        if (!payload.clientMessageId().matches("[a-fA-F0-9-]{36}") || payload.ciphertext().length()>16000)
            throw new BusinessRuleException("Invalid message identifier or size.");
        try {
            if (Base64.getDecoder().decode(payload.iv()).length!=12 || Base64.getDecoder().decode(payload.ciphertext()).length>12000)
                throw new BusinessRuleException("Invalid ciphertext or nonce.");
        } catch (IllegalArgumentException ex) { throw new BusinessRuleException("Malformed encrypted payload."); }
        // No plaintext, symmetric keys, passwords, phone or email are stored here.
        Message message=messages.findByClientMessageId(payload.clientMessageId()).orElse(null);
        if (message!=null) {
            if (!message.getSenderId().equals(actor.getId()) || !message.getConversationId().equals(payload.conversationId()))
                throw new BusinessRuleException("Duplicate message identifier.");
            return view(message);
        }
        message=new Message();message.setConversationId(conversation.getId());
        message.setClientMessageId(payload.clientMessageId());message.setSenderId(actor.getId());
        Long peer=peerId(actor,conversation);message.setRecipientId(peer);
        message.setCiphertext(payload.ciphertext());message.setIv(payload.iv());
        try { message=messages.save(message); }
        catch (DuplicateKeyException ex) {
            Message existing=messages.findByClientMessageId(payload.clientMessageId()).orElseThrow();
            if (!existing.getSenderId().equals(actor.getId())) throw new BusinessRuleException("Duplicate message identifier.");
            return view(existing);
        }
        MessageView v=view(message);
        // Spring User destinations route only to sessions authenticated as that user.
        User target=users.findById(peer).orElseThrow();
        messaging.convertAndSendToUser(target.getEmail(),"/queue/messages",v);
        messaging.convertAndSendToUser(actor.getEmail(),"/queue/messages",v);
        return v;
    }
    public MessageView view(Message m) {
        return new MessageView(m.getId(),m.getConversationId(),m.getSenderId(),m.getRecipientId(),
                m.getCiphertext(),m.getIv(),m.getCreatedAt().toString(),m.getReadAt()!=null);
    }
}
