package com.app.carimbai.config;

import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.Security;

@Configuration
public class WebPushConfig {

    @Value("${carimbai.vapid.public-key}")
    private String vapidPublicKey;

    @Value("${carimbai.vapid.private-key}")
    private String vapidPrivateKey;

    @Value("${carimbai.vapid.subject}")
    private String vapidSubject;

    @Bean
    public PushService pushService() throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        return new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
    }
}
