package com.app.carimbai.services;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.MerchantInfo;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffUserRepository staffRepo;
    private final StaffUserMerchantRepository staffMerchantRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        StaffUser user = staffRepo.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalStateException("Staff user is inactive");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        List<StaffUserMerchant> links = staffMerchantRepo.findByStaffUserIdAndActiveTrue(user.getId());
        if (links.isEmpty()) {
            throw new IllegalStateException("Staff user has no active merchant links");
        }

        StaffUserMerchant activeLink = resolveActiveLink(links, request.merchantId());

        String token = jwtService.generateToken(user, activeLink);

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
                .orElseThrow(() -> new IllegalArgumentException("No active link to merchant " + merchantId));

        String token = jwtService.generateToken(user, link);

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
