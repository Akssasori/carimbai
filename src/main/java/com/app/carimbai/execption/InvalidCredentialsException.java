package com.app.carimbai.execption;

/**
 * Falha de autenticação genérica de staff (FIX-08 / SEC-008). Cobre uniformemente:
 * e-mail inexistente, senha inválida, usuário inativo, sem vínculo a merchant.
 * Mensagem nunca distingue o motivo para o cliente — o motivo real fica na trilha
 * de auditoria. Mapeada para 401 pelo {@code GlobalExceptionHandler}.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
