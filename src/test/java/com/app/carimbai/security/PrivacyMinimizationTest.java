package com.app.carimbai.security;

import com.app.carimbai.config.PrivacyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PrivacyMinimizationTest {
    @Test void auditProjectionDropsCredentialsContactsAndFreeText() {
        var projected=AuditDataMinimizer.project(Map.of("customerId",1,"email","private@example.test","token","secret",
                "phone","123456","name","Private","details",Map.of("password","secret"),"reason","Name with spaces\nsecret"));
        assertThat(projected).containsExactlyEntriesOf(Map.of("customerId",1));
    }
    @Test void networkMetadataIsNotCollectedByDefault() {
        var metadata=new NetworkMetadata(new PrivacyProperties());
        var request=new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.1");
        assertThat(metadata.ip(request)).isNull();
        assertThat(metadata.userAgent("identifying user agent")).isNull();
    }
    @Test void configuredNetworkCollectionRejectsHostnamesAndForwardedSpoofingAndBoundsAgent() {
        var settings=new PrivacyProperties(); settings.setCollectNetworkMetadata(true);
        var metadata=new NetworkMetadata(settings);
        var request=new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.1"); request.addHeader("X-Forwarded-For","203.0.113.99");
        assertThat(metadata.ip(request).getHostAddress()).isEqualTo("192.0.2.1");
        request.setRemoteAddr("example.test"); assertThat(metadata.ip(request)).isNull();
        assertThat(metadata.userAgent("x".repeat(300)+"\r\nprivate")).hasSize(256).doesNotContain("\r","\n");
    }
}
