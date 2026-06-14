package com.app.carimbai.services;

import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.models.StampToken;
import com.app.carimbai.repositories.StampTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de segurança (unitários, sem contexto Spring/DB) do core de tokens HMAC
 * de selo. Verificam controles positivos confirmados nas Fases 7 e 12:
 * integridade da assinatura (não-manipulação de cardId), anti-replay por nonce,
 * checagem de expiração e ausência de confusão de tipo (CUSTOMER_QR ≠ REDEEM_QR).
 */
class StampTokenServiceTest {

    private StampTokenRepository repo;
    private StampTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(StampTokenRepository.class);
        service = new StampTokenService(repo);
        ReflectionTestUtils.setField(service, "secret", "hmac-test-secret-0123456789-abcdef");
        ReflectionTestUtils.setField(service, "qrTtlSeconds", 90);
        ReflectionTestUtils.invokeMethod(service, "init");
        when(repo.save(any(StampToken.class))).thenAnswer(inv -> {
            StampToken t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
    }

    private TokenPayload payloadFrom(QrTokenResponse r) {
        return new TokenPayload(r.type(), r.idRef(), r.nonce(), r.exp(), r.sig());
    }

    @Test
    void validatesFreshlyIssuedToken() {
        when(repo.existsByNonce(any(UUID.class))).thenReturn(false);
        QrTokenResponse qr = service.generateQrCustomer(123L);

        StampToken saved = service.validateAndConsume(payloadFrom(qr));

        assertThat(saved).isNotNull();
        verify(repo).save(any(StampToken.class));
    }

    @Test
    void rejectsTamperedCardId() {
        when(repo.existsByNonce(any(UUID.class))).thenReturn(false);
        QrTokenResponse qr = service.generateQrCustomer(123L);

        // troca idRef (cardId) mantendo a assinatura original
        TokenPayload tampered = new TokenPayload(qr.type(), 999L, qr.nonce(), qr.exp(), qr.sig());

        assertThatThrownBy(() -> service.validateAndConsume(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void rejectsReplayedNonce() {
        when(repo.existsByNonce(any(UUID.class))).thenReturn(true); // nonce já consumido
        QrTokenResponse qr = service.generateQrCustomer(123L);

        assertThatThrownBy(() -> service.validateAndConsume(payloadFrom(qr)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Replay");
    }

    @Test
    void rejectsTypeConfusionCustomerAsRedeem() {
        when(repo.existsByNonce(any(UUID.class))).thenReturn(false);
        QrTokenResponse qr = service.generateQrCustomer(123L); // tipo CUSTOMER_QR

        // mesma carga, alegando REDEEM_QR → assinatura não confere (tipo é assinado)
        TokenPayload confused = new TokenPayload("REDEEM_QR", qr.idRef(), qr.nonce(), qr.exp(), qr.sig());

        assertThatThrownBy(() -> service.validateAndConsume(confused))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void rejectsExpiredToken() {
        when(repo.existsByNonce(any(UUID.class))).thenReturn(false);
        QrTokenResponse qr = service.generateQrCustomer(123L);

        TokenPayload expired = new TokenPayload(qr.type(), qr.idRef(), qr.nonce(),
                OffsetDateTime.now().minusSeconds(60).toEpochSecond(), qr.sig());

        assertThatThrownBy(() -> service.validateAndConsume(expired))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }
}
