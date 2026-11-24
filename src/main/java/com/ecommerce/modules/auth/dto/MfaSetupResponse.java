package com.ecommerce.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {
    private String secretKey;
    private String qrCodeDataUri;
    private List<String> recoveryCodes;
    private String manualSetupKey;
}

