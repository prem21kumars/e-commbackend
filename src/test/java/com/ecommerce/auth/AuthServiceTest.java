package com.ecommerce.auth;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.UnauthorizedException;
import com.ecommerce.common.utility.HashUtils;
import com.ecommerce.modules.auth.dto.*;
import com.ecommerce.modules.auth.entity.*;
import com.ecommerce.modules.auth.repository.*;
import com.ecommerce.modules.auth.service.AuthService;
import com.ecommerce.modules.auth.service.MfaService;
import com.ecommerce.security.jwt.JwtTokenProvider;
import com.ecommerce.security.mfa.TotpService;
import com.ecommerce.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private MfaSecretRepository mfaSecretRepository;
    @Mock
    private LoginHistoryRepository loginHistoryRepository;
    @Mock
    private OAuthAccountRepository oAuthAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private TotpService totpService;
    @Mock
    private MfaService mfaService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutDurationMinutes", 30);

        customerRole = Role.builder().id(1L).name("ROLE_CUSTOMER").build();

        sampleUser = User.builder()
                .id(100L)
                .email("shopper@example.com")
                .passwordHash("$argon2id$mockedhash")
                .firstName("John")
                .lastName("Doe")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .mfaEnabled(false)
                .roles(new HashSet<>(Set.of(customerRole)))
                .build();
    }

    @Test
    @DisplayName("Registration succeeds when email is unique")
    void testRegisterSuccess() {
        RegisterRequest req = RegisterRequest.builder()
                .email("new@example.com")
                .password("Password123!")
                .firstName("Alice")
                .lastName("Smith")
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(200L);
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access_token_123");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        AuthResponse res = authService.register(req, "127.0.0.1", "JUnit");

        assertNotNull(res);
        assertEquals("access_token_123", res.getAccessToken());
        assertNotNull(res.getRefreshToken());
        assertFalse(res.isMfaRequired());
        assertEquals("new@example.com", res.getUser().getEmail());

        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Registration throws ConflictException when email already exists")
    void testRegisterEmailConflict() {
        RegisterRequest req = RegisterRequest.builder()
                .email("shopper@example.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(userRepository.existsByEmail("shopper@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(req, "127.0.0.1", "JUnit"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Login succeeds with valid credentials when MFA is disabled")
    void testLoginSuccessWithoutMfa() {
        LoginRequest req = LoginRequest.builder()
                .email("shopper@example.com")
                .password("CorrectPassword123!")
                .build();

        when(userRepository.findByEmailWithRolesAndPermissions("shopper@example.com"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("CorrectPassword123!", "$argon2id$mockedhash")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access_token_valid");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        AuthResponse res = authService.login(req, "127.0.0.1", "JUnit");

        assertNotNull(res);
        assertEquals("access_token_valid", res.getAccessToken());
        assertFalse(res.isMfaRequired());
        assertEquals(0, sampleUser.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("Login requires MFA challenge when user has MFA enabled")
    void testLoginWithMfaChallenge() {
        sampleUser.setMfaEnabled(true);
        LoginRequest req = LoginRequest.builder()
                .email("shopper@example.com")
                .password("CorrectPassword123!")
                .build();

        when(userRepository.findByEmailWithRolesAndPermissions("shopper@example.com"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("CorrectPassword123!", "$argon2id$mockedhash")).thenReturn(true);
        when(jwtTokenProvider.generatePreAuthToken(100L)).thenReturn("pre_auth_mfa_token");

        AuthResponse res = authService.login(req, "127.0.0.1", "JUnit");

        assertNotNull(res);
        assertTrue(res.isMfaRequired());
        assertEquals("pre_auth_mfa_token", res.getPreAuthToken());
        assertNull(res.getAccessToken()); // Full session token MUST NOT be issued
    }

    @Test
    @DisplayName("Login increments failed attempts and throws BadCredentialsException on password mismatch")
    void testLoginBadCredentials() {
        LoginRequest req = LoginRequest.builder()
                .email("shopper@example.com")
                .password("WrongPassword")
                .build();

        when(userRepository.findByEmailWithRolesAndPermissions("shopper@example.com"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "$argon2id$mockedhash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(req, "127.0.0.1", "JUnit"));
        assertEquals(1, sampleUser.getFailedLoginAttempts());
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("Account is locked after reaching max failed login attempts")
    void testAccountLockout() {
        sampleUser.setFailedLoginAttempts(4); // 4 prior failures
        LoginRequest req = LoginRequest.builder()
                .email("shopper@example.com")
                .password("WrongPasswordAgain")
                .build();

        when(userRepository.findByEmailWithRolesAndPermissions("shopper@example.com"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPasswordAgain", "$argon2id$mockedhash")).thenReturn(false);

        assertThrows(LockedException.class, () -> authService.login(req, "127.0.0.1", "JUnit"));
        assertEquals(5, sampleUser.getFailedLoginAttempts());
        assertEquals(UserStatus.LOCKED, sampleUser.getStatus());
        assertNotNull(sampleUser.getLockoutUntil());
    }

    @Test
    @DisplayName("Refresh token rotation detects compromised token reuse and invalidates all user sessions")
    void testRefreshTokenReuseAttackDetection() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(1L)
                .user(sampleUser)
                .tokenHash(HashUtils.sha256("stolen_token"))
                .isRevoked(true) // Already used/revoked
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByTokenHash(HashUtils.sha256("stolen_token")))
                .thenReturn(Optional.of(revokedToken));

        RefreshTokenRequest req = RefreshTokenRequest.builder()
                .refreshToken("stolen_token")
                .build();

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(req, "127.0.0.1", "JUnit"));

        // Security check: Verify all tokens for this user were revoked immediately!
        verify(refreshTokenRepository).revokeAllUserTokens(sampleUser.getId());
    }
}

