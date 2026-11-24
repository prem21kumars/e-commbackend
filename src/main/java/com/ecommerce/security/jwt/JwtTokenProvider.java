package com.ecommerce.security.jwt;

import com.ecommerce.security.principal.UserPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enterprise JWT token generator and parser.
 * Issues access tokens, cryptographically bound refresh tokens, and temporary MFA pre-auth tokens.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secretHex,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs,
            @Value("${app.jwt.issuer:ecommerce-api}") String issuer
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(ensureBase64(secretHex));
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.issuer = issuer;
    }

    private static String ensureBase64(String secret) {
        // Fallback safe decoding for 256-bit keys
        try {
            Decoders.BASE64.decode(secret);
            return secret;
        } catch (Exception e) {
            return java.util.Base64.getEncoder().encodeToString(secret.getBytes());
        }
    }

    public String generateAccessToken(UserPrincipal principal) {
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("email", principal.getEmail())
                .claim("roles", roles)
                .claim("mfa_pending", false)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generatePreAuthToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 300_000); // 5 minutes

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("mfa_pending", true)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generatePasswordResetToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 900_000); // 15 minutes

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("token_type", "PASSWORD_RESET")
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean isPasswordResetToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get("token_type", String.class);
            return "PASSWORD_RESET".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public boolean isPreAuthToken(String token) {
        Claims claims = getClaims(token);
        Boolean pending = claims.get("mfa_pending", Boolean.class);
        return Boolean.TRUE.equals(pending);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.warn("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}

