package com.ecommerce.auth;

import com.ecommerce.security.mfa.TotpService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TotpService Unit Tests")
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    @DisplayName("Generates valid non-empty Base32 secret")
    void testSecretGeneration() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.length() >= 16);
    }

    @Test
    @DisplayName("Generates QR code PNG data URI with correct header")
    void testQrCodeGeneration() {
        String secret = totpService.generateSecret();
        String qrDataUri = totpService.generateQrCodeDataUri("user@example.com", secret);

        assertNotNull(qrDataUri);
        assertTrue(qrDataUri.startsWith("data:image/png;base64,"));
    }

    @Test
    @DisplayName("Generates formatted single-use recovery codes")
    void testRecoveryCodesGeneration() {
        List<String> codes = totpService.generateRecoveryCodes(8);

        assertEquals(8, codes.size());
        for (String code : codes) {
            assertNotNull(code);
            assertEquals(9, code.length()); // Format: XXXX-XXXX (4 + 1 + 4 = 9)
            assertTrue(code.contains("-"));
        }
    }
}

