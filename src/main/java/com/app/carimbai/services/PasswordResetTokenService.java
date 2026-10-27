package com.app.carimbai.services;

import com.app.carimbai.models.PasswordResetToken;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.PasswordResetTokenRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.OpaqueTokens;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;

/**
 * Cria e consome tokens one-shot para reset de senha do staff.
 *
 * Mesmo padrao do RefreshTokenService:
 *  - 32 bytes aleatorios (SecureRandom) → base64url para o cliente.
 *  - Apenas o SHA-256 vai pro banco; vazamento do banco nao expoe tokens.
 *  - `consume()` valida e marca `used_at = NOW()` na mesma chamada (one-shot).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final PasswordResetTokenRepository repo;
    private final StaffUserRepository staffRepo;
    private final com.app.carimbai.security.NetworkMetadata metadata;

    @Value("${carimbai.password-reset.token-ttl-hours:1}")
    private int tokenTtlHours;

    @Autowired(required = false)
    private HttpServletRequest request;

    public record IssuedToken(String rawToken, PasswordResetToken persisted) {}

    @Transactional
    public IssuedToken issue(StaffUser staff) {
        staffRepo.findByIdForUpdate(staff.getId())
                .orElseThrow(() -> new PasswordResetTokenInvalidException("Invalid password reset token"));
        repo.invalidateForStaff(staff.getId(), OffsetDateTime.now());
        String raw = generateRawToken();
        String hash = sha256(raw);

        PasswordResetToken entity = PasswordResetToken.builder()
                .tokenHash(hash)
                .staffUser(staff)
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusHours(tokenTtlHours))
                .ip(resolveIp())
                .userAgent(resolveUserAgent())
                .build();

        entity = repo.saveAndFlush(entity);
        return new IssuedToken(raw, entity);
    }

    /**
     * Valida o token cru e marca como consumido. Retorna o staff alvo.
     * Lanca PasswordResetTokenInvalidException em qualquer falha:
     *  - token nao existe (nunca emitido ou hash diferente)
     *  - token ja foi usado
     *  - token expirou
     *  - staff foi desativado
     */
    @Transactional
    public StaffUser consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw new PasswordResetTokenInvalidException("Token missing");
        }
        String hash = sha256(rawToken);
        Long staffId = repo.findStaffIdByTokenHash(hash)
                .orElseThrow(() -> new PasswordResetTokenInvalidException("Invalid password reset token"));
        StaffUser staff = staffRepo.findByIdForUpdate(staffId)
                .orElseThrow(() -> new PasswordResetTokenInvalidException("Invalid password reset token"));
        PasswordResetToken token = repo.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new PasswordResetTokenInvalidException("Token not found"));

        if (token.getUsedAt() != null) {
            throw new PasswordResetTokenInvalidException("Token already used");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new PasswordResetTokenInvalidException("Token expired");
        }
        if (!Boolean.TRUE.equals(staff.getActive())) {
            throw new PasswordResetTokenInvalidException("Staff user is inactive");
        }

        token.setUsedAt(OffsetDateTime.now());
        repo.saveAndFlush(token);

        return staff;
    }

    private String generateRawToken() {
        return OpaqueTokens.generate();
    }

    private String sha256(String input) {
        return OpaqueTokens.hash(input);
    }

    private InetAddress resolveIp() {
        if (request == null) return null;
        try {
            return metadata.ip(request);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private String resolveUserAgent() {
        if (request == null) return null;
        try {
            return metadata.userAgent(request.getHeader("User-Agent"));
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public static class PasswordResetTokenInvalidException extends RuntimeException {
        public PasswordResetTokenInvalidException(String msg) {
            super(msg);
        }
    }
}
