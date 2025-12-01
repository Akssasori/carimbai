package com.app.carimbai.services;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampRequest;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.execption.TooManyStampsException;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.StampRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.OffsetDateTime;
import java.util.Objects;

import static com.app.carimbai.enums.StampType.CUSTOMER_QR;

@Service
@RequiredArgsConstructor
public class StampsService {

    private final StampTokenService tokenService;
    private final CardRepository cardRepo;
    private final StampRepository stampRepo;
    private final LocationRepository locationRepo;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Value("${carimbai.rate-limit.seconds:120}")
    private Integer rateWindowSeconds;

    @Value("${carimbai.stamps-needed:10}")
    private Integer defaultStampsNeeded;


    @Transactional
    public StampResponse applyStamp(StampRequest stampRequest, String userAgent, Long locationId,
                                        String idemKey) {

        if (stampRequest.type() != CUSTOMER_QR) {
            throw new IllegalArgumentException("Tipo de carimbo inválido para este endpoint.");
        }

        var customerQrPayload = objectMapper.convertValue(stampRequest.payload(), CustomerQrPayload.class);

        RequestMeta requestMeta = null;
        if (Objects.nonNull(userAgent) && Objects.nonNull(locationId)) {
            requestMeta =  new RequestMeta(userAgent, locationId);
        }

        idempotencyService.acquireOrThrow(idemKey);

        checkRateLimit(customerQrPayload.cardId());

        var tokenPayload = new TokenPayload("CUSTOMER_QR", customerQrPayload.cardId(),
                customerQrPayload.nonce(), customerQrPayload.exp(), customerQrPayload.sig());

        tokenService.validateAndConsume(tokenPayload);

        Card card = cardRepo.findById(customerQrPayload.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + customerQrPayload.cardId()));

        card.setStampsCount(card.getStampsCount() + 1);
        card = cardRepo.save(card);

        var stamp = new Stamp();
        stamp.setCard(card);
        stamp.setSource(StampSource.A);
        if (requestMeta != null) {
            stamp.setUserAgent(requestMeta.userAgent());
            if (requestMeta.locationId() != null) {
                var locRef = locationRepo.getReferenceById(requestMeta.locationId());
                stamp.setLocation(locRef);
            }
        }

        stampRepo.save(stamp);

        var program = card.getProgram();
        int stampsNeeded = program.getRuleTotalStamps() != null
                ? program.getRuleTotalStamps()
                : defaultStampsNeeded;

        boolean rewardIssued = card.getStampsCount() >= stampsNeeded;

        return new StampResponse(true, card.getId(), card.getStampsCount(), stampsNeeded, rewardIssued);
    }

    private void checkRateLimit(Long cardId) {
        var since = OffsetDateTime.now().minusSeconds(rateWindowSeconds);
        boolean recent = stampRepo.existsRecentByCard(cardId, since);
        if (recent) {
            throw new TooManyStampsException("Rate limit: stamp too soon for this card");
        }
    }
}
