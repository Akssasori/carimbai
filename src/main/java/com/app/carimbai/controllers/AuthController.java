package com.app.carimbai.controllers;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.RefreshTokenRequest;
import com.app.carimbai.dtos.login.RefreshTokenResponse;
import com.app.carimbai.dtos.login.SwitchMerchantRequest;
import com.app.carimbai.services.AuthService;
import com.app.carimbai.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/switch-merchant")
    public ResponseEntity<LoginResponse> switchMerchant(@Valid @RequestBody SwitchMerchantRequest request) {
        Long staffId = SecurityUtils.getRequiredStaffUser().getId();
        return ResponseEntity.ok(authService.switchMerchant(staffId, request.merchantId()));
    }

    @Operation(summary = "Rotaciona o refresh token e devolve um novo par (access + refresh).",
            description = "Cliente envia o refresh token atual; servidor invalida-o e emite par novo. " +
                    "Endpoint publico (permitAll) — autenticacao acontece pelo proprio refresh token.")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Invalida o refresh token do cliente.",
            description = "Idempotente. Cliente deve descartar o access JWT em seguida.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
