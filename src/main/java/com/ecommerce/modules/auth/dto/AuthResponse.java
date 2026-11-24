package com.ecommerce.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;

    @Builder.Default
    private boolean mfaRequired = false;

    private String preAuthToken;

    private UserSummaryDto user;

    public static AuthResponse mfaChallenge(String preAuthToken) {
        return AuthResponse.builder()
                .mfaRequired(true)
                .preAuthToken(preAuthToken)
                .build();
    }

    public static AuthResponse authenticated(String accessToken, String refreshToken, Long expiresIn, UserSummaryDto user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .mfaRequired(false)
                .user(user)
                .build();
    }
}

