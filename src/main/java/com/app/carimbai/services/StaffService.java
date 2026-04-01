package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void setPin(Long cashierId, String rawPin) {
        if (rawPin == null || rawPin.length() < 4 || rawPin.length() > 10)
            throw new IllegalArgumentException("PIN must be 4..10 digits");

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
}
