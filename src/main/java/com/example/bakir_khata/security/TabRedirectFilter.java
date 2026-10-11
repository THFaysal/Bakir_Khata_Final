package com.example.bakir_khata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Keep the tab selector across Spring MVC redirect: URLs and login redirects. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TabRedirectFilter extends OncePerRequestFilter {
    public static String withTab(String url, String id) {
        if (id == null || url == null || url.isBlank() || url.contains("__tab=")) return url;
        // Never attach even a tab selector to external URLs, logout/login, or payment-provider callbacks.
        if (!url.startsWith("/") || url.startsWith("//")) return url;
        String path = url.split("[?#]", 2)[0];
        if (path.equals("/login") || path.equals("/admin/login") || path.equals("/register")
                || path.startsWith("/forgot-password") || path.startsWith("/reset-password")
                || path.startsWith("/receipts/verify") || path.startsWith("/payments/sslcommerz/")) return url;
        int hash = url.indexOf('#');
        String fragment = hash < 0 ? "" : url.substring(hash);
        String base = hash < 0 ? url : url.substring(0, hash);
        return base + (base.contains("?") ? "&" : "?") + "__tab=" + id + fragment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Referrer-Policy", "no-referrer");
        String path = request.getRequestURI();
        // Only verified public static assets may be cached. Uploaded evidence, health,
        // financial pages, receipts, authentication and messaging remain no-store.
        if (!path.startsWith("/css/") && !path.startsWith("/js/") && !path.startsWith("/branding/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        }
        String id = TabSecurityContextRepository.tabId(request);
        if (id == null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override public void sendRedirect(String location) throws IOException {
                super.sendRedirect(withTab(location, id));
            }
            @Override public void setHeader(String name, String value) {
                super.setHeader(name, "Location".equalsIgnoreCase(name) ? withTab(value, id) : value);
            }
            @Override public void addHeader(String name, String value) {
                super.addHeader(name, "Location".equalsIgnoreCase(name) ? withTab(value, id) : value);
            }
        });
    }
}
