package com.ecommerce.security.mfa;

import com.ecommerce.common.exception.BadRequestException;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Multi-Factor Authentication Service.
 * Implements RFC-6238 TOTP algorithm, QR code rendering, and recovery code generation.
 */
@Slf4j
@Service
public class TotpService {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService() {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setAllowedTimePeriodDiscrepancy(1); // 1-step window (±30s) for clock skew tolerance
        this.codeVerifier = verifier;
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String generateQrCodeDataUri(String email, String secret) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer("OO Toys E-Commerce")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] imageData = qrGenerator.generate(data);
            return Utils.getDataUriForImage(imageData, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code for MFA setup", e);
            throw new BadRequestException("Unable to generate MFA QR Code: " + e.getMessage());
        }
    }

    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    public List<String> generateRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Base32 unambiguous charset

        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder(10);
            for (int j = 0; j < 8; j++) {
                sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            sb.insert(4, "-"); // Format: XXXX-XXXX
            codes.add(sb.toString());
        }
        return codes;
    }
}

