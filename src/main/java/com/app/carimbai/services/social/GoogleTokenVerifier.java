package com.app.carimbai.services.social;

import com.app.carimbai.config.OAuthProperties;
import com.app.carimbai.enums.SocialProvider;
import com.app.carimbai.execption.InvalidSocialTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(OAuthProperties props) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(props.google().clientId()))
                .build();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public VerifiedSocialIdentity verify(String token) {
        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new InvalidSocialTokenException("Google token inválido ou expirado");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new VerifiedSocialIdentity(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name")
            );
        } catch (InvalidSocialTokenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao verificar Google token: {}", e.getMessage());
            throw new InvalidSocialTokenException("Falha ao verificar token do Google");
        }
    }
}
