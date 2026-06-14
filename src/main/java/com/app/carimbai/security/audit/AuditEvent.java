package com.app.carimbai.security.audit;

/**
 * Eventos de auditoria mínimos (FIX-10 / SEC-026). Manter conservador — só o
 * que importa para investigação de incidente, sem virar log de aplicação.
 */
public enum AuditEvent {
    /** Login de staff (e-mail+senha) — SUCCESS/FAILURE. */
    STAFF_LOGIN,
    /** Troca de merchant ativo no token de staff. */
    STAFF_SWITCH_MERCHANT,
    /** Login social do cliente (Google/Apple/Facebook) — SUCCESS/FAILURE. */
    CUSTOMER_SOCIAL_LOGIN,
    /** Login-light de cliente disparado por staff (balcão). */
    CUSTOMER_LOGIN_OR_REGISTER,
    /** PIN de caixa validado/recusado. */
    CASHIER_PIN_VALIDATE,
    /** PIN de caixa bloqueado por excesso de tentativas (FIX-04). */
    CASHIER_PIN_LOCKED,
    /** Definição/alteração de PIN de caixa. */
    CASHIER_PIN_SET,
    /** Acesso negado por escopo/posse (cross-tenant ou cross-customer). */
    ACCESS_DENIED,
    /** Resgate de recompensa. */
    REDEEM,
    /** Enroll de cliente em programa (staff escaneia QR do cliente). */
    CARD_ENROLL,
    /** Criação de merchant (PLATFORM_ADMIN). */
    MERCHANT_CREATE,
    /** Criação/desativação de staff. */
    STAFF_USER_CREATE
}
