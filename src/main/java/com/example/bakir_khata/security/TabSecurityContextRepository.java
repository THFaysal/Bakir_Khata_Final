package com.example.bakir_khata.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Keeps multiple authenticated web contexts in the SAME HttpSession, indexed by
 * an unpredictable, non-JWT per-tab selector. The browser cookie still anchors
 * each context: copying the selector to another browser does not authorize it.
 *
 * The URL selector is NOT a standalone bearer token. JavaScript cannot set a
 * custom header on a normal full-page GET, hence the selector travels in URLs,
 * and response Referrer-Policy must be no-referrer. Do not put API JWTs in URLs.
 */
@Component
public final class TabSecurityContextRepository implements SecurityContextRepository {
    public static final String PARAMETER = "__tab";
    private static final Pattern ID = Pattern.compile("[a-f0-9]{32}");
    private static final String PREFIX = "BK_TAB_AUTH_";
    private static final SecurityContextRepository ANONYMOUS = new NullSecurityContextRepository();

    public static String tabId(HttpServletRequest request) {
        String id = request.getParameter(PARAMETER);
        return id != null && ID.matcher(id).matches() ? id : null;
    }

    private SecurityContextRepository delegate(HttpServletRequest request) {
        String id = tabId(request);
        if (id == null) return ANONYMOUS;
        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.setSpringSecurityContextKey(PREFIX + id);
        return repo;
    }

    @Override @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        return delegate(holder.getRequest()).loadContext(holder);
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        return delegate(request).loadDeferredContext(request);
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        delegate(request).saveContext(context, request, response);
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return delegate(request).containsContext(request);
    }

    /** Log out/revoke ONLY the tab identified in the current form. */
    public void clearTab(HttpServletRequest request) {
        String id = tabId(request);
        HttpSession session = request.getSession(false);
        if (id != null && session != null) session.removeAttribute(PREFIX + id);
    }
}
