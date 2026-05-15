package com.app.carimbai.enums;

/**
 * Acoes de negocio criticas rastreadas no audit log.
 * Acoes nao-criticas (criar customer, criar cartao, etc.) ficam de fora
 * intencionalmente para reduzir ruido. Adicionar quando houver necessidade real.
 */
public enum AuditAction {
    // Auth
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGIN_RATE_LIMITED,
    LOGOUT,
    TOKEN_REFRESHED,

    // Loyalty operations
    STAMP_APPLIED,
    REWARD_REDEEMED,

    // Staff management
    STAFF_CREATED,
    STAFF_ROLE_CHANGED,
    STAFF_ACTIVATED,
    STAFF_DEACTIVATED,
    STAFF_PIN_SET,

    // Programs
    PROGRAM_CREATED,
    PROGRAM_UPDATED,

    // Locations
    LOCATION_CREATED,
    LOCATION_UPDATED
}
