package com.app.carimbai.facade;

import com.app.carimbai.models.fidelity.Customer;

/** Resultado de uma autenticação de cliente: a entidade + o JWT emitido (FIX-02). */
public record CustomerSession(Customer customer, String token) {
}
