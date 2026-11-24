package com.ecommerce.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryRequest {

    @NotBlank(message = "Pre-auth token is required")
    private String preAuthToken;

    @NotBlank(message = "Recovery code is required")
    private String recoveryCode;
}

