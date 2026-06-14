package com.app.carimbai.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIX-04 / SEC-017 — Lockout de PIN após 5 erros; sucesso reseta a contagem.
 */
class PinLockoutServiceTest {

    @Test
    void unknownStaff_isNotLocked() {
        var s = new PinLockoutService();
        assertThatCode(() -> s.assertNotLocked(42L)).doesNotThrowAnyException();
    }

    @Test
    void fourFailures_doNotLock() {
        var s = new PinLockoutService();
        for (int i = 0; i < PinLockoutService.MAX_FAILURES - 1; i++) s.recordFailure(7L);
        assertThatCode(() -> s.assertNotLocked(7L)).doesNotThrowAnyException();
    }

    @Test
    void fiveFailures_lock() {
        var s = new PinLockoutService();
        for (int i = 0; i < PinLockoutService.MAX_FAILURES; i++) s.recordFailure(7L);
        assertThatThrownBy(() -> s.assertNotLocked(7L)).isInstanceOf(PinLockedException.class);
    }

    @Test
    void success_resetsFailures() {
        var s = new PinLockoutService();
        for (int i = 0; i < PinLockoutService.MAX_FAILURES - 1; i++) s.recordFailure(7L);
        s.recordSuccess(7L);
        // após o reset, 4 falhas novas continuam não bloqueando (contagem zerada)
        for (int i = 0; i < PinLockoutService.MAX_FAILURES - 1; i++) s.recordFailure(7L);
        assertThatCode(() -> s.assertNotLocked(7L)).doesNotThrowAnyException();
    }

    @Test
    void lockoutIsPerStaff() {
        var s = new PinLockoutService();
        for (int i = 0; i < PinLockoutService.MAX_FAILURES; i++) s.recordFailure(7L);
        assertThatThrownBy(() -> s.assertNotLocked(7L)).isInstanceOf(PinLockedException.class);
        assertThatCode(() -> s.assertNotLocked(8L)).doesNotThrowAnyException();
    }
}
