package com.ecommerce.modules.auth.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.auth.dto.*;
import com.ecommerce.modules.auth.service.AuthService;
import com.ecommerce.modules.auth.service.MfaService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & Authorization", description = "Endpoints for customer registration, authentication, TOTP MFA, refresh-token rotation, and OAuth2")
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;

    @PostMapping("/register")
    @Operation(summary = "Customer Registration", description = "Register a new customer account with Argon2id password encryption")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.register(request, ipAddress, userAgent);
        return new ResponseEntity<>(ApiResponse.success("Account registered successfully", response), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "User Login", description = "Authenticate using email and password with brute-force lockout protection")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Verify MFA Challenge", description = "Verify 6-digit TOTP code during login challenge")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.verifyMfa(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("MFA verified successfully", response));
    }

    @PostMapping("/mfa/recovery")
    @Operation(summary = "Verify MFA with Recovery Code", description = "Authenticate using single-use emergency backup recovery code")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfaWithRecovery(@Valid @RequestBody MfaRecoveryRequest request, HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.verifyMfaWithRecovery(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Recovery code accepted. Session granted.", response));
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiate MFA Setup", description = "Generates new TOTP secret and QR code for Google Authenticator")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> initiateMfaSetup(@AuthenticationPrincipal UserPrincipal principal) {
        MfaSetupResponse response = mfaService.initiateMfaSetup(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("MFA setup initiated", response));
    }

    @PostMapping("/mfa/activate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Activate MFA", description = "Confirms TOTP setup and returns one-time recovery codes")
    public ResponseEntity<ApiResponse<List<String>>> activateMfa(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @RequestParam String code) {
        List<String> recoveryCodes = mfaService.verifyAndActivateMfa(principal.getId(), code);
        return ResponseEntity.ok(ApiResponse.success("MFA successfully activated. Store recovery codes safely.", recoveryCodes));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Rotate Refresh Token", description = "Exchanges valid refresh token for new access and rotated refresh tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.refreshToken(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes refresh token and terminates session")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(ApiResponse.messageOnly("Logged out successfully"));
    }

    @PostMapping("/google")
    @Operation(summary = "Google OAuth2 Login", description = "Authenticate or register via Google OpenID Connect ID token")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.googleLogin(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Google login completed", response));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot Password", description = "Initiates password reset process and generates recovery instructions/token")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String tokenOrMsg = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Recovery instructions processed successfully", tokenOrMsg));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset Password", description = "Validates recovery token and updates user password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.messageOnly("Password reset successfully. You may now log in with your new password."));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change Password", description = "Allows authenticated user to update their account password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.messageOnly("Password changed successfully"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

