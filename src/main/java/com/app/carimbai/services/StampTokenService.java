package com.app.carimbai.services;

import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.models.StampToken;
import com.app.carimbai.repositories.StampTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class StampTokenService {

    private final StampTokenRepository stampTokenRepository;
    private final ThreadLocal<Mac> macThreadLocal;

    private static final Duration TTL = Duration.ofMinutes(1);

    public StampTokenService(StampTokenRepository stampTokenRepository, @Value("${carimbai.hmac-secret}") String secret) {
        this.stampTokenRepository = stampTokenRepository;
        this.macThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return mac;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize Mac instance", e);
            }
        });
    }

    public QrTokenResponse generateQrCustomer(Long cardId) {
        TokenPayload customerQr = issue("CUSTOMER_QR", cardId);
        return new QrTokenResponse(customerQr.type(),
                customerQr.idRef(),
                customerQr.nonce(),
                customerQr.exp(),
                customerQr.sig());
    }
    public TokenPayload issueStore(Long locationId) { return issue("STORE_QR", locationId); }

    private TokenPayload issue(String type, Long idRef) {
        var nonce = UUID.randomUUID();
        var exp = OffsetDateTime.now().plus(TTL);
        var payload = payload(idRef, nonce, exp);
        var sig = sign(payload);
        return new TokenPayload(type, idRef, nonce, exp.toEpochSecond(), sig);
    }

    public StampToken validateAndConsume(TokenPayload p) {
        // exp
        var expTime = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(p.exp()),
                java.time.ZoneOffset.UTC
        );

        if (OffsetDateTime.now().isAfter(expTime)) throw new IllegalArgumentException("Token expired");

        // assinatura
        var expected = sign(payload(p.idRef(), p.nonce(), expTime));
        if (!constantTimeEquals(expected, p.sig())) throw new IllegalArgumentException("Invalid signature");

        // anti-replay
        if (stampTokenRepository.existsByNonce(p.nonce())) throw new IllegalStateException("Replay detected");

        // persist uso
        var entity = new StampToken();
        entity.setType(p.type());
        entity.setIdRef(p.idRef());
        entity.setNonce(p.nonce());
        entity.setExpAt(expTime);
        entity.setUsedAt(OffsetDateTime.now());
        entity.setSignature(p.sig());
        return stampTokenRepository.save(entity);
    }

    private String payload(Long idRef, UUID nonce, OffsetDateTime exp) {
        return idRef + "|" + nonce + "|" + exp.toEpochSecond();
    }

    private String sign(String payload) {
        var mac = macThreadLocal.get();
        mac.reset();
        byte[] out = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i=0; i<a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

}
