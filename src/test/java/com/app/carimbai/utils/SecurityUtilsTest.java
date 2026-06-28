package com.app.carimbai.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do helper de isolamento multi-tenant (SEC-020 / FIX-03). Unitários, sem
 * Spring/DB — montam o SecurityContext manualmente como o JwtAuthenticationFilter faz.
 */
class SecurityUtilsTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setActiveMerchant(Long merchantId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "principal",
                Map.of("merchantId", merchantId, "role", "ADMIN"),
                List.of(new SimpleGrantedAuthority("ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void sameMerchant_passes() {
        setActiveMerchant(5L);
        assertThatCode(() -> SecurityUtils.requireActiveMerchant(5L)).doesNotThrowAnyException();
    }

    @Test
    void differentMerchant_denied() {
        setActiveMerchant(5L);
        assertThatThrownBy(() -> SecurityUtils.requireActiveMerchant(6L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nullMerchant_denied() {
        setActiveMerchant(5L);
        assertThatThrownBy(() -> SecurityUtils.requireActiveMerchant(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noContext_throws() {
        assertThatThrownBy(() -> SecurityUtils.requireActiveMerchant(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
