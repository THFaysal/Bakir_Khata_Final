package com.example.bakir_khata.service.impl;

import com.example.bakir_khata.dto.NotificationDTO;
import com.example.bakir_khata.model.Notification;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.exception.UnauthorizedActionException;
import com.example.bakir_khata.repository.NotificationRepository;
import com.example.bakir_khata.repository.TransactionRepository;
import com.example.bakir_khata.model.enums.TransactionStatus;
import com.example.bakir_khata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final TransactionRepository transactionRepository;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void notify(User recipient, String type, String message, Long relatedTransactionId) {
        Notification notification = notificationRepository.save(Notification.builder()
                .recipient(recipient).type(type).message(message).relatedTransactionId(relatedTransactionId).build());
        push(recipient.getId(), notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getForUser(User user) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(n -> new NotificationDTO(n.getId(), n.getMessage(), n.getType(), n.getRelatedTransactionId(), isActionableCashRequest(n, user), n.isRead(), n.getCreatedAt()))
                .toList();
    }

    private boolean isActionableCashRequest(Notification notification, User user) {
        if (!"CASH_PAYMENT_REQUEST".equals(notification.getType()) || notification.getRelatedTransactionId() == null) return false;
        return transactionRepository.findById(notification.getRelatedTransactionId())
                .filter(t -> t.getCounterpartyUser() != null && t.getCounterpartyUser().getId().equals(user.getId()))
                .map(t -> t.getStatus() == TransactionStatus.AWAITING_LENDER_CONFIRMATION || t.getStatus() == TransactionStatus.PENDING)
                .orElse(false);
    }

    @Override public long unreadCount(User user) { return notificationRepository.countByRecipientIdAndIsReadFalse(user.getId()); }

    @Override @Transactional
    public void markRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getRecipient().getId().equals(user.getId())) throw new UnauthorizedActionException("This notification does not belong to you");
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    public SseEmitter subscribe(User user) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        emitters.computeIfAbsent(user.getId(), k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> {
            var list = emitters.get(user.getId());
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emitters.remove(user.getId(), list);
            }
        };

        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException ignored) {
            remove.run();
        }
        return emitter;
    }

    @Override
    public void disconnect(Long userId) {
        if (userId == null) return;
        var list = emitters.remove(userId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try { emitter.complete(); } catch (Exception ignored) { }
        }
    }

    private void push(Long userId, Notification notification) {
        var list = emitters.get(userId);
        if (list == null) return;
        String payload = notification.getType() + "|" + notification.getMessage().replace("|", " ") + "|" + (notification.getRelatedTransactionId() == null ? "" : notification.getRelatedTransactionId());
        for (SseEmitter emitter : list) {
            try { emitter.send(SseEmitter.event().name("notification").data(payload)); }
            catch (IOException ex) { list.remove(emitter); }
        }
    }
}
