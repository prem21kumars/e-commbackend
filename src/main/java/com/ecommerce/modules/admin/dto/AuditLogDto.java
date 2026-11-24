package com.ecommerce.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    private String entityName;
    private String entityId;
    private String action;
    private String performedBy;
    private String ipAddress;
    private String oldState;
    private String newState;
    private String correlationId;
    private Instant timestamp;
}

