package com.example.bakir_khata.messenger;

import com.example.bakir_khata.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/messenger") @RequiredArgsConstructor
public class MessengerKeyController {
    private final MessengerService messenger;
    @PostMapping("/keys") public ResponseEntity<String> register(@AuthenticationPrincipal User user,
                    @RequestParam String publicKey,@RequestParam(defaultValue="false") boolean reset) {
        messenger.registerPublicKey(user,publicKey,reset);
        return ResponseEntity.ok("Public key registered");
    }
}
