package com.app.carimbai.services;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.PinLockedException;
import com.app.carimbai.security.PinLockoutService;
import com.app.carimbai.security.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIX-04 / SEC-017 — integração StaffService × PinLockoutService:
 * <ul>
 *   <li>5 PINs incorretos seguidos disparam o lockout (próxima chamada → 423);</li>
 *   <li>acerto reseta — falhas anteriores não bloqueiam.</li>
 * </ul>
 */
class StaffServiceLockoutTest {

    private StaffUserRepository staffRepo;
    private BCryptPasswordEncoder encoder;
    private PinLockoutService lockout;
    private StaffService service;

    @BeforeEach
    void setUp() {
        staffRepo = mock(StaffUserRepository.class);
        var linkRepo = mock(StaffUserMerchantRepository.class);
        var merchantService = mock(MerchantService.class);
        encoder = mock(BCryptPasswordEncoder.class);
        lockout = new PinLockoutService(); // real, in-memory
        service = new StaffService(staffRepo, linkRepo, encoder, merchantService, lockout, mock(AuditService.class));

        var staff = StaffUser.builder().id(10L).pinHash("HASH").active(true).build();
        when(staffRepo.findById(10L)).thenReturn(Optional.of(staff));
    }

    @Test
    void fiveWrongPins_blockNextAttempt() {
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);

        for (int i = 0; i < PinLockoutService.MAX_FAILURES; i++) {
            assertThatThrownBy(() -> service.validateCashierPin(10L, "wrong"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        // 6ª tentativa — bloqueada antes mesmo de chamar o encoder.
        assertThatThrownBy(() -> service.validateCashierPin(10L, "wrong"))
                .isInstanceOf(PinLockedException.class);
    }

    @Test
    void successResetsCounter() {
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);
        when(encoder.matches(eq("right"), any())).thenReturn(true);

        for (int i = 0; i < PinLockoutService.MAX_FAILURES - 1; i++) {
            assertThatThrownBy(() -> service.validateCashierPin(10L, "wrong"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        // sucesso zera o contador
        assertThatCode(() -> service.validateCashierPin(10L, "right")).doesNotThrowAnyException();

        // após reset, mais 4 falhas continuam não bloqueando
        for (int i = 0; i < PinLockoutService.MAX_FAILURES - 1; i++) {
            assertThatThrownBy(() -> service.validateCashierPin(10L, "wrong"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> service.validateCashierPin(10L, "right")).doesNotThrowAnyException();
    }
}
