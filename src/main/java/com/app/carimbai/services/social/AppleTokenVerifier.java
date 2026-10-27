package com.app.carimbai.services.social;

import com.app.carimbai.config.OAuthProperties;
import com.app.carimbai.enums.SocialProvider;
import com.app.carimbai.execption.InvalidSocialTokenException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.JWSAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class AppleTokenVerifier implements SocialTokenVerifier {

    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final String clientId;

    // Cache de JWKs simples (recarregado a cada erro de verificação)
    private volatile JWKSet cachedJwkSet;
    private volatile long cachedAt = 0;
    private static final long CACHE_TTL_MS = 3_600_000; // 1h

    public AppleTokenVerifier(OAuthProperties props) {
        this.clientId = props.apple().clientId();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.APPLE;
    }

    @Override
    public VerifiedSocialIdentity verify(String token) {
        try {
            if (clientId == null || clientId.isBlank()) {
                throw new InvalidSocialTokenException("Apple login is not configured");
            }
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new InvalidSocialTokenException("Invalid Apple algorithm");
            }
            var claims = jwt.getJWTClaimsSet();

            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw new InvalidSocialTokenException("Apple token com issuer inválido");
            }
            if (!claims.getAudience().contains(clientId)) {
                throw new InvalidSocialTokenException("Apple token com audience inválido");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw new InvalidSocialTokenException("Apple token expirado");
            }

            JWKSet jwkSet = getJwkSet();
            String kid = jwt.getHeader().getKeyID();
            RSAKey rsaKey = (RSAKey) jwkSet.getKeyByKeyId(kid);
            if (rsaKey == null) {
                // Forçar reload e tentar de novo
                cachedAt = 0;
                jwkSet = getJwkSet();
                rsaKey = (RSAKey) jwkSet.getKeyByKeyId(kid);
            }
            if (rsaKey == null) {
                throw new InvalidSocialTokenException("Chave publica da Apple indisponivel");
            }

            JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!jwt.verify(verifier)) {
                throw new InvalidSocialTokenException("Assinatura do Apple token inválida");
            }

            String subject = claims.getSubject();
            String email = claims.getStringClaim("email");
            String name = extractName(claims.getClaim("name"));

            if (subject == null || subject.isBlank()) {
                throw new InvalidSocialTokenException("Invalid Apple subject");
            }
            return new VerifiedSocialIdentity(subject, email, name,
                    "true".equals(String.valueOf(claims.getClaim("email_verified"))));

        } catch (InvalidSocialTokenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Apple token verification failed: {}", e.getClass().getSimpleName());
            throw new InvalidSocialTokenException("Falha ao verificar token da Apple");
        }
    }

    private JWKSet getJwkSet() {
        long now = System.currentTimeMillis();
        if (cachedJwkSet == null || (now - cachedAt) > CACHE_TTL_MS) {
            try {
                cachedJwkSet = JWKSet.load(URI.create(APPLE_JWKS_URL).toURL());
                cachedAt = now;
            } catch (Exception e) {
                throw new InvalidSocialTokenException("Não foi possível carregar chaves da Apple");
            }
        }
        return cachedJwkSet;
    }

    @SuppressWarnings("unchecked")
    private String extractName(Object nameClaim) {
        if (nameClaim == null) return null;
        if (nameClaim instanceof String s) return s;
        if (nameClaim instanceof Map<?, ?> map) {
            // Apple pode enviar { firstName: "...", lastName: "..." }
            String first = (String) ((Map<String, Object>) map).get("firstName");
            String last = (String) ((Map<String, Object>) map).get("lastName");
            if (first != null && last != null) return first + " " + last;
            if (first != null) return first;
            if (last != null) return last;
        }
        return null;
    }
}
