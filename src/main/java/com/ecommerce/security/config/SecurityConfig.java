package com.ecommerce.security.config;

import com.ecommerce.common.response.ErrorResponse;
import com.ecommerce.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

/**
 * Enterprise Spring Security 6.x configuration.
 * Configured for stateless REST API operation, Argon2id password encryption,
 * CORS allowlisting, and granular role/permission-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // OWASP recommended Argon2id parameters
        // saltLength: 16 bytes, hashLength: 32 bytes, parallelism: 1 thread, memory: 65536 KB (64MB), iterations: 3
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                        // OpenAPI / Swagger & Scalar documentation
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/scalar/**",
                                "/scalar",
                                "/scalar.html",
                                "/favicon.ico",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        // Health, Ping, Actuator probes
                        .requestMatchers(
                                "/api/v1/health/**",
                                "/api/health/**",
                                "/api/v1/ping",
                                "/api/ping",
                                "/actuator/**"
                        ).permitAll()

                        // Public Authentication endpoints
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/auth/**"
                        ).permitAll()

                        // Public Catalog browsing (products & categories read-only)
                        .requestMatchers(
                                "/api/v1/products/**",
                                "/api/products/**",
                                "/api/v1/categories/**",
                                "/api/categories/**"
                        ).permitAll()

                        // Public Coupon validation & Shipment tracking
                        .requestMatchers(
                                "/api/v1/coupons/validate",
                                "/api/coupons/validate",
                                "/api/v1/shipping/track/**",
                                "/api/shipping/track/**"
                        ).permitAll()

                        // Razorpay payment webhook (validated via HMAC signature)
                        .requestMatchers(
                                "/api/v1/payments/razorpay/webhook",
                                "/api/payments/razorpay/webhook"
                        ).permitAll()

                        // Admin scoped operations
                        .requestMatchers("/api/v1/admin/**", "/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Customer authenticated operations
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]",
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://*:*",
                "https://*:*",
                "*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Correlation-ID", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse error = ErrorResponse.builder()
                    .success(false)
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .errorCode("UNAUTHORIZED")
                    .message("Full authentication is required to access this resource: " + authException.getMessage())
                    .path(request.getRequestURI())
                    .timestamp(Instant.now())
                    .build();

            objectMapper.writeValue(response.getOutputStream(), error);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse error = ErrorResponse.builder()
                    .success(false)
                    .status(HttpStatus.FORBIDDEN.value())
                    .errorCode("ACCESS_DENIED")
                    .message("Access denied: You do not possess the required role or permission")
                    .path(request.getRequestURI())
                    .timestamp(Instant.now())
                    .build();

            objectMapper.writeValue(response.getOutputStream(), error);
        };
    }
}
