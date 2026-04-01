package com.app.carimbai.security;

import com.app.carimbai.models.core.StaffUser;
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
import java.util.List;
import java.util.Map;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_MERCHANT_ID = "activeMerchantId";

    private final JwtService jwtService;
    private final StaffUserRepository staffRepo;

    public JwtAuthenticationFilter(JwtService jwtService, StaffUserRepository staffRepo) {
        this.jwtService = jwtService;
        this.staffRepo = staffRepo;
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
                if (!jwtService.isExpired(token)) {
                    Long staffId = jwtService.extractStaffId(token);
                    String role = jwtService.extractRole(token);
                    Long merchantId = jwtService.extractMerchantId(token);

                    StaffUser user = staffRepo.findById(staffId)
                            .orElse(null);

                    if (user != null && Boolean.TRUE.equals(user.getActive())) {
                        var authorities = List.of(new SimpleGrantedAuthority(role));
                        var auth = new UsernamePasswordAuthenticationToken(
                                user, Map.of("merchantId", merchantId, "role", role), authorities
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);

                        request.setAttribute(ATTR_MERCHANT_ID, merchantId);
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
