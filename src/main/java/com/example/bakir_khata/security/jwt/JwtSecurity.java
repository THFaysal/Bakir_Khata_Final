package com.example.bakir_khata.security.jwt;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Validates signed JWT claims against up-to-date account privileges. */
@Component
public class JwtSecurity implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;
    private final JwtEncoder encoder;
    private final String issuer;

    public JwtSecurity(UserRepository users, JwtEncoder encoder,
                       @Value("${app.jwt.issuer:bakir-khata}") String issuer) {
        this.users = users;
        this.encoder = encoder;
        this.issuer = issuer;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer)
                .subject(user.getId().toString()).issuedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.MINUTES))
                .claim("ver", user.getTokenVersion()).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        User user;
        try { user = users.findById(Long.valueOf(jwt.getSubject())).orElse(null); }
        catch (NumberFormatException ex) { user = null; }
        Object version = jwt.getClaims().get("ver");
        if (user == null || !user.isEnabled() || !user.isAccountNonLocked()
                || !(version instanceof Number) || ((Number) version).longValue() != user.getTokenVersion()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token"), "Session expired or account permissions changed");
        }
        return new UsernamePasswordAuthenticationToken(user, jwt, user.getAuthorities());
    }
}
