package com.ecommerce.modules.auth.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.utility.HashUtils;
import com.ecommerce.modules.auth.dto.MfaSetupResponse;
import com.ecommerce.modules.auth.entity.MfaRecoveryCode;
import com.ecommerce.modules.auth.entity.MfaSecret;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.MfaRecoveryCodeRepository;
import com.ecommerce.modules.auth.repository.MfaSecretRepository;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.security.mfa.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private final MfaSecretRepository mfaSecretRepository;
    private final MfaRecoveryCodeRepository mfaRecoveryCodeRepository;
    private final UserRepository userRepository;
    private final TotpService totpService;

    @Transactional
    public MfaSetupResponse initiateMfaSetup(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Generate new TOTP secret key
        String secretKey = totpService.generateSecret();

        // Remove old unverified secret if any
        mfaSecretRepository.findByUserId(userId).ifPresent(mfaSecretRepository::delete);

        MfaSecret mfaSecret = MfaSecret.builder()
                .user(user)
                .secretKey(secretKey)
                .isVerified(false)
                .build();
        mfaSecretRepository.save(mfaSecret);

        String qrCodeDataUri = totpService.generateQrCodeDataUri(user.getEmail(), secretKey);

        return MfaSetupResponse.builder()
                .secretKey(secretKey)
                .manualSetupKey(secretKey)
                .qrCodeDataUri(qrCodeDataUri)
                .build();
    }

    @Transactional
    public List<String> verifyAndActivateMfa(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        MfaSecret mfaSecret = mfaSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("MFA setup was not initiated for this user"));

        if (!totpService.verifyCode(mfaSecret.getSecretKey(), code)) {
            throw new BadRequestException("Invalid TOTP verification code. Please check your authenticator clock.");
        }

        mfaSecret.setVerified(true);
        mfaSecretRepository.save(mfaSecret);

        user.setMfaEnabled(true);
        userRepository.save(user);

        // Generate and persist 8 single-use recovery codes
        mfaRecoveryCodeRepository.deleteByUserId(userId);
        List<String> plainRecoveryCodes = totpService.generateRecoveryCodes(8);

        List<MfaRecoveryCode> recoveryEntities = plainRecoveryCodes.stream()
                .map(plainCode -> MfaRecoveryCode.builder()
                        .user(user)
                        .codeHash(HashUtils.sha256(plainCode.replace("-", "").toUpperCase()))
                        .isUsed(false)
                        .build())
                .toList();

        mfaRecoveryCodeRepository.saveAll(recoveryEntities);
        log.info("MFA successfully enabled and activated for user id={}", userId);

        return plainRecoveryCodes;
    }

    @Transactional
    public boolean verifyRecoveryCode(Long userId, String recoveryCode) {
        String normalized = recoveryCode.replace("-", "").toUpperCase();
        String codeHash = HashUtils.sha256(normalized);

        List<MfaRecoveryCode> unusedCodes = mfaRecoveryCodeRepository.findByUserIdAndIsUsedFalse(userId);
        for (MfaRecoveryCode mfaCode : unusedCodes) {
            if (mfaCode.getCodeHash().equals(codeHash)) {
                mfaCode.setUsed(true);
                mfaCode.setUsedAt(Instant.now());
                mfaRecoveryCodeRepository.save(mfaCode);
                log.info("User id={} successfully used an MFA recovery code", userId);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void disableMfa(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        MfaSecret mfaSecret = mfaSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("MFA is not enabled for this user"));

        if (!totpService.verifyCode(mfaSecret.getSecretKey(), code)) {
            throw new BadRequestException("Invalid TOTP code. Cannot disable MFA.");
        }

        mfaRecoveryCodeRepository.deleteByUserId(userId);
        mfaSecretRepository.deleteByUserId(userId);

        user.setMfaEnabled(false);
        userRepository.save(user);
        log.info("MFA disabled for user id={}", userId);
    }
}

