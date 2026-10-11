package com.example.bakir_khata.config;

import com.example.bakir_khata.security.MobileOrEmailAuthenticationProvider;
import com.example.bakir_khata.security.jwt.JwtSecurity;
import com.example.bakir_khata.security.WebSessionRefreshFilter;
import com.example.bakir_khata.security.TabSecurityContextRepository;
import com.example.bakir_khata.security.TabRedirectFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.Role;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtSecurity jwtSecurity;
    private final WebSessionRefreshFilter sessionRefreshFilter;
    private final TabSecurityContextRepository tabContexts;

    // JSON APIs require an independently verified bearer JWT; web-session cookies alone
    // cannot authorize state changes on the API chain. This prevents CSRF on /api/**.
    @Bean @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        return http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/token").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/coordinator/**").hasAnyRole("COORDINATOR", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(jwtSecurity)))
                .build();
    }

    // Thymeleaf pages use tab-selected SecurityContexts INSIDE the shared HttpSession,
    // with CSRF enabled and session-id rotation on login.
    @Bean @Order(2)
    SecurityFilterChain webSecurity(HttpSecurity http, MobileOrEmailAuthenticationProvider provider) throws Exception {
        http.authenticationProvider(provider);
        // MVC defines bounded public cache headers for /css, /js and /branding.
        // All dynamic web routes receive no-store in TabRedirectFilter.
        http.headers(headers -> headers.cacheControl(cache -> cache.disable()));
        // Default JSESSIONID remains the browser anchor; each tab has its own authentication context.
        http.securityContext(context -> context.securityContextRepository(tabContexts).requireExplicitSave(true));
        http.addFilterAfter(sessionRefreshFilter, SecurityContextHolderFilter.class);
        http.csrf(csrf -> csrf.ignoringRequestMatchers(
                "/payments/sslcommerz/ipn", "/payments/sslcommerz/result"));
        http.sessionManagement(s -> s.sessionFixation(f -> f.migrateSession())
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/branding/**", "/uploads/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/", "/login", "/register", "/admin/login", "/forgot-password", "/reset-password", "/receipts/verify").permitAll()
                .requestMatchers("/payments/sslcommerz/ipn", "/payments/sslcommerz/result").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/coordinator/applications/**", "/coordinator/list").hasRole("ADMIN")
                .requestMatchers("/coordinator/dashboard", "/transactions/review", "/transactions/*/flag", "/management/**", "/moderation/**")
                    .hasAnyRole("COORDINATOR", "ADMIN")
                .requestMatchers("/coordinator/apply/**").hasRole("USER")
                .requestMatchers("/lenders/**", "/loans/**", "/payments/**", "/reminders/**", "/transactions/**", "/receipts/**").hasRole("USER")
                .requestMatchers("/community/**", "/messenger/**", "/subscription/**", "/verification/**").hasRole("USER")
                .anyRequest().authenticated());
        http.formLogin(form -> form.securityContextRepository(tabContexts).loginPage("/login").loginProcessingUrl("/login")
                .usernameParameter("identifier").passwordParameter("credential")
                .successHandler((request,response,auth)-> {
                    User user=(User)auth.getPrincipal();
                    String target=user.getRole()==Role.ADMIN?"/admin":
                            user.getRole()==Role.COORDINATOR?"/coordinator/dashboard":"/dashboard";
                    response.sendRedirect(TabRedirectFilter.withTab(target, TabSecurityContextRepository.tabId(request)));
                })
                .failureUrl("/login?error=true").permitAll());
        // Log out the current tab; a different tab's account is not logged out.
        http.logout(logout -> logout.logoutUrl("/logout").invalidateHttpSession(false)
                .addLogoutHandler((request,response,authentication) -> tabContexts.clearTab(request))
                .clearAuthentication(true)
                .logoutSuccessUrl("/login?logout=true").permitAll());
        return http.build();
    }

    // Prevent auto-registering the same filter outside the Spring Security chain.
    @Bean FilterRegistrationBean<WebSessionRefreshFilter> disableDirectFilterRegistration() {
        FilterRegistrationBean<WebSessionRefreshFilter> bean = new FilterRegistrationBean<>(sessionRefreshFilter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
