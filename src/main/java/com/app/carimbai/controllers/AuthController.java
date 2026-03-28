package com.app.carimbai.controllers;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.dtos.login.SwitchMerchantRequest;
import com.app.carimbai.services.AuthService;
import com.app.carimbai.utils.SecurityUtils;
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
}
