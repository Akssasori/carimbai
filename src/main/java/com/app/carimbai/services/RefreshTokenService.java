package com.app.carimbai.services;

import com.app.carimbai.models.RefreshToken;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.RefreshTokenRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.StaffAccessService;
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
import java.util.UUID;

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

    private final RefreshTokenRepository refreshRepo;
    private final StaffUserRepository staffRepo;
    private final StaffAccessService staffAccess;
    private final com.app.carimbai.security.NetworkMetadata metadata;
    private final PrivacyJournalService privacyJournal;

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
        staff = staffRepo.findByIdForUpdate(staff.getId())
                .orElseThrow(() -> new RefreshTokenInvalidException("Invalid session"));
        staffAccess.requireActiveLink(staff.getId(), merchant.getId());
        String rawToken = generateRawToken();
        String hash = sha256(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(hash)
                .familyId(UUID.randomUUID())
                .tokenVersion(staff.getTokenVersion())
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
    @Transactional(noRollbackFor = RefreshTokenInvalidException.class)
    public IssuedToken rotate(String rawToken) {
        RefreshToken current = findValidOrThrow(rawToken);

        // Emite novo
        String newRaw = generateRawToken();
        String newHash = sha256(newRaw);
        RefreshToken next = RefreshToken.builder()
                .tokenHash(newHash)
                .familyId(current.getFamilyId())
                .tokenVersion(current.getStaffUser().getTokenVersion())
                .staffUser(current.getStaffUser())
                .merchant(current.getMerchant())
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenDays))
                .ip(resolveIp())
                .userAgent(resolveUserAgent())
                .build();
        next = refreshRepo.saveAndFlush(next);

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
        refreshRepo.findStaffIdByTokenHash(hash).ifPresent(staffId -> {
            staffRepo.findByIdForUpdate(staffId);
            refreshRepo.findByTokenHashForUpdate(hash).ifPresent(rt ->
                    {
                        if (refreshRepo.revokeFamily(rt.getFamilyId(), OffsetDateTime.now())>0)
                            privacyJournal.sessionInvalidated(com.app.carimbai.enums.AuditActorType.STAFF,staffId);
                    });
        });
    }

    @Transactional
    public void revokeFamily(Long staffId, UUID familyId) {
        if (familyId == null) return;
        staffRepo.findByIdForUpdate(staffId);
        refreshRepo.revokeFamily(familyId, OffsetDateTime.now());
    }

    private RefreshToken findValidOrThrow(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw new RefreshTokenInvalidException("Refresh token missing");
        }
        String hash = sha256(rawToken);
        Long staffId = refreshRepo.findStaffIdByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenInvalidException("Invalid session"));
        // Lock staff before tokens, consistently with password reset and logout.
        StaffUser staff = staffRepo.findByIdForUpdate(staffId)
                .orElseThrow(() -> new RefreshTokenInvalidException("Invalid session"));
        RefreshToken rt = refreshRepo.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new RefreshTokenInvalidException("Refresh token not found"));

        if (rt.getRevokedAt() != null) {
            if (refreshRepo.revokeFamily(rt.getFamilyId(), OffsetDateTime.now())>0)
                privacyJournal.sessionInvalidated(com.app.carimbai.enums.AuditActorType.STAFF,staffId);
            throw new RefreshTokenInvalidException("Refresh token already revoked");
        }
        if (rt.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RefreshTokenInvalidException("Refresh token expired");
        }
        if (!Boolean.TRUE.equals(staff.getActive()) || !staff.getTokenVersion().equals(rt.getTokenVersion())) {
            throw new RefreshTokenInvalidException("Staff user is inactive");
        }
        staffAccess.requireActiveLink(staffId, rt.getMerchant().getId());
        return rt;
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

    public static class RefreshTokenInvalidException extends RuntimeException {
        public RefreshTokenInvalidException(String msg) {
            super(msg);
        }
    }
}
