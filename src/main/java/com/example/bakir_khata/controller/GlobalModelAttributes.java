package com.example.bakir_khata.controller;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.service.NotificationService;
import com.example.bakir_khata.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {
    private final UserService userService;
    private final NotificationService notificationService;

    @ModelAttribute("currentUser")
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        try { return userService.getUserByEmail(auth.getName()); } catch (Exception e) { return null; }
    }

    @ModelAttribute("notificationCount")
    public long notificationCount() {
        User user = currentUser();
        return user == null ? 0 : notificationService.unreadCount(user);
    }
}
