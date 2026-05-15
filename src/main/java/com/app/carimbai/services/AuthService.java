package com.app.carimbai.services;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.MerchantInfo;
import com.app.carimbai.dtos.login.RefreshTokenResponse;
import com.app.carimbai.enums.AuditAction;
import com.app.carimbai.enums.AuditActorType;
import com.app.carimbai.execption.LoginRateLimitedException;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffUserRepository staffRepo;
    private final StaffUserMerchantRepository staffMerchantRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final LoginRateLimitService loginRateLimitService;
    private final RefreshTokenService refreshTokenService;

    public LoginResponse login(LoginRequest request) {
        // Rate limit antes de qualquer outra coisa: previne brute force.
        // Se passar, NAO conta como gasto se o login for bem-sucedido (recordSuccess limpa).
        try {
            loginRateLimitService.checkOrThrow(request.email());
        } catch (LoginRateLimitedException ex) {
            auditService.log(AuditService.AuditEntry.builder()
                    .action(AuditAction.LOGIN_RATE_LIMITED)
                    .actorType(AuditActorType.ANONYMOUS)
                    .success(false)
                    .details(Map.of(
                            "email", request.email() != null ? request.email() : "",
                            "retryAfterSeconds", ex.getRetryAfterSeconds()
                    ))
                    .build());
            throw ex;
        }

        StaffUser user = staffRepo.findByEmail(request.email()).orElse(null);

        if (user == null) {
            auditLoginFailed(null, request.email(), "USER_NOT_FOUND");
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (Boolean.FALSE.equals(user.getActive())) {
            auditLoginFailed(user.getId(), request.email(), "USER_INACTIVE");
            throw new IllegalStateException("Staff user is inactive");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLoginFailed(user.getId(), request.email(), "WRONG_PASSWORD");
            throw new IllegalArgumentException("Invalid credentials");
        }

        List<StaffUserMerchant> links = staffMerchantRepo.findByStaffUserIdAndActiveTrue(user.getId());
        if (links.isEmpty()) {
            auditLoginFailed(user.getId(), request.email(), "NO_ACTIVE_MERCHANT");
            throw new IllegalStateException("Staff user has no active merchant links");
        }

        StaffUserMerchant activeLink = resolveActiveLink(links, request.merchantId());

        String token = jwtService.generateToken(user, activeLink);
        RefreshTokenService.IssuedToken refresh = refreshTokenService.issue(user, activeLink.getMerchant());

        // Login bem-sucedido: reseta o bucket para nao carregar tentativas anteriores.
        loginRateLimitService.recordSuccess(request.email());

        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.LOGIN_SUCCESS)
                .actorType(AuditActorType.STAFF)
                .actorId(user.getId())
                .merchantId(activeLink.getMerchant().getId())
                .details(Map.of("email", user.getEmail(), "role", activeLink.getRole().name()))
                .build());

        List<MerchantInfo> merchants = links.stream()
                .map(l -> new MerchantInfo(
                        l.getMerchant().getId(),
                        l.getMerchant().getName(),
                        l.getRole().name(),
                        Boolean.TRUE.equals(l.getIsDefault())
                ))
                .toList();

        return new LoginResponse(
                token,
                refresh.rawToken(),
                user.getId(),
                activeLink.getMerchant().getId(),
                activeLink.getRole().name(),
                user.getEmail(),
                merchants
        );
    }

    public LoginResponse switchMerchant(Long staffId, Long merchantId) {
        StaffUser user = staffRepo.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalStateException("Staff user is inactive");
        }

        StaffUserMerchant link = staffMerchantRepo
                .findByStaffUserIdAndMerchantIdAndActiveTrue(staffId, merchantId)
                .orElseThrow(() -> new IllegalArgumentException("No active link to merchant " + merchantId));

        String token = jwtService.generateToken(user, link);
        RefreshTokenService.IssuedToken refresh = refreshTokenService.issue(user, link.getMerchant());

        List<StaffUserMerchant> allLinks = staffMerchantRepo.findByStaffUserIdAndActiveTrue(staffId);
        List<MerchantInfo> merchants = allLinks.stream()
                .map(l -> new MerchantInfo(
                        l.getMerchant().getId(),
                        l.getMerchant().getName(),
                        l.getRole().name(),
                        Boolean.TRUE.equals(l.getIsDefault())
                ))
                .toList();

        return new LoginResponse(
                token,
                refresh.rawToken(),
                user.getId(),
                link.getMerchant().getId(),
                link.getRole().name(),
                user.getEmail(),
                merchants
        );
    }

    /**
     * Recebe um refresh token cru, rotaciona e devolve um access + refresh novos.
     * O cliente substitui ambos no storage. Token antigo fica invalidado.
     */
    public RefreshTokenResponse refresh(String rawRefreshToken) {
        RefreshTokenService.IssuedToken issued = refreshTokenService.rotate(rawRefreshToken);

        // Re-emite access token. Para mantermos o role/merchant atualizados, precisamos
        // do link ativo correspondente ao merchant do refresh.
        StaffUser user = issued.persisted().getStaffUser();
        Long merchantId = issued.persisted().getMerchant().getId();
        StaffUserMerchant activeLink = staffMerchantRepo
                .findByStaffUserIdAndMerchantIdAndActiveTrue(user.getId(), merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Staff " + user.getId() + " no longer has active link to merchant " + merchantId));

        String accessToken = jwtService.generateToken(user, activeLink);

        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.TOKEN_REFRESHED)
                .actorType(AuditActorType.STAFF)
                .actorId(user.getId())
                .merchantId(merchantId)
                .build());

        return new RefreshTokenResponse(accessToken, issued.rawToken());
    }

    /**
     * Logout: revoga o refresh token. Cliente deve descartar tambem o access JWT.
     * Idempotente (revoga so se ainda nao foi revogado).
     */
    public void logout(String rawRefreshToken) {
        StaffUser actor = SecurityUtils.getCurrentStaffUserOrNull();
        refreshTokenService.revoke(rawRefreshToken);

        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.LOGOUT)
                .actorType(actor != null ? AuditActorType.STAFF : AuditActorType.ANONYMOUS)
                .actorId(actor != null ? actor.getId() : null)
                .build());
    }

    private void auditLoginFailed(Long staffId, String email, String reason) {
        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.LOGIN_FAILED)
                .actorType(staffId != null ? AuditActorType.STAFF : AuditActorType.ANONYMOUS)
                .actorId(staffId)
                .success(false)
                .details(Map.of("email", email != null ? email : "", "reason", reason))
                .build());
    }

    private StaffUserMerchant resolveActiveLink(List<StaffUserMerchant> links, Long requestedMerchantId) {
        if (requestedMerchantId != null) {
            return links.stream()
                    .filter(l -> l.getMerchant().getId().equals(requestedMerchantId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No active link to requested merchant"));
        }

        return links.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsDefault()))
                .findFirst()
                .orElse(links.getFirst());
    }
}
