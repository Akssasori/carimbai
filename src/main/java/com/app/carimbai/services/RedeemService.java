package com.app.carimbai.services;

import com.app.carimbai.dtos.RedeemRequest;
import com.app.carimbai.dtos.RedeemResponse;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Reward;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.RewardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedeemService {

    private final CardRepository cardRepo;
    private final RewardRepository rewardRepo;
    private final LocationRepository locationRepo;
    private final int stampsNeeded;

    public RedeemService(CardRepository cardRepository,
                         RewardRepository rewardRepo,
                         LocationRepository locationRepo,
                         @Value("${carimbai.stamps-needed:10}") int stampsNeeded) {
        this.cardRepo = cardRepository;
        this.rewardRepo = rewardRepo;
        this.locationRepo = locationRepo;
        this.stampsNeeded = stampsNeeded;
    }

    @Transactional
    public RedeemResponse redeem(RedeemRequest req) {
        
        Location location = null;
        if (req.locationId() != null) {
            location = locationRepo.findById(req.locationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found: " + req.locationId()));
        }

        Card card = cardRepo.findById(req.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + req.cardId()));

        if (card.getStampsCount() < stampsNeeded) {
            throw new IllegalStateException(
                    "Not enough stamps to redeem (has=%d needed=%d)".formatted(card.getStampsCount(), stampsNeeded));
        }

        Reward reward = new Reward();
        reward.setCard(card);
        reward.setLocation(location);
        reward = rewardRepo.save(reward);

        card.setStampsCount(0);
        cardRepo.save(card);

        return new RedeemResponse(true, reward.getId(), card.getId(), card.getStampsCount());
    }
}
