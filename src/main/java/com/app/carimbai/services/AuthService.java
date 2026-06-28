package com.app.carimbai.services;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.MerchantInfo;
import com.app.carimbai.execption.InvalidCredentialsException;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditMask;
import com.app.carimbai.security.audit.AuditService;
import jakarta.annotation.PostConstruct;
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
    private final AuditService audit;

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

    public LoginResponse login(LoginRequest request) {
        // FIX-08 / SEC-008 — toda falha vira a MESMA exceção/mensagem; bcrypt
        // roda sempre (mesmo se user==null/inactive) para que o tempo de resposta
        // não distinga "e-mail existe?" de "senha errada".
        StaffUser user = staffRepo.findByEmail(request.email()).orElse(null);

        String hashToCheck = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash()
                : dummyPasswordHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("email", AuditMask.email(request.email()), "reason", "not_found"));
            throw new InvalidCredentialsException();
        }
        if (Boolean.FALSE.equals(user.getActive())) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("staffId", user.getId(), "reason", "inactive"));
            throw new InvalidCredentialsException();
        }
        if (!passwordOk) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("staffId", user.getId(), "reason", "bad_password"));
            throw new InvalidCredentialsException();
        }

        List<StaffUserMerchant> links = staffMerchantRepo.findByStaffUserIdAndActiveTrue(user.getId());
        if (links.isEmpty()) {
            audit.failure(AuditEvent.STAFF_LOGIN, Map.of("staffId", user.getId(), "reason", "no_merchant_link"));
            throw new InvalidCredentialsException();
        }

        StaffUserMerchant activeLink = resolveActiveLink(links, request.merchantId());

        String token = jwtService.generateToken(user, activeLink);
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
                .orElseThrow(() -> {
                    audit.denied(AuditEvent.STAFF_SWITCH_MERCHANT,
                            Map.of("staffId", staffId, "targetMerchantId", merchantId, "reason", "no_active_link"));
                    return new IllegalArgumentException("No active link to merchant " + merchantId);
                });

        String token = jwtService.generateToken(user, link);
        audit.success(AuditEvent.STAFF_SWITCH_MERCHANT,
                Map.of("staffId", staffId, "merchantId", merchantId, "role", link.getRole().name()));

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
                user.getId(),
                link.getMerchant().getId(),
                link.getRole().name(),
                user.getEmail(),
                merchants
        );
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
