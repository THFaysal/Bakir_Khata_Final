package com.example.bakir_khata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Fail closed if JavaScript didn't attach a secure random browser tab ID on login. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 9)
public class TabLoginGuardFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getRequestURI())
                && TabSecurityContextRepository.tabId(request) == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "A secure browser tab identifier is required. Enable JavaScript and reload /login.");
            return;
        }
        chain.doFilter(request, response);
    }
}
