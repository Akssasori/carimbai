package com.app.carimbai.services;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.MerchantInfo;
import com.app.carimbai.dtos.login.RefreshTokenResponse;
import com.app.carimbai.dtos.login.ResetPasswordRequest;
import com.app.carimbai.execption.InvalidCredentialsException;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.repositories.RefreshTokenRepository;
import com.app.carimbai.repositories.PasswordResetTokenRepository;
import com.app.carimbai.security.StaffAccessService;
import com.app.carimbai.security.TokenRevocationService;
import com.app.carimbai.utils.SecurityUtils;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditMask;
import com.app.carimbai.security.audit.AuditSecurityService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffUserRepository staffRepo;
    private final StaffUserMerchantRepository staffMerchantRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditSecurityService audit;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordResetTokenRepository passwordResetRepo;
    private final TokenRevocationService revocation;
    private final StaffAccessService staffAccess;
    private final LoginRateLimitService loginRateLimit;
    private final PrivacyJournalService privacyJournal;

    /**
     * Hash bcrypt fixo usado quando o e-mail não existe — equaliza o tempo de
     * resposta com o caminho de senha real (FIX-08 / SEC-008, defesa contra
     * timing). Inicializado uma vez no {@link #init()}.
     */
    private String dummyPasswordHash;

    @PostConstruct
    void init() {
        // Codifica algo aleatório nunca-aceito; o custo do bcrypt aqui é o mesmo
        // que o {@code passwordEncoder.matches} faria com um hash real.
        this.dummyPasswordHash = passwordEncoder.encode("never-matches-anything");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        loginRateLimit.checkOrThrow(request.email());
        // FIX-08 / SEC-008 — toda falha vira a MESMA exceção/mensagem; bcrypt
        // roda sempre (mesmo se user==null/inactive) para que o tempo de resposta
        // não distinga "e-mail existe?" de "senha errada".
        StaffUser user = staffRepo.findByEmailForUpdate(request.email()).orElse(null);

        String hashToCheck = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash()
                : dummyPasswordHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("email", AuditMask.email(request.email()), "reason", "not_found"));
            throw new InvalidCredentialsException();
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("staffId", user.getId(), "reason", "inactive"));
            throw new InvalidCredentialsException();
        }
        if (!passwordOk) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("staffId", user.getId(), "reason", "bad_password"));
            throw new InvalidCredentialsException();
        }

        List<StaffUserMerchant> links = activeLinks(user.getId());
        if (links.isEmpty()) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("staffId", user.getId(), "reason", "no_merchant_link"));
            throw new InvalidCredentialsException();
        }

        StaffUserMerchant activeLink = resolveActiveLink(links, request.merchantId());

        var refresh = refreshTokenService.issue(user, activeLink.getMerchant());
        String token = jwtService.generateToken(user, activeLink, refresh.persisted().getFamilyId());
        loginRateLimit.recordSuccess(request.email());
        audit.success(AuditEvent.STAFF_LOGIN, Map.of(
                "staffId", user.getId(),
                "merchantId", activeLink.getMerchant().getId(),
                "role", activeLink.getRole().name()));

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

    @Transactional
    public LoginResponse switchMerchant(Long staffId, Long merchantId) {
        StaffUser user = staffRepo.findByIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalStateException("Staff user is inactive");
        }

        StaffUserMerchant link = staffAccess.requireActiveLink(staffId, merchantId);
        refreshTokenService.revokeFamily(staffId, SecurityUtils.getActiveFamilyIdOrNull());
        privacyJournal.sessionInvalidated(com.app.carimbai.enums.AuditActorType.STAFF,staffId);
        var refresh = refreshTokenService.issue(user, link.getMerchant());
        String token = jwtService.generateToken(user, link, refresh.persisted().getFamilyId());
        audit.success(AuditEvent.STAFF_SWITCH_MERCHANT,
                Map.of("staffId", staffId, "merchantId", merchantId, "role", link.getRole().name()));

        List<StaffUserMerchant> allLinks = activeLinks(staffId);
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

    // No outer transaction: rotate must commit a detected replay's family revocation.
    public RefreshTokenResponse refresh(String rawToken) {
        var rotated = refreshTokenService.rotate(rawToken);
        var token = rotated.persisted();
        var link = staffAccess.requireActiveLink(token.getStaffUser().getId(), token.getMerchant().getId());
        String access = jwtService.generateToken(link.getStaffUser(), link, token.getFamilyId());
        audit.success(AuditEvent.SESSION_REFRESH, Map.of("staffId", link.getStaffUser().getId()));
        return new RefreshTokenResponse(access, rotated.rawToken());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must contain at most 72 UTF-8 bytes");
        }
        StaffUser staff = passwordResetTokenService.consume(request.token());
        staff.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        staff.setTokenVersion(staff.getTokenVersion() + 1);
        staffRepo.saveAndFlush(staff);
        refreshRepo.revokeAllForStaff(staff.getId(), OffsetDateTime.now());
        passwordResetRepo.invalidateForStaff(staff.getId(), OffsetDateTime.now());
        privacyJournal.sessionInvalidated(com.app.carimbai.enums.AuditActorType.STAFF,staff.getId());
        audit.success(AuditEvent.PASSWORD_RESET, Map.of("staffId", staff.getId()));
    }

    @Transactional
    public void logout(String authorization, String rawRefreshToken) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                jwtService.parseToken(token);
                boolean newRevocation=revocation.revoke(jwtService.extractJti(token), jwtService.extractExpiration(token));
                if (newRevocation) privacyJournal.sessionInvalidated("CUSTOMER".equals(jwtService.extractType(token))
                        ? com.app.carimbai.enums.AuditActorType.CUSTOMER : com.app.carimbai.enums.AuditActorType.STAFF,
                        jwtService.extractStaffId(token));
                if (!"CUSTOMER".equals(jwtService.extractType(token))) {
                    refreshTokenService.revokeFamily(jwtService.extractStaffId(token), jwtService.extractFamilyId(token));
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Expired or invalid access credentials need no denylist entry.
            }
        }
        refreshTokenService.revoke(rawRefreshToken);
        audit.success(AuditEvent.LOGOUT, Map.of());
    }

    private List<StaffUserMerchant> activeLinks(Long staffId) {
        return staffMerchantRepo.findByStaffUserIdAndActiveTrue(staffId).stream()
                .filter(link -> Boolean.TRUE.equals(link.getMerchant().getActive()))
                .toList();
    }

    private StaffUserMerchant resolveActiveLink(List<StaffUserMerchant> links, Long requestedMerchantId) {
        if (requestedMerchantId != null) {
            return links.stream()
                    .filter(l -> l.getMerchant().getId().equals(requestedMerchantId))
                    .findFirst()
                    .orElseThrow(InvalidCredentialsException::new);
        }

        return links.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsDefault()))
                .findFirst()
                .orElse(links.getFirst());
    }
}
