package com.example.bakir_khata.security.jwt;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import com.example.bakir_khata.security.MobileOrEmailAuthenticationProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {
    private final JwtSecurity jwtSecurity;
    private final MobileOrEmailAuthenticationProvider provider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record Credentials(@NotBlank String identifier, @NotBlank String credential) {}

    @PostMapping("/token")
    public Map<String,Object> token(@Valid @RequestBody Credentials credentials) {
        Authentication authentication = provider.authenticate(
                new UsernamePasswordAuthenticationToken(credentials.identifier(), credentials.credential()));
        User user = (User) authentication.getPrincipal();
        // Require the account password for JWT access; legacy PIN auth is disabled.
        if (!passwordEncoder.matches(credentials.credential(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("A password is required to obtain a JWT.");
        }
        return Map.of("accessToken", jwtSecurity.issue(user), "tokenType", "Bearer",
                "expiresIn", 1800, "role", user.getRole().name());
    }

    @GetMapping("/me")
    public Map<String,Object> me(@AuthenticationPrincipal User user) {
        return Map.of("id", user.getId(), "email", user.getEmail(),
                "role", user.getRole().name(), "verified", user.isCommunityVerified());
    }
    @PostMapping("/revoke")
    public Map<String,String> revoke(@AuthenticationPrincipal User user) {
        User stored = userRepository.findById(user.getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        stored.setTokenVersion(stored.getTokenVersion() + 1);
        userRepository.save(stored);
        return Map.of("status", "All existing JWT access tokens revoked. Re-login required.");
    }
}
