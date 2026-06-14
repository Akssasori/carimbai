package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-02 Fase D — fecha o resíduo do SEC-001: o {@code /api/customers/login-or-register}
 * deixa de ser self-service do cliente e vira ação de balcão (staff); e
 * {@code POST /api/customers} fica restrito ao PLATFORM_ADMIN. A imposição
 * acontece em duas camadas: SecurityConfig (URL) + @PreAuthorize (método).
 * Aqui validamos a anotação no método (defesa em profundidade contra alguém
 * afrouxar o SecurityConfig sem perceber).
 */
class CustomerControllerAuthzTest {

    @Test
    void createCustomer_requiresPlatformAdmin() throws NoSuchMethodException {
        Method m = CustomerController.class.getDeclaredMethod("createCustomer", CreateCustomerRequest.class);
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        assertThat(pre).as("createCustomer deve ter @PreAuthorize (FIX-02 Fase D)").isNotNull();
        assertThat(pre.value()).contains("PLATFORM_ADMIN");
    }

    @Test
    void loginOrRegister_requiresStaff() throws NoSuchMethodException {
        Method m = CustomerController.class.getDeclaredMethod("loginOrRegister", CustomerLoginRequest.class);
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        assertThat(pre).as("loginOrRegister deve ter @PreAuthorize (FIX-02 Fase D)").isNotNull();
        assertThat(pre.value())
                .as("deve exigir staff — não é mais self-service do cliente (SEC-001)")
                .contains("CASHIER")
                .contains("ADMIN");
    }
}
