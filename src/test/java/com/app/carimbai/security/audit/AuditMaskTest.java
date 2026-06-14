package com.app.carimbai.security.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FIX-10 / SEC-027 — mascaramento de PII em auditoria. */
class AuditMaskTest {

    @Test
    void email_masksLocalPart_preservesDomain() {
        assertThat(AuditMask.email("joao.silva@example.com")).isEqualTo("j***@example.com");
    }

    @Test
    void email_nullOrBlank_returnsLiteralNull() {
        assertThat(AuditMask.email(null)).isEqualTo("null");
        assertThat(AuditMask.email("")).isEqualTo("null");
    }

    @Test
    void email_malformed_returnsStars() {
        assertThat(AuditMask.email("no-at-sign")).isEqualTo("***");
        assertThat(AuditMask.email("@onlydomain.com")).isEqualTo("***");
        assertThat(AuditMask.email("nolocal@")).isEqualTo("***");
    }

    @Test
    void tail4_keepsLast4Digits_stripsNonDigits() {
        assertThat(AuditMask.tail4("(11) 98765-4321")).isEqualTo("***4321");
        assertThat(AuditMask.tail4("12345678901")).isEqualTo("***8901");
    }

    @Test
    void tail4_shortOrBlank_returnsStars() {
        assertThat(AuditMask.tail4(null)).isEqualTo("null");
        assertThat(AuditMask.tail4("12")).isEqualTo("***");
    }
}
