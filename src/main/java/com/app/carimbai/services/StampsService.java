package com.app.carimbai.services;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampRequest;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.CardStatus;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.execption.CardReadyToRedeemException;
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

        if (idemKey != null && !idemKey.isBlank()) {
            // idempotência por chamada
            idempotencyService.acquireOrThrow(idemKey);
        }

        // rate-limit por cartão
        checkRateLimit(customerQrPayload.cardId());

        // carrega card
        Card card = cardRepo.findById(customerQrPayload.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + customerQrPayload.cardId()));

        if (!card.getProgram().getMerchant().getId().equals(staffUser.getMerchant().getId())) {
            throw new IllegalArgumentException("Card does not belong to staff merchant");
        }

        // ── Regra: card já está pronto para resgate → bloqueia novo carimbo ──
        if (CardStatus.READY_TO_REDEEM.equals(card.getStatus())) {
            throw new CardReadyToRedeemException("Card " + card.getId() + " is ready to redeem.");
        }

        // valida token + persiste uso → precisamos do tokenId
        var tokenPayload = new TokenPayload(
                "CUSTOMER_QR",
                customerQrPayload.cardId(),
                customerQrPayload.nonce(),
                customerQrPayload.exp(),
                customerQrPayload.sig()
        );

        StampToken savedToken = tokenService.validateAndConsume(tokenPayload);

        var program = card.getProgram();
        int needed = program.getRuleTotalStamps() != null
                ? program.getRuleTotalStamps()
                : defaultStampsNeeded;

        // ── Regra: nunca passa de `needed` ──
        int currentStamps = card.getStampsCount();
        if (currentStamps >= needed) {
            throw new CardReadyToRedeemException("Card " + card.getId() + " is ready to redeem.");
        }

        // incrementa — nunca ultrapassa `needed`
        int newStampsCount = Math.min(currentStamps + 1, needed);
        card.setStampsCount(newStampsCount);

        // ── Regra: ao bater `needed` → seta status e trava ──
        boolean rewardIssued = newStampsCount >= needed;
        if (rewardIssued) {
            card.setStatus(CardStatus.READY_TO_REDEEM);
        }

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

                if (!locRef.getMerchant().getId().equals(staffUser.getMerchant().getId())) {
                    throw new IllegalArgumentException("Location does not belong to staff merchant");
                }

                stamp.setLocation(locRef);
            }
        }

        stampRepo.save(stamp);

        return new StampResponse(true,
                card.getId(),
                card.getStampsCount(),
                needed,
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
