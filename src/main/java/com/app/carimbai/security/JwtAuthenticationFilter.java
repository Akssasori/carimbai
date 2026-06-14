package com.app.carimbai.security;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.StaffUserRepository;
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

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_MERCHANT_ID = "activeMerchantId";

    private final JwtService jwtService;
    private final StaffUserRepository staffRepo;
    private final CustomerRepository customerRepo;
    private final TokenRevocationService revocationService;

    public JwtAuthenticationFilter(JwtService jwtService, StaffUserRepository staffRepo,
                                   CustomerRepository customerRepo,
                                   TokenRevocationService revocationService) {
        this.jwtService = jwtService;
        this.staffRepo = staffRepo;
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
                        if (customer != null) {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    customer,
                                    Map.of("customerId", customerId, "type", "CUSTOMER"),
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    } else {
                        Long staffId = jwtService.extractStaffId(token);
                        String role = jwtService.extractRole(token);
                        Long merchantId = jwtService.extractMerchantId(token);

                        StaffUser user = staffRepo.findById(staffId)
                                .orElse(null);

                        if (user != null && Boolean.TRUE.equals(user.getActive())) {
                            var authorities = new ArrayList<SimpleGrantedAuthority>();
                            authorities.add(new SimpleGrantedAuthority(role));
                            // Autoridade global derivada do StaffUser (recarregado a cada request). SEC-020.
                            if (Boolean.TRUE.equals(user.getPlatformAdmin())) {
                                authorities.add(new SimpleGrantedAuthority("PLATFORM_ADMIN"));
                            }
                            var auth = new UsernamePasswordAuthenticationToken(
                                    user, Map.of("merchantId", merchantId, "role", role), authorities
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);

                            request.setAttribute(ATTR_MERCHANT_ID, merchantId);
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
