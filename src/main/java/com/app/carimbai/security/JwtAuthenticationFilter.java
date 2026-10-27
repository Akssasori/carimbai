package com.app.carimbai.security;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.execption.InvalidCredentialsException;
import com.app.carimbai.repositories.RefreshTokenRepository;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.services.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.OffsetDateTime;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_MERCHANT_ID = "activeMerchantId";

    private final JwtService jwtService;
    private final StaffAccessService staffAccess;
    private final RefreshTokenRepository refreshTokens;
    private final CustomerRepository customerRepo;
    private final TokenRevocationService revocationService;

    public JwtAuthenticationFilter(JwtService jwtService, StaffAccessService staffAccess,
                                   CustomerRepository customerRepo,
                                   TokenRevocationService revocationService, RefreshTokenRepository refreshTokens) {
        this.jwtService = jwtService;
        this.staffAccess = staffAccess;
        this.refreshTokens = refreshTokens;
        this.customerRepo = customerRepo;
        this.revocationService = revocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                // FIX-11 / SEC-012 — denylist por jti (logout). isRevoked é "no-op" silencioso
                // se o token não tiver jti (legado pré-FIX-11), o que naturalmente vira false.
                if (!jwtService.isExpired(token) && !revocationService.isRevoked(jwtService.extractJti(token))) {
                    // Token de cliente (FIX-02): autentica como ROLE_CUSTOMER e NÃO o trata como staff.
                    if ("CUSTOMER".equals(jwtService.extractType(token))) {
                        Long customerId = jwtService.extractCustomerId(token);
                        Customer customer = customerRepo.findById(customerId).orElse(null);
                        if (customer != null && Boolean.TRUE.equals(customer.getActive())
                                && customer.getTokenVersion()==jwtService.extractTokenVersion(token)) {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    customer,
                                    Map.of("customerId", customerId, "type", "CUSTOMER"),
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    } else {
                        Long staffId = jwtService.extractStaffId(token);
                        Long merchantId = jwtService.extractMerchantId(token);
                        var link = staffAccess.requireActiveLink(staffId, merchantId);
                        StaffUser user = link.getStaffUser();
                        String role = link.getRole().name();
                        var familyId = jwtService.extractFamilyId(token);
                        boolean activeSession = familyId == null || refreshTokens
                                .existsByFamilyIdAndStaffUserIdAndMerchantIdAndRevokedAtIsNullAndExpiresAtAfter(
                                        familyId, staffId, merchantId, OffsetDateTime.now());

                        if (user.getTokenVersion() == jwtService.extractTokenVersion(token) && activeSession) {
                            var authorities = new ArrayList<SimpleGrantedAuthority>();
                            authorities.add(new SimpleGrantedAuthority(role));
                            // Autoridade global derivada do StaffUser (recarregado a cada request). SEC-020.
                            if (Boolean.TRUE.equals(user.getPlatformAdmin())) {
                                authorities.add(new SimpleGrantedAuthority("PLATFORM_ADMIN"));
                            }
                            if (Boolean.TRUE.equals(user.getPrivacyOfficer())) {
                                authorities.add(new SimpleGrantedAuthority("PRIVACY_OFFICER"));
                            }
                            Map<String, Object> credentials = new HashMap<>();
                            credentials.put("merchantId", merchantId);
                            credentials.put("role", role);
                            if (familyId != null) credentials.put("familyId", familyId);
                            var auth = new UsernamePasswordAuthenticationToken(user, credentials, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);

                            request.setAttribute(ATTR_MERCHANT_ID, merchantId);
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException | InvalidCredentialsException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
