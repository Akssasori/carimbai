package com.app.carimbai.execption;

public class EmailAlreadyLinkedException extends RuntimeException {
    public EmailAlreadyLinkedException(String email) {
        super("O email " + email + " já está vinculado a outro provedor social. Faça login com o provedor original.");
    }
}
