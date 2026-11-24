package com.ecommerce.auth;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.auth.controller.AuthController;
import com.ecommerce.modules.auth.dto.*;
import com.ecommerce.modules.auth.service.AuthService;
import com.ecommerce.modules.auth.service.MfaService;
import com.ecommerce.security.jwt.JwtAuthenticationFilter;
import com.ecommerce.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Web Layer Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MfaService mfaService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/v1/auth/register returns 201 Created on valid input")
    void registerShouldReturnCreated() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("test@example.com")
                .password("SecurePass123!")
                .firstName("Test")
                .lastName("User")
                .build();

        AuthResponse authResponse = AuthResponse.authenticated(
                "mock_jwt_access",
                "mock_refresh_token",
                900L,
                UserSummaryDto.builder()
                        .id(1L)
                        .email("test@example.com")
                        .firstName("Test")
                        .lastName("User")
                        .roles(Set.of("ROLE_CUSTOMER"))
                        .build()
        );

        when(authService.register(any(RegisterRequest.class), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock_jwt_access"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK on valid credentials")
    void loginShouldReturnOk() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .email("test@example.com")
                .password("SecurePass123!")
                .build();

        AuthResponse authResponse = AuthResponse.authenticated(
                "mock_jwt_access",
                "mock_refresh_token",
                900L,
                UserSummaryDto.builder()
                        .id(1L)
                        .email("test@example.com")
                        .firstName("Test")
                        .lastName("User")
                        .roles(Set.of("ROLE_CUSTOMER"))
                        .build()
        );

        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock_jwt_access"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 Bad Request on invalid email")
    void registerShouldFailOnInvalidEmail() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("not-an-email")
                .password("SecurePass123!")
                .firstName("Test")
                .lastName("User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}

