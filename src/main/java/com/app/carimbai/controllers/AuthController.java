package com.app.carimbai.controllers;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.SwitchMerchantRequest;
import com.app.carimbai.security.TokenRevocationService;
import com.app.carimbai.services.AuthService;
import com.app.carimbai.services.JwtService;
import com.app.carimbai.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenRevocationService revocationService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/switch-merchant")
    public ResponseEntity<LoginResponse> switchMerchant(@Valid @RequestBody SwitchMerchantRequest request) {
        Long staffId = SecurityUtils.getRequiredStaffUser().getId();
        return ResponseEntity.ok(authService.switchMerchant(staffId, request.merchantId()));
    }

    /**
     * Logout (FIX-11 / SEC-012) — revoga o JWT corrente pelo jti. Token expirado/
     * inválido também responde 204 (idempotente; não vaza se o token era válido).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                revocationService.revoke(jwtService.extractJti(token), jwtService.extractExpiration(token));
            } catch (RuntimeException ignored) {
                // token inválido/expirado: nada a revogar.
            }
        }
        return ResponseEntity.noContent().build();
    }
}
