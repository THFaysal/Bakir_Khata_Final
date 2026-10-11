package com.example.bakir_khata.messenger;

import com.example.bakir_khata.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/messenger") @RequiredArgsConstructor
public class MessengerApiController {
    private final MessengerService messenger;
    public record CreateConversation(Long peerId) {}
    @GetMapping("/conversations") public List<Conversation> conversations(@AuthenticationPrincipal User user) {
        return messenger.inbox(user);
    }
    @PostMapping("/conversations") public Map<String,String> create(@AuthenticationPrincipal User user,
                                                @RequestBody CreateConversation request) {
        return Map.of("conversationId",messenger.start(user,request.peerId()).getId());
    }
    @GetMapping("/conversations/{id}/messages") public List<MessengerService.MessageView> history(
                                  @AuthenticationPrincipal User user,@PathVariable String id) {
        return messenger.history(user,id);
    }
    @PostMapping("/conversations/send") public MessengerService.MessageView send(
             @AuthenticationPrincipal User user,@RequestBody MessengerService.EncryptedPayload payload) {
        return messenger.send(user,payload);
    }
    @GetMapping("/identity") public Map<String,Object> identity(@AuthenticationPrincipal User user) {
        var p=messenger.identity(user);
        return Map.of("messengerId",p.getMessengerId(),"displayName",p.getDisplayName(),
                "publicKey",p.getPublicKey()==null?"":p.getPublicKey());
    }
}
