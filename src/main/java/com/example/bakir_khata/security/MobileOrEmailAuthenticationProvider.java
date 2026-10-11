package com.example.bakir_khata.security;


import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MobileOrEmailAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String identifier = authentication.getName();
        String credential = String.valueOf(authentication.getCredentials());

        if (!StringUtils.hasText(identifier) || !StringUtils.hasText(credential)) {
            throw new BadCredentialsException("Invalid email/phone or password.");
        }

        User user = findByEmailOrPhone(identifier)
                .orElseThrow(() -> new BadCredentialsException("Invalid email/phone or password."));

        if (!user.isEnabled()) {
            throw new DisabledException("This account is disabled.");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("This account is suspended.");
        }

        boolean passwordMatches = passwordEncoder.matches(credential, user.getPassword());
        // Existing numeric PINs are not accepted for sign-in (including development).
        // Password-only auth avoids silently downgrading an account to a short secret.
        if (!passwordMatches) {
            throw new BadCredentialsException("Invalid email/phone or password.");
        }

        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private Optional<User> findByEmailOrPhone(String identifier) {
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(identifier.trim());
        if (byEmail.isPresent()) return byEmail;
        Optional<User> exact = userRepository.findByPhone(identifier.trim());
        if (exact.isPresent()) return exact;
        try { return userRepository.findByPhone(PhoneNumbers.canonical(identifier)); }
        catch (com.example.bakir_khata.exception.BusinessRuleException ex) { return Optional.empty(); }
    }
}
