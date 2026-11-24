package com.ecommerce.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventDto implements Serializable {
    private Long userId;
    private String recipientEmail;
    private String recipientName;
    private String notificationType;
    private String subject;
    private String templateName;
    private Map<String, Object> templateModel;
}

