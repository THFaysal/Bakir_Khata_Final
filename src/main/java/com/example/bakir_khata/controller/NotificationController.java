package com.example.bakir_khata.controller;

import com.example.bakir_khata.model.User;
import com.example.bakir_khata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public String notifications(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("notifications", notificationService.getForUser(user));
        return "notifications/index";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable Long id, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        notificationService.markRead(id, user);
        return "redirect:/notifications";
    }

    @GetMapping("/notifications/stream")
    public SseEmitter stream(@AuthenticationPrincipal User user) {
        return notificationService.subscribe(user);
    }
}
