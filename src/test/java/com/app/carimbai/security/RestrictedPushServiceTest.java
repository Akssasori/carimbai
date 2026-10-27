package com.app.carimbai.security;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class RestrictedPushServiceTest {
    @Test void encryptsAndSignsWithoutUnusedNettyTransportAndDisablesRedirects() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var generator = KeyPairGenerator.getInstance("ECDH", "BC");
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        var vapid = generator.generateKeyPair();
        var browser = generator.generateKeyPair();
        var base64 = Base64.getUrlEncoder().withoutPadding();
        var service = new RestrictedPushService(
                base64.encodeToString(Utils.encode((ECPublicKey) vapid.getPublic())),
                base64.encodeToString(Utils.encode((ECPrivateKey) vapid.getPrivate())),
                "mailto:test@example.test", new PushEndpointPolicy());
        var notification = new Notification("https://fcm.googleapis.com/fcm/send/test-only",
                browser.getPublic(), new byte[16], "test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var post = service.preparePost(notification, Encoding.AES128GCM);
        assertThat(post.getConfig().isRedirectsEnabled()).isFalse();
        assertThat(post.getConfig().getConnectTimeout()).isEqualTo(5000);
        assertThat(post.getFirstHeader("Authorization")).isNotNull();
        assertThat(post.getEntity().getContentLength()).isPositive();
    }
}
