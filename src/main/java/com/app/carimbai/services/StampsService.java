package com.app.carimbai.services;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.execption.TooManyStampsException;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.StampRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class StampsService {

    private final StampTokenService tokenService;
    private final CardRepository cardRepo;
    private final StampRepository stampRepo;
    private final LocationRepository locationRepo;
    private final IdempotencyService idempotencyService;
    private final int rateWindowSeconds;
    private final int stampsNeeded;

    public StampsService(StampTokenService tokenService,
                         CardRepository cardRepo,
                         StampRepository stampRepo,
                         LocationRepository locationRepo,
                         IdempotencyService idempotencyService,
                         @Value("${carimbai.rate-limit.seconds:120}") int rateWindowSeconds,
                         @Value("${carimbai.stamps-needed:10}") int stampsNeeded) {
        this.tokenService = tokenService;
        this.cardRepo = cardRepo;
        this.stampRepo = stampRepo;
        this.locationRepo = locationRepo;
        this.idempotencyService = idempotencyService;
        this.rateWindowSeconds = rateWindowSeconds;
        this.stampsNeeded = stampsNeeded;
    }

    @Transactional
    public StampResponse handleCustomer(CustomerQrPayload p, RequestMeta meta, String idemKey) throws Exception {

        idempotencyService.acquireOrThrow(idemKey);

        checkRateLimit(p.cardId());

        var payload = new TokenPayload("CUSTOMER_QR", p.cardId(), p.nonce(), p.exp(), p.sig());
        tokenService.validateAndConsume(payload);

        Card card = cardRepo.findById(p.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + p.cardId()));

        card.setStampsCount(card.getStampsCount() + 1);
        card = cardRepo.save(card);

        var stamp = new Stamp();
        stamp.setCard(card);
        stamp.setSource(StampSource.A);
        if (meta != null) {
            stamp.setUserAgent(meta.userAgent());
            if (meta.locationId() != null) {
                var locRef = locationRepo.getReferenceById(meta.locationId());
                stamp.setLocation(locRef);
            }
        }
        stampRepo.save(stamp);

        boolean rewardIssued = card.getStampsCount() >= stampsNeeded;

        return new StampResponse(true, card.getId(), card.getStampsCount(), stampsNeeded, rewardIssued);
    }

    private void checkRateLimit(Long cardId) throws Exception {
        var since = OffsetDateTime.now().minusSeconds(rateWindowSeconds);
        boolean recent = stampRepo.existsRecentByCard(cardId, since);
        if (recent) {
            throw new TooManyStampsException("Rate limit: stamp too soon for this card");
        }
    }
}
