package com.ecommerce.modules.notification.service;

import com.ecommerce.config.RabbitMqConfig;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.notification.dto.NotificationEventDto;
import com.ecommerce.modules.notification.entity.Notification;
import com.ecommerce.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RabbitTemplate rabbitTemplate;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void publishNotificationEvent(NotificationEventDto event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.NOTIFICATION_EXCHANGE,
                    RabbitMqConfig.NOTIFICATION_ROUTING_KEY,
                    event
            );
            log.info("Dispatched notification event [{}] to recipient {}", event.getNotificationType(), event.getRecipientEmail());
        } catch (Exception e) {
            log.warn("RabbitMQ unavailable, falling back to synchronous notification persistence: {}", e.getMessage());
            processNotificationSynchronously(event);
        }
    }

    @Transactional
    public void processNotificationSynchronously(NotificationEventDto event) {
        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null) {
            log.warn("Cannot process notification: user id {} not found", event.getUserId());
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .type(event.getNotificationType())
                .channel("EMAIL")
                .subject(event.getSubject())
                .recipient(event.getRecipientEmail())
                .content("Hello " + event.getRecipientName() + ",\n\n" + event.getSubject())
                .status("SENT")
                .sentAt(Instant.now())
                .build();
        notificationRepository.save(notification);
    }
}

