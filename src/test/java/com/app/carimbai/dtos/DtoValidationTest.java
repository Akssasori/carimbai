package com.app.carimbai.dtos;

import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.dtos.admin.SetPinRequest;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.enums.StaffRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de Bean Validation dos DTOs (unitários, sem Spring/DB) — cobrem as
 * correções das Fases 14/15: política de senha (SEC-016), formato de PIN
 * (SEC-017), regras de programa (SEC-035), esquema de imageUrl (SEC-023) e
 * validação de entrada do cliente (SEC-022). Usa o Jakarta Validator diretamente.
 */
class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static boolean violatesField(Set<? extends ConstraintViolation<?>> v, String field) {
        return v.stream().anyMatch(c -> c.getPropertyPath().toString().equals(field));
    }

    // ---- CreateStaffUserRequest (SEC-016) ----
    @Test
    void staff_weakPasswordRejected() {
        var r = new CreateStaffUserRequest(1L, "a@b.com", "123", StaffRole.CASHIER);
        assertThat(violatesField(validator.validate(r), "password")).isTrue();
    }

    @Test
    void staff_invalidEmailRejected() {
        var r = new CreateStaffUserRequest(1L, "naoeemail", "senhaforte123", StaffRole.CASHIER);
        assertThat(violatesField(validator.validate(r), "email")).isTrue();
    }

    @Test
    void staff_validAccepted() {
        var r = new CreateStaffUserRequest(1L, "a@b.com", "senhaforte123", StaffRole.ADMIN);
        assertThat(validator.validate(r)).isEmpty();
    }

    // ---- SetPinRequest (SEC-017) ----
    @Test
    void pin_nonNumericRejected() {
        assertThat(validator.validate(new SetPinRequest("abcd"))).isNotEmpty();
    }

    @Test
    void pin_tooShortRejected() {
        assertThat(validator.validate(new SetPinRequest("12"))).isNotEmpty();
    }

    @Test
    void pin_validAccepted() {
        assertThat(validator.validate(new SetPinRequest("1234"))).isEmpty();
    }

    // ---- CreateProgramRequest (SEC-035 + SEC-023) ----
    @Test
    void program_zeroStampsRejected() {
        var r = new CreateProgramRequest("P", 0, null, null, null, null, null, null, null, null, null);
        assertThat(violatesField(validator.validate(r), "ruleTotalStamps")).isTrue();
    }

    @Test
    void program_javascriptImageUrlRejected() {
        var r = new CreateProgramRequest("P", 10, null, null, null, null, null, null, null,
                "javascript:alert(1)", 0);
        assertThat(violatesField(validator.validate(r), "imageUrl")).isTrue();
    }

    @Test
    void program_httpsImageUrlAccepted() {
        var r = new CreateProgramRequest("P", 10, null, null, null, null, null, null, null,
                "https://cdn.exemplo/img.png", 0);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void program_emptyImageUrlAccepted() {
        var r = new CreateProgramRequest("P", 10, null, null, null, null, null, null, null, "", 0);
        assertThat(validator.validate(r)).isEmpty();
    }

    // ---- CustomerLoginRequest (SEC-022) ----
    @Test
    void customer_invalidEmailRejected() {
        var r = new CustomerLoginRequest("Nome", "naoeemail", null, null);
        assertThat(violatesField(validator.validate(r), "email")).isTrue();
    }

    @Test
    void customer_validAccepted() {
        var r = new CustomerLoginRequest("Nome", "a@b.com", "11999998888", null);
        assertThat(validator.validate(r)).isEmpty();
    }
}
