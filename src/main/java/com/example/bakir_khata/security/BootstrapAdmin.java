package com.example.bakir_khata.security;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.Role;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration @RequiredArgsConstructor
public class BootstrapAdmin {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    @Value("${app.bootstrap.admin-email:}") private String adminEmail;
    @Value("${app.bootstrap.admin-password:}") private String adminPassword;
    @Value("${app.bootstrap.admin-phone:}") private String adminPhone;
    @Bean ApplicationRunner createFirstAdmin() {
        return args -> {
            if (adminEmail.isBlank() && adminPassword.isBlank()) return;
            if (adminEmail.isBlank() || adminPassword.length()<14) throw new IllegalStateException("Admin bootstrap requires an email and password of at least 14 characters");
            if (users.countByRole(Role.ADMIN)>0) return;
            if (users.existsByEmail(adminEmail)) throw new IllegalStateException("Bootstrap email already exists: refusing privilege escalation");
            User admin=new User();admin.setName("System Admin");admin.setEmail(adminEmail.toLowerCase());
            admin.setPassword(encoder.encode(adminPassword));admin.setRole(Role.ADMIN);
            if (!adminPhone.isBlank()) {
                String canonical=PhoneNumbers.canonical(adminPhone);
                if (users.existsByPhone(canonical)) throw new IllegalStateException("Admin bootstrap phone is already in use");
                admin.setPhone(canonical);
            }
            users.save(admin);
        };
    }
}
