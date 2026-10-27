package com.app.carimbai.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class PushEndpointPolicyTest {
    private final PushEndpointPolicy policy = new PushEndpointPolicy();

    @Test
    void acceptsKnownHttpsPushProviders() {
        for (String url : new String[]{"https://fcm.googleapis.com/fcm/send/a",
                "https://updates.push.services.mozilla.com/wpush/v2/a", "https://web.push.apple.com/a"}) {
            assertThatCode(() -> policy.validateDestination(url)).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsInternalUrlsSpoofedHostsAndAlternatePorts() {
        for (String url : new String[]{"http://127.0.0.1/a", "https://169.254.169.254/a",
                "https://localhost/a", "https://fcm.googleapis.com.attacker.test/a",
                "https://fcm.googleapis.com:8443/a", "https://user@fcm.googleapis.com/a",
                "https://attacker.test/a"}) {
            assertThatThrownBy(() -> policy.validateDestination(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void validatesWebPushKeyLengths() {
        byte[] key = new byte[65];
        key[0] = 4;
        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        assertThatCode(() -> policy.validateKeys(p256dh, auth)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateKeys(p256dh, "invalid")).isInstanceOf(IllegalArgumentException.class);
    }
}
