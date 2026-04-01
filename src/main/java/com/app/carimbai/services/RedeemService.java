package com.app.carimbai.services;

import com.app.carimbai.dtos.RedeemRequest;
import com.app.carimbai.dtos.RedeemResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.CardStatus;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Reward;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.RewardRepository;
import com.app.carimbai.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedeemService {

    private final CardRepository cardRepo;
    private final RewardRepository rewardRepo;
    private final LocationRepository locationRepo;
    private final StaffService staffService;
    private final ObjectMapper objectMapper;
    private final StampTokenService stampTokenService;

    @Value("${carimbai.stamps-needed:10}")
    private Integer defaultStampsNeeded;

    @Transactional
    public RedeemResponse redeem(RedeemRequest redeemRequest, String cashierPin) {

        StaffUser staffUser = SecurityUtils.getRequiredStaffUser();
        Long activeMerchantId = SecurityUtils.getActiveMerchantId();

        Location location = null;
        if (redeemRequest.locationId() != null) {
            location = locationRepo.findById(redeemRequest.locationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found: " + redeemRequest.locationId()));

            if (!location.getMerchant().getId().equals(activeMerchantId)) {
                throw new IllegalArgumentException("Location does not belong to staff merchant");
            }

            // lê flags.requirePinOnRedeem (default true)
            boolean requirePin = isRequirePinOnRedeem(location);

            if (requirePin) {
                if (cashierPin == null || cashierPin.isBlank()) {
                    throw new IllegalArgumentException("Cashier PIN is required for redeem");
                }
                // valida PIN do próprio staff logado
                staffService.validateCashierPin(staffUser.getId(), cashierPin);
            }
        }

        Long cardId = (redeemRequest.redeemQr() != null) ? redeemRequest.redeemQr().cardId() : redeemRequest.cardId();
        if (cardId == null) throw new IllegalArgumentException("cardId is required");

        if (redeemRequest.redeemQr() != null && redeemRequest.cardId() != null
                && !redeemRequest.cardId().equals(redeemRequest.redeemQr().cardId())) {
            throw new IllegalArgumentException("cardId mismatch between request and QR payload");
        }

        // ✅ Se veio QR de resgate, validar + anti-replay
        if (redeemRequest.redeemQr() != null) {
            log.info("Redeem type REDEEM");
            var p = redeemRequest.redeemQr();

            var tokenPayload = new TokenPayload(
                    "REDEEM_QR",
                    p.cardId(),
                    UUID.fromString(p.nonce()),
                    p.exp(),
                    p.sig()
            );

            stampTokenService.validateAndConsume(tokenPayload); // reaproveita e grava ops.stamp_tokens
        }

        Card card = cardRepo.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

        if (!card.getProgram().getMerchant().getId().equals(activeMerchantId)) {
            throw new IllegalArgumentException("Card does not belong to staff merchant");
        }

        var program = card.getProgram();
        int stampsNeeded = program.getRuleTotalStamps() != null
                ? program.getRuleTotalStamps()
                : defaultStampsNeeded;

        if (card.getStatus() != CardStatus.READY_TO_REDEEM) {
            throw new IllegalStateException("CARD_NOT_READY_TO_REDEEM");
        }

        if (card.getStampsCount() < stampsNeeded) {
            throw new IllegalStateException(
                    "Not enough stamps to redeem (has=%d needed=%d)".formatted(card.getStampsCount(), stampsNeeded));
        }

        Reward reward = new Reward();
        reward.setCard(card);
        reward.setLocation(location);
        reward.setCashier(staffUser);
        reward = rewardRepo.save(reward);

        card.setStampsCount(0);
        card.setStatus(CardStatus.ACTIVE);
        cardRepo.save(card);

        return new RedeemResponse(true,
                reward.getId(),
                card.getId(),
                card.getStampsCount()
        );
    }

    private boolean isRequirePinOnRedeem(Location location) {
        try {
            String flagsJson = location.getFlags();
            if (flagsJson == null || flagsJson.isBlank()) {
                return true; // default: exige PIN
            }

            JsonNode node = objectMapper.readTree(flagsJson);
            // default true se não vier no JSON
            return node.path("requirePinOnRedeem").asBoolean(true);
        } catch (Exception e) {
            // se der problema pra ler, melhor "falhar seguro" e exigir PIN
            return true;
        }
    }

}
