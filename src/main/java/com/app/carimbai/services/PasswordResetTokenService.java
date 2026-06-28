package com.app.carimbai.services;

import com.app.carimbai.models.PasswordResetToken;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.PasswordResetTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Objects;

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

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository repo;

    @Value("${carimbai.password-reset.token-ttl-hours:1}")
    private int tokenTtlHours;

    @Autowired(required = false)
    private HttpServletRequest request;

    public record IssuedToken(String rawToken, PasswordResetToken persisted) {}

    @Transactional
    public IssuedToken issue(StaffUser staff) {
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

        entity = repo.save(entity);
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
        if (rawToken == null || rawToken.isBlank()) {
            throw new PasswordResetTokenInvalidException("Token missing");
        }
        String hash = sha256(rawToken);
        PasswordResetToken token = repo.findByTokenHash(hash)
                .orElseThrow(() -> new PasswordResetTokenInvalidException("Token not found"));

        if (token.getUsedAt() != null) {
            throw new PasswordResetTokenInvalidException("Token already used");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new PasswordResetTokenInvalidException("Token expired");
        }
        StaffUser staff = token.getStaffUser();
        if (Boolean.FALSE.equals(staff.getActive())) {
            throw new PasswordResetTokenInvalidException("Staff user is inactive");
        }

        token.setUsedAt(OffsetDateTime.now());
        repo.save(token);

        return staff;
    }

    private String generateRawToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Objects.requireNonNullElse(input, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private InetAddress resolveIp() {
        if (request == null) return null;
        try {
            String forwarded = request.getHeader("X-Forwarded-For");
            String raw = (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr();
            return raw != null && !raw.isBlank() ? InetAddress.getByName(raw) : null;
        } catch (UnknownHostException | IllegalStateException e) {
            return null;
        }
    }

    private String resolveUserAgent() {
        if (request == null) return null;
        try {
            return request.getHeader("User-Agent");
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
