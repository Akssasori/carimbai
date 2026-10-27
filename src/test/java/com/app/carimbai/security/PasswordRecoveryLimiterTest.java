package com.app.carimbai.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryLimiterTest {
    @Test void limitsAccountAcrossDifferentCaseAndIndependentOfIp() {
        var limiter = new PasswordRecoveryLimiter();
        for (int i = 0; i < 5; i++) assertThat(limiter.allow("Owner@example.test")).isTrue();
        assertThat(limiter.allow(" owner@EXAMPLE.test ")).isFalse();
        assertThat(limiter.allow("other@example.test")).isTrue();
    }
}
