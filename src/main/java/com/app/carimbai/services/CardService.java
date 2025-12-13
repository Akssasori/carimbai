package com.app.carimbai.services;

import com.app.carimbai.dtos.CardItemDto;
import com.app.carimbai.dtos.CardListResponse;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${carimbai.stamps-needed:10}")
    private Integer defaultStampsNeeded;

    public static final long DEFAULT_PROGRAM_ID = 1L;


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

    public Optional<Card> findByProgramIdAndCustomerId(Long programId, Long customerId) {
        return cardRepository.findByProgramIdAndCustomerId(programId, customerId);
    }

    public Card save(Card card) {
        return cardRepository.save(card);
    }

    public void ensureDefaultCard(Customer customer) {

        Program program = programService.findById(DEFAULT_PROGRAM_ID);

        if (program == null) {
            // não tem programa cadastrado ainda, não faz nada
            return;
        }

        // já existe cartão para esse programa + cliente?
        boolean exists = cardRepository
                .findByProgramIdAndCustomerId(program.getId(), customer.getId())
                .isPresent();

        if (exists) {
            return;
        }

        // se não, cria um card zerado
        Card card = new Card();
        card.setProgram(program);
        card.setCustomer(customer);
        card.setStampsCount(0);
        // status e createdAt já tem default na entidade
        cardRepository.save(card);
    }
}
