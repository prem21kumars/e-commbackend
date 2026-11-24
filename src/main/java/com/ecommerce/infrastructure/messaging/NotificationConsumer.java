package com.ecommerce.infrastructure.messaging;

import com.ecommerce.config.RabbitMqConfig;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.notification.dto.NotificationEventDto;
import com.ecommerce.modules.notification.entity.Notification;
import com.ecommerce.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void consumeNotificationEvent(NotificationEventDto event) {
        log.info("Received notification queue event: type={}, recipient={}",
                event.getNotificationType(), event.getRecipientEmail());

        try {
            User user = userRepository.findById(event.getUserId()).orElse(null);
            if (user == null) {
                log.warn("Discarding notification: User id {} does not exist", event.getUserId());
                return;
            }

            // In production, JavaMailSender or SMTP API delivers email here
            Notification notification = Notification.builder()
                    .user(user)
                    .type(event.getNotificationType())
                    .channel("EMAIL")
                    .subject(event.getSubject())
                    .recipient(event.getRecipientEmail())
                    .content("Dear " + event.getRecipientName() + ",\n\n" + event.getSubject())
                    .status("SENT")
                    .sentAt(Instant.now())
                    .build();
            notificationRepository.save(notification);

            log.info("Successfully dispatched and logged notification to {}", event.getRecipientEmail());
        } catch (Exception e) {
            log.error("Failed to process notification event for {}", event.getRecipientEmail(), e);
            throw e; // Triggers RabbitMQ retry & DLQ routing
        }
    }
}

