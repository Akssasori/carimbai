package com.app.carimbai.services;

import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.StaffUserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class StaffService {

    private final StaffUserRepository repo;
    private final BCryptPasswordEncoder encoder;

    public StaffService(StaffUserRepository repo) {
        this.repo = repo;
        this.encoder = new BCryptPasswordEncoder(); // simples p/ MVP
    }

    public StaffUser validateCashierPin(Long cashierId, String pin) {
        if (cashierId == null || pin == null || pin.isBlank())
            throw new IllegalArgumentException("Missing cashierId or PIN");

        var user = repo.findById(cashierId)
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

        var user = repo.findById(cashierId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found"));

        if (user.getRole() != StaffRole.CASHIER)
            throw new IllegalArgumentException("Only CASHIER can have redeem PIN");

        user.setPinHash(encoder.encode(rawPin));
        repo.save(user);
    }
}
