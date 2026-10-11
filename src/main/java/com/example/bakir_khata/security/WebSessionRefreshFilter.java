package com.example.bakir_khata.security;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class WebSessionRefreshFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final TabSecurityContextRepository contextRepository;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/branding/")
                || path.startsWith("/api/") || path.equals("/receipts/verify") || path.equals("/favicon.ico");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                                              FilterChain chain) throws ServletException,IOException {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.getPrincipal() instanceof User stored) {
            User fresh=users.findById(stored.getId()).orElse(null);
            if (fresh==null || !fresh.isEnabled() || !fresh.isAccountNonLocked()
                    || fresh.getTokenVersion()!=stored.getTokenVersion()) {
                SecurityContextHolder.clearContext();
                contextRepository.clearTab(request);
                response.sendRedirect("/login?expired=true");return;
            }
            SecurityContext context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(fresh,null,fresh.getAuthorities()));
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context,request,response);
        }
        chain.doFilter(request,response);
    }
}
