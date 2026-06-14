package com.app.carimbai.security.audit;

/**
 * Mascaramento de PII para a trilha de auditoria (FIX-10 / SEC-027). Evita
 * gravar e-mail/telefone/documento crus mesmo num arquivo restrito.
 */
public final class AuditMask {

    private AuditMask() {}

    /** {@code joao.silva@example.com} → {@code j***@example.com}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) return "null";
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return "***";
        char first = email.charAt(0);
        return first + "***@" + email.substring(at + 1);
    }

    /** Mantém só os 4 últimos dígitos do telefone/documento. */
    public static String tail4(String value) {
        if (value == null || value.isBlank()) return "null";
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) return "***";
        return "***" + digits.substring(digits.length() - 4);
    }
}
