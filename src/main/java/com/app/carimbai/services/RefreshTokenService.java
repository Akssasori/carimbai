package com.app.carimbai.services;

import com.app.carimbai.models.RefreshToken;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.RefreshTokenRepository;
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
 * Gerencia o ciclo de vida de refresh tokens do staff.
 *
 * - `issue(staff, merchant)`: cria refresh novo (32 bytes aleatorios), salva o
 *   hash SHA-256 no banco, retorna o token em claro para o cliente. Cliente
 *   armazena o token; servidor so tem o hash.
 *
 * - `rotate(rawToken, ip, userAgent)`: valida + emite um novo + marca o antigo
 *   como revogado com `replaced_by_id` apontando para o sucessor.
 *
 * - `revoke(rawToken)`: invalida explicitamente (usado no logout).
 *
 * O resultado `IssuedToken` carrega tanto o `rawToken` (para o cliente) quanto
 * a entidade persistida (para quem precisa do staff/merchant pra emitir o JWT
 * de access).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository refreshRepo;

    @Value("${carimbai.jwt.refresh-token-days:30}")
    private int refreshTokenDays;

    @Autowired(required = false)
    private HttpServletRequest request;

    public record IssuedToken(String rawToken, RefreshToken persisted) {}

    /**
     * Emite um refresh token novo para (staff, merchant) ativo.
     */
    @Transactional
    public IssuedToken issue(StaffUser staff, Merchant merchant) {
        String rawToken = generateRawToken();
        String hash = sha256(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(hash)
                .staffUser(staff)
                .merchant(merchant)
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenDays))
                .ip(resolveIp())
                .userAgent(resolveUserAgent())
                .build();

        entity = refreshRepo.save(entity);
        return new IssuedToken(rawToken, entity);
    }

    /**
     * Valida um refresh token cru, rotaciona (cria novo + revoga atual). Lanca
     * RefreshTokenInvalidException se token nao existe, esta expirado ou ja
     * foi revogado.
     */
    @Transactional
    public IssuedToken rotate(String rawToken) {
        RefreshToken current = findValidOrThrow(rawToken);

        // Emite novo
        String newRaw = generateRawToken();
        String newHash = sha256(newRaw);
        RefreshToken next = RefreshToken.builder()
                .tokenHash(newHash)
                .staffUser(current.getStaffUser())
                .merchant(current.getMerchant())
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenDays))
                .ip(resolveIp())
                .userAgent(resolveUserAgent())
                .build();
        next = refreshRepo.save(next);

        // Revoga o atual com pointer para o sucessor
        current.setRevokedAt(OffsetDateTime.now());
        current.setReplacedById(next.getId());
        refreshRepo.save(current);

        return new IssuedToken(newRaw, next);
    }

    /**
     * Revoga o token cru passado. Idempotente: nao falha se token nao existe.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String hash = sha256(rawToken);
        refreshRepo.findByTokenHash(hash).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(OffsetDateTime.now());
                refreshRepo.save(rt);
            }
        });
    }

    private RefreshToken findValidOrThrow(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenInvalidException("Refresh token missing");
        }
        String hash = sha256(rawToken);
        RefreshToken rt = refreshRepo.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenInvalidException("Refresh token not found"));

        if (rt.getRevokedAt() != null) {
            throw new RefreshTokenInvalidException("Refresh token already revoked");
        }
        if (rt.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RefreshTokenInvalidException("Refresh token expired");
        }
        if (Boolean.FALSE.equals(rt.getStaffUser().getActive())) {
            throw new RefreshTokenInvalidException("Staff user is inactive");
        }
        return rt;
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

    public static class RefreshTokenInvalidException extends RuntimeException {
        public RefreshTokenInvalidException(String msg) {
            super(msg);
        }
    }
}
