package com.example.bakir_khata.messenger;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller @RequiredArgsConstructor
public class MessengerStompController {
    private final MessengerService messenger;
    private final UserRepository users;
    @MessageMapping("/messages.send")
    public void send(@Payload MessengerService.EncryptedPayload payload,Principal principal) {
        if (principal==null) throw new IllegalArgumentException("Login required");
        User user=users.findByEmail(principal.getName()).orElseThrow();
        messenger.send(user,payload);
    }
}
