package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.dtos.staff.admin.UpdateStaffMerchantRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffUserRepository staffUserRepository;
    private final StaffUserMerchantRepository staffMerchantRepository;
    private final BCryptPasswordEncoder encoder;
    private final MerchantService merchantService;

    public StaffUser validateCashierPin(Long cashierId, String pin) {
        if (cashierId == null || pin == null || pin.isBlank())
            throw new IllegalArgumentException("Missing cashierId or PIN");

        var user = staffUserRepository.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        if (Boolean.FALSE.equals(user.getActive()))
            throw new IllegalArgumentException("Cashier not active/authorized");

        var pinHash = user.getPinHash();
        if (pinHash == null || !encoder.matches(pin, pinHash))
            throw new IllegalArgumentException("Invalid cashier PIN");

        return user;
    }

    public void setPin(Long cashierId, String rawPin, Long callerMerchantId) {
        if (rawPin == null || rawPin.length() < 4 || rawPin.length() > 10)
            throw new IllegalArgumentException("PIN must be 4..10 digits");

        // O staff alvo precisa estar ativo no merchant ativo de quem está chamando.
        staffMerchantRepository
                .findByStaffUserIdAndMerchantIdAndActiveTrue(cashierId, callerMerchantId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Target staff does not belong to caller's active merchant"));

        var user = staffUserRepository.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        user.setPinHash(encoder.encode(rawPin));
        staffUserRepository.save(user);
    }

    @Transactional
    public StaffUser createStaffUser(@Valid CreateStaffUserRequest request) {

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

        return staffUser;
    }

    @Transactional(readOnly = true)
    public List<StaffUserMerchant> listStaffByMerchant(Long merchantId) {
        return staffMerchantRepository.findAllByMerchantIdWithStaff(merchantId);
    }

    /**
     * Atualiza role e/ou active no vinculo staff_user_merchants para o (staffId, merchantId).
     * Guard de auto-lockout: o admin logado nao pode (1) se desativar, (2) se rebaixar para CASHIER.
     * Isso evita o cenario em que o unico ADMIN do merchant fica trancado fora.
     */
    @Transactional
    public StaffUserMerchant updateStaffInMerchant(Long merchantId,
                                                   Long staffId,
                                                   UpdateStaffMerchantRequest request,
                                                   Long callerStaffId) {
        StaffUserMerchant link = staffMerchantRepository
                .findByStaffUserIdAndMerchantId(staffId, merchantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Staff " + staffId + " has no link with merchant " + merchantId));

        boolean isSelf = callerStaffId != null && callerStaffId.equals(staffId);
        if (isSelf) {
            if (Boolean.FALSE.equals(request.active())) {
                throw new AccessDeniedException("You cannot deactivate yourself in this merchant");
            }
            if (request.role() != null && request.role() != link.getRole()
                    && link.getRole() != null && "ADMIN".equals(link.getRole().name())) {
                throw new AccessDeniedException("You cannot demote yourself from ADMIN in this merchant");
            }
        }

        if (request.role() != null) link.setRole(request.role());
        if (request.active() != null) link.setActive(request.active());

        return staffMerchantRepository.save(link);
    }
}
