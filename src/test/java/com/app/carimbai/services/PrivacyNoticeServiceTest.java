package com.app.carimbai.services;

import com.app.carimbai.config.PrivacyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class PrivacyNoticeServiceTest {
    @Test void defaultNoticeIsExplicitDraftWithConfiguredContact() throws Exception {
        var service=new PrivacyNoticeService(new PrivacyProperties()); service.load();
        assertThat(service.current().published()).isFalse();
        assertThat(service.current().controllerDocument()).contains("FICTICIO");
        assertThat(service.current().contactEmail()).isEqualTo("diniz3003@gmail.com");
        assertThatThrownBy(()->service.requirePublished("draft-2026-10-26"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void booleanAloneCannotPublishTheFictitiousDraft() {
        var settings=new PrivacyProperties(); settings.setNoticeApproved(true);
        assertThatThrownBy(()->new PrivacyNoticeService(settings).load()).isInstanceOf(IllegalStateException.class);
    }
    @Test void arbitraryResourcePathsAreRejectedBeforeReadingFiles() {
        var settings=new PrivacyProperties(); settings.setNoticeResource("../../application-prod.yaml");
        assertThatThrownBy(()->new PrivacyNoticeService(settings).load()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cnpjValidatorSupportsNewAlphanumericFormatAndRejectsPlaceholders() {
        var service=new PrivacyNoticeService(new PrivacyProperties());
        assertThat((Boolean)ReflectionTestUtils.invokeMethod(service,"validCnpj","12.ABC.345/01DE-35")).isTrue();
        assertThat((Boolean)ReflectionTestUtils.invokeMethod(service,"validCnpj","00.000.000/0000-00 (FICTICIO)")).isFalse();
        assertThat((Boolean)ReflectionTestUtils.invokeMethod(service,"validCnpj","12.ABC.345/01DE-36")).isFalse();
    }
}
