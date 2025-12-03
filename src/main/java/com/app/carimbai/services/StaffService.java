package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.StaffUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffUserRepository staffUserRepository;
    private final BCryptPasswordEncoder encoder;
    private final MerchantService merchantService;

    public StaffUser validateCashierPin(Long cashierId, String pin) {
        if (cashierId == null || pin == null || pin.isBlank())
            throw new IllegalArgumentException("Missing cashierId or PIN");

        var user = staffUserRepository.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        if (user.getRole() != StaffRole.CASHIER || Boolean.FALSE.equals(user.getActive()))
            throw new IllegalArgumentException("Cashier not active/authorized");

        var pinHash = user.getPinHash();
        if (pinHash == null || !encoder.matches(pin, pinHash))
            throw new IllegalArgumentException("Invalid cashier PIN");

        return user;
    }

    /** Define/atualiza o PIN do caixa (use em endpoint/admin). */
    public void setPin(Long cashierId, String rawPin) {
        if (rawPin == null || rawPin.length() < 4 || rawPin.length() > 10)
            throw new IllegalArgumentException("PIN must be 4..10 digits");

        var user = staffUserRepository.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        if (user.getRole() != StaffRole.CASHIER)
            throw new IllegalArgumentException("Only CASHIER can have redeem PIN");

        user.setPinHash(encoder.encode(rawPin));
        staffUserRepository.save(user);
    }

    public StaffUser createStaffUser(@Valid CreateStaffUserRequest request) {

        Merchant merchant = merchantService.findById(request.merchantId());

        var staffUser = StaffUser.builder()
                .merchant(merchant)
                .email(request.email())
                .passwordHash(encoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .build();

        return staffUserRepository.save(staffUser);
    }
}
