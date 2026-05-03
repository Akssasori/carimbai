package com.app.carimbai.services.social;

import com.app.carimbai.enums.SocialProvider;

public interface SocialTokenVerifier {
    SocialProvider provider();
    VerifiedSocialIdentity verify(String token);
}
