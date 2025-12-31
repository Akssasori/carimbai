package com.app.carimbai.services;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampRequest;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.execption.TooManyStampsException;
import com.app.carimbai.models.StampToken;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.StampRepository;
import com.app.carimbai.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

        // payload do QR
        var customerQrPayload = objectMapper.convertValue(stampRequest.payload(), CustomerQrPayload.class);

        // staff logado (CASHIER ou ADMIN)
        StaffUser staffUser = SecurityUtils.getRequiredStaffUser();

        RequestMeta requestMeta = null;
        if (Objects.nonNull(userAgent) && Objects.nonNull(locationId)) {
            requestMeta =  new RequestMeta(userAgent, locationId);
        }

        // idempotência por chamada
        idempotencyService.acquireOrThrow(idemKey);

        // rate-limit por cartão
        checkRateLimit(customerQrPayload.cardId());

        // valida token + persiste uso → precisamos do tokenId
        var tokenPayload = new TokenPayload(
                "CUSTOMER_QR",
                customerQrPayload.cardId(),
                customerQrPayload.nonce(),
                customerQrPayload.exp(),
                customerQrPayload.sig()
        );

        StampToken savedToken = tokenService.validateAndConsume(tokenPayload);

        // carrega card
        Card card = cardRepo.findById(customerQrPayload.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + customerQrPayload.cardId()));

        if (!card.getProgram().getMerchant().getId().equals(staffUser.getMerchant().getId())) {
            throw new IllegalArgumentException("Card does not belong to staff merchant");
        }

        // incrementa contagem
        card.setStampsCount(card.getStampsCount() + 1);
        card = cardRepo.save(card);

        // monta Stamp
        var stamp = new Stamp();
        stamp.setCard(card);
        stamp.setSource(StampSource.A);
        stamp.setCashier(staffUser);
        stamp.setTokenId(savedToken.getId());

        if (requestMeta != null) {
            stamp.setUserAgent(requestMeta.userAgent());
            if (requestMeta.locationId() != null) {
                var locRef = locationRepo.findById(requestMeta.locationId())
                        .orElseThrow(() -> new IllegalArgumentException("Location not found"));

                // valida se location é do mesmo merchant do staff
                if (!locRef.getMerchant().getId().equals(staffUser.getMerchant().getId())) {
                    throw new IllegalArgumentException("Location does not belong to staff merchant");
                }

                stamp.setLocation(locRef);
            }
        }

        stampRepo.save(stamp);

        var program = card.getProgram();
        int stampsNeeded = program.getRuleTotalStamps() != null
                ? program.getRuleTotalStamps()
                : defaultStampsNeeded;

        boolean rewardIssued = card.getStampsCount() >= stampsNeeded;

        return new StampResponse(true,
                card.getId(),
                card.getStampsCount(),
                stampsNeeded,
                rewardIssued
        );
    }

    private void checkRateLimit(Long cardId) {
        var since = OffsetDateTime.now().minusSeconds(rateWindowSeconds);
        boolean recent = stampRepo.existsRecentByCard(cardId, since);
        if (recent) {
            throw new TooManyStampsException("Rate limit: stamp too soon for this card");
        }
    }
}
