package com.app.carimbai.services;

import com.app.carimbai.dtos.CardItemDto;
import com.app.carimbai.dtos.CardListResponse;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.repositories.CardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CardsService {

    private final CardRepository cardRepository;
    private final Integer stampsNeeded;

    public CardsService(
            CardRepository cardRepository,
            @Value("${carimbai.stamps-needed}") Integer stampsNeeded
    ) {
        this.cardRepository = cardRepository;
        this.stampsNeeded = stampsNeeded;
    }

    @Transactional(readOnly = true)
    public CardListResponse getCustomerCards(Long customerId) {
        List<Card> cards = cardRepository.findByCustomerIdWithProgram(customerId);
        
        List<CardItemDto> cardDtos = cards.stream()
                .map(this::toDto)
                .toList();
        
        return new CardListResponse(cardDtos);
    }

    private CardItemDto toDto(Card card) {
        var program = card.getProgram();
        var merchant = program.getMerchant();
        
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
}
