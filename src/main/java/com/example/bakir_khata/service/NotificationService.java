package com.example.bakir_khata.service;

import com.example.bakir_khata.dto.NotificationDTO;
import com.example.bakir_khata.model.User;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {
    void notify(User recipient, String type, String message, Long relatedTransactionId);
    List<NotificationDTO> getForUser(User user);
    long unreadCount(User user);
    void markRead(Long notificationId, User user);
    SseEmitter subscribe(User user);
    void disconnect(Long userId);
}
