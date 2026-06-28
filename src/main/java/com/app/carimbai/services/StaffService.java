package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.PinLockedException;
import com.app.carimbai.security.PinLockoutService;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditMask;
import com.app.carimbai.security.audit.AuditService;
import com.app.carimbai.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffUserRepository staffUserRepository;
    private final StaffUserMerchantRepository staffMerchantRepository;
    private final BCryptPasswordEncoder encoder;
    private final MerchantService merchantService;
    private final PinLockoutService pinLockoutService;
    private final AuditService audit;

    public StaffUser validateCashierPin(Long cashierId, String pin) {
        if (cashierId == null || pin == null || pin.isBlank())
            throw new IllegalArgumentException("Missing cashierId or PIN");

        // FIX-04 / SEC-017 — checa lockout antes do bcrypt (não vaza "user existe?"
        // pelo tempo do hash; e evita custo de bcrypt em sequência de brute-force).
        try {
            pinLockoutService.assertNotLocked(cashierId);
        } catch (PinLockedException ex) {
            audit.event(AuditEvent.CASHIER_PIN_LOCKED, "DENIED", Map.of("staffId", cashierId));
            throw ex;
        }

        var user = staffUserRepository.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        if (Boolean.FALSE.equals(user.getActive()))
            throw new IllegalArgumentException("Cashier not active/authorized");

        var pinHash = user.getPinHash();
        if (pinHash == null || !encoder.matches(pin, pinHash)) {
            pinLockoutService.recordFailure(cashierId);
            audit.failure(AuditEvent.CASHIER_PIN_VALIDATE, Map.of("staffId", cashierId));
            throw new IllegalArgumentException("Invalid cashier PIN");
        }

        pinLockoutService.recordSuccess(cashierId);
        audit.success(AuditEvent.CASHIER_PIN_VALIDATE, Map.of("staffId", cashierId));
        return user;
    }

    public void setPin(Long cashierId, String rawPin) {
        if (rawPin == null || rawPin.length() < 4 || rawPin.length() > 10)
            throw new IllegalArgumentException("PIN must be 4..10 digits");

        Long activeMerchantId = SecurityUtils.getActiveMerchantId();

        var user = staffUserRepository.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        // O caixa alvo precisa pertencer ao merchant ativo do ADMIN (SEC-020).
        staffMerchantRepository.findByStaffUserIdAndMerchantIdAndActiveTrue(cashierId, activeMerchantId)
                .orElseThrow(() -> {
                    audit.denied(AuditEvent.CASHIER_PIN_SET,
                            Map.of("targetStaffId", cashierId, "activeMerchantId", activeMerchantId));
                    return new AccessDeniedException("Staff does not belong to the active merchant");
                });

        user.setPinHash(encoder.encode(rawPin));
        staffUserRepository.save(user);
        audit.success(AuditEvent.CASHIER_PIN_SET,
                Map.of("targetStaffId", cashierId, "merchantId", activeMerchantId));
    }

    @Transactional
    public StaffUser createStaffUser(@Valid CreateStaffUserRequest request) {

        SecurityUtils.requireActiveMerchant(request.merchantId()); // SEC-020
        Merchant merchant = merchantService.findById(request.merchantId());

        var staffUser = StaffUser.builder()
                .email(request.email())
                .passwordHash(encoder.encode(request.password()))
                .active(true)
                .build();

        try {
            staffUser = staffUserRepository.save(staffUser);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Email already in use");
        }

        var link = StaffUserMerchant.builder()
                .staffUser(staffUser)
                .merchant(merchant)
                .role(request.role())
                .active(true)
                .isDefault(true)
                .build();

        staffMerchantRepository.save(link);

        audit.success(AuditEvent.STAFF_USER_CREATE, Map.of(
                "newStaffId", staffUser.getId(),
                "email", AuditMask.email(staffUser.getEmail()),
                "merchantId", request.merchantId(),
                "role", request.role().name()));
        return staffUser;
    }
}
