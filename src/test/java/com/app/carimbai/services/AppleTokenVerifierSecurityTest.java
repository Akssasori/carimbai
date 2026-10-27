package com.app.carimbai.services;

import com.app.carimbai.config.OAuthProperties;
import com.app.carimbai.execption.InvalidSocialTokenException;
import com.app.carimbai.services.social.AppleTokenVerifier;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class AppleTokenVerifierSecurityTest {
    @Test
    void verifiesSignedAppleTokenWithoutNetworkAndRejectsOtherAudiences() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var verifier = new AppleTokenVerifier(new OAuthProperties(null, new OAuthProperties.Apple("our-app", "team"), null));
        ReflectionTestUtils.setField(verifier, "cachedJwkSet", new JWKSet(key.toPublicJWK()));
        ReflectionTestUtils.setField(verifier, "cachedAt", System.currentTimeMillis());
        for (String audience : new String[]{"our-app", "other-app"}) {
            var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                    new JWTClaimsSet.Builder().issuer("https://appleid.apple.com").audience(audience).subject("customer")
                            .expirationTime(new Date(System.currentTimeMillis() + 60000))
                            .claim("email", "customer@example.com").claim("email_verified", true).build());
            token.sign(new RSASSASigner(key));
            if (audience.equals("our-app")) {
                assertThat(verifier.verify(token.serialize()).emailVerified()).isTrue();
            } else {
                assertThatThrownBy(() -> verifier.verify(token.serialize())).isInstanceOf(InvalidSocialTokenException.class);
            }
        }
    }

    @Test
    void missingConfigurationFailsClosed() {
        var verifier = new AppleTokenVerifier(new OAuthProperties(null, new OAuthProperties.Apple("", ""), null));
        assertThatThrownBy(() -> verifier.verify("untrusted")).isInstanceOf(InvalidSocialTokenException.class);
    }

    @Test
    void rejectsExpiredWrongSignatureAndMalformedTokens() throws Exception {
        var trusted = new RSAKeyGenerator(2048).keyID("trusted").generate();
        var untrusted = new RSAKeyGenerator(2048).keyID("trusted").generate();
        var verifier = new AppleTokenVerifier(new OAuthProperties(null, new OAuthProperties.Apple("our-app", "team"), null));
        ReflectionTestUtils.setField(verifier, "cachedJwkSet", new JWKSet(trusted.toPublicJWK()));
        ReflectionTestUtils.setField(verifier, "cachedAt", System.currentTimeMillis());
        for (boolean expired : new boolean[]{true, false}) {
            var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("trusted").build(),
                    new JWTClaimsSet.Builder().issuer("https://appleid.apple.com").audience("our-app").subject("customer")
                            .expirationTime(new Date(System.currentTimeMillis() + (expired ? -60000 : 60000))).build());
            token.sign(new RSASSASigner(expired ? trusted : untrusted));
            assertThatThrownBy(() -> verifier.verify(token.serialize())).isInstanceOf(InvalidSocialTokenException.class);
        }
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(InvalidSocialTokenException.class);
    }
}
