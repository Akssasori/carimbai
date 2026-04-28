package com.app.carimbai.services.social;

import com.app.carimbai.enums.SocialProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SocialTokenVerifierRegistry {

    private final Map<SocialProvider, SocialTokenVerifier> verifiers;

    public SocialTokenVerifierRegistry(List<SocialTokenVerifier> verifierList) {
        this.verifiers = verifierList.stream()
                .collect(Collectors.toMap(SocialTokenVerifier::provider, Function.identity()));
    }

    public SocialTokenVerifier get(SocialProvider provider) {
        SocialTokenVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw new IllegalArgumentException("Provedor social não suportado: " + provider);
        }
        return verifier;
    }
}
