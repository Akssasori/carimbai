package com.app.carimbai.services.social;

public record VerifiedSocialIdentity(
        String subject,
        String email,
        String name
) {
}
