package com.app.carimbai.services.social;

public record VerifiedSocialIdentity(
        String subject,
        String email,
        String name,
        boolean emailVerified
) {
    public VerifiedSocialIdentity(String subject, String email, String name) {
        this(subject, email, name, false);
    }
}
