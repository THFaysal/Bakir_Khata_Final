package com.example.bakir_khata.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/** JWT keys are created once by Spring and injected; never call an @Bean method directly. */
@Configuration(proxyBeanMethods = false)
public class JwtCryptoConfig {
    private static final Logger log = LoggerFactory.getLogger(JwtCryptoConfig.class);

    @Bean("jwtSigningKey")
    public SecretKey jwtSigningKey(@Value("${app.jwt.secret:}") String encoded,
                                   @Value("${spring.profiles.active:dev}") String profile) {
        byte[] bytes;
        if (encoded.isBlank()) {
            if (profile.toLowerCase(java.util.Locale.ROOT).contains("prod")) {
                throw new IllegalStateException("APP_JWT_SECRET must be set for production (base64, >= 32 bytes)");
            }
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("Development JWT secret generated for this process; tokens expire when restarted. Set APP_JWT_SECRET.");
        } else {
            try { bytes = Base64.getDecoder().decode(encoded); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("APP_JWT_SECRET must be base64", ex); }
            if (bytes.length < 32) throw new IllegalStateException("APP_JWT_SECRET needs at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(@Qualifier("jwtSigningKey") SecretKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Qualifier("jwtSigningKey") SecretKey signingKey,
                                 @Value("${app.jwt.issuer:bakir-khata}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
