package com.app.carimbai.services;

import com.app.carimbai.dtos.CardItemDto;
import com.app.carimbai.dtos.CardListResponse;
import com.app.carimbai.dtos.admin.CreateCardRequest;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.repositories.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardsService {

    private final CardRepository cardRepository;
    private final ProgramService programService;
    private final CustomerService customerService;

    @Value("${carimbai.stamps-needed:10}")
    private Integer defaultStampsNeeded;


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

//    @Transactional
//    public Card createCard(CreateCardRequest request) {
//
//        var program = programService.findById(request.programId());
//        var customer = customerService.findById(request.customerId());
//
//        // Honra o unique (program, customer): se já existir, apenas retorna
//        return cardRepository.findByProgramIdAndCustomerId(program.getId(), customer.getId())
//                .orElseGet(() -> {
//                    Card c = new Card();
//                    c.setProgram(program);
//                    c.setCustomer(customer);
//                    c.setStampsCount(0);
//                    // status default ACTIVE já está no @Builder, mas set explicito:
//                    // c.setStatus(CardStatus.ACTIVE);
//                    return cardRepository.save(c);
//                });
//    }

    @Transactional
    public Card getOrCreateCard(Long programId, Long customerId) {

        var program = programService.findById(programId);
        var customer = customerService.findById(customerId);

        return cardRepository.findByProgramIdAndCustomerId(program.getId(), customer.getId())
                .orElseGet(() -> {
                    Card c = new Card();
                    c.setProgram(program);
                    c.setCustomer(customer);
                    c.setStampsCount(0);
                    return cardRepository.save(c);
                });
    }

}
