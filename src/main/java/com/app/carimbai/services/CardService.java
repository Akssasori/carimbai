package com.app.carimbai.services;

import com.app.carimbai.dtos.CardItemDto;
import com.app.carimbai.dtos.CardListResponse;
import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.dtos.redeem.RedeemQrResponse;
import com.app.carimbai.enums.CardStatus;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final ProgramService programService;
    private final CustomerService customerService;
    private final StampTokenService stampTokenService;

    @Value("${carimbai.stamps-needed:10}")
    private Integer defaultStampsNeeded;


    @Transactional(readOnly = true)
    public CardListResponse getCustomerCards(Long customerId) {
        Long authenticatedCustomerId = SecurityUtils.getRequiredCustomerId();
        if (!authenticatedCustomerId.equals(customerId)) {
            throw new AccessDeniedException("Customer can only access their own cards");
        }

        List<Card> cards = cardRepository.findByCustomerIdWithProgram(customerId);

        List<CardItemDto> cardDtos = cards.stream()
                .map(this::toDto)
                .toList();

        return new CardListResponse(cardDtos);
    }

    private CardItemDto toDto(Card card) {
        var program = card.getProgram();
        var merchant = program.getMerchant();

        int stampsNeeded = program.getRuleTotalStamps() != null
                ? program.getRuleTotalStamps()
                : defaultStampsNeeded;
        
        boolean hasReward = card.getStampsCount() >= stampsNeeded;
        
        return new CardItemDto(
                card.getId(),
                program.getId(),
                program.getName(),
                merchant.getName(),
                program.getRewardName(),
                card.getStampsCount(),
                stampsNeeded,
                card.getStatus().name(),
                hasReward
        );
    }

    @Transactional
    public CardResult getOrCreateCard(Long programId, Long customerId) {

        var program = programService.findById(programId);
        var customer = customerService.findById(customerId);

        // O staff só pode inscrever clientes em programas do merchant ativo dele.
        Long activeMerchantId = SecurityUtils.getActiveMerchantId();
        if (!program.getMerchant().getId().equals(activeMerchantId)) {
            throw new AccessDeniedException("Program does not belong to the staff's active merchant");
        }

        return cardRepository.findByProgramIdAndCustomerId(program.getId(), customer.getId())
                .map(existing -> new CardResult(existing, false))
                .orElseGet(() -> {
                    Card c = new Card();
                    c.setProgram(program);
                    c.setCustomer(customer);
                    c.setStampsCount(0);
                    return new CardResult(cardRepository.save(c), true);
                });
    }

    public Optional<Card> findByProgramIdAndCustomerId(Long programId, Long customerId) {
        return cardRepository.findByProgramIdAndCustomerId(programId, customerId);
    }

    public Card save(Card card) {
        return cardRepository.save(card);
    }

    public RedeemQrResponse generateRedeemQr(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

        Long authenticatedCustomerId = SecurityUtils.getRequiredCustomerId();
        if (!card.getCustomer().getId().equals(authenticatedCustomerId)) {
            throw new AccessDeniedException("Customer can only generate redeem QR for their own card");
        }

        if (card.getStatus() != CardStatus.READY_TO_REDEEM) {
            throw new IllegalArgumentException("Card status is not READY_TO_REDEEM");
        }

        QrTokenResponse qrTokenResponse = stampTokenService.generateRedeemQr(cardId);

        return new RedeemQrResponse(qrTokenResponse.type(),
                cardId,
                qrTokenResponse.nonce(),
                qrTokenResponse.exp(),
                qrTokenResponse.sig());

    }
}
