package com.app.carimbai.security;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Base64;
import java.util.Locale;

@Component
public class PushEndpointPolicy {
    public void validateDestination(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = host.equals("fcm.googleapis.com")
                    || host.equals("updates.push.services.mozilla.com")
                    || host.endsWith(".push.apple.com") || host.endsWith(".notify.windows.com");
            if (!"https".equals(uri.getScheme()) || !allowed || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443
                    || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unsupported push endpoint");
        }
    }

    public void validateKeys(String p256dh, String auth) {
        try {
            byte[] publicKey = Base64.getUrlDecoder().decode(p256dh);
            byte[] secret = Base64.getUrlDecoder().decode(auth);
            if (publicKey.length != 65 || publicKey[0] != 4 || secret.length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid push keys");
        }
    }
}
