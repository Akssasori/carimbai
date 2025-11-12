package com.app.carimbai.services;

import com.app.carimbai.dtos.LocationPolicy;
import com.app.carimbai.dtos.RedeemRequest;
import com.app.carimbai.dtos.RedeemResponse;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Reward;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.RewardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedeemService {

    private final StaffService staffService;
    private final CardRepository cardRepo;
    private final ProgramRepository programRepo;
    private final RewardRepository rewardRepo;
    private final LocationRepository locationRepo;
    private final LocationPolicyService policyService;

    private final boolean useLocationPolicy;

    public RedeemService(StaffService staffService,
                         CardRepository cardRepository,
                         ProgramRepository programRepo,
                         RewardRepository rewardRepo,
                         LocationRepository locationRepo,
                         LocationPolicyService policyService,
                         @Value("${carimbai.policy.use-location-policy:false}") boolean useLocationPolicy) {
        this.staffService = staffService;
        this.cardRepo = cardRepository;
        this.programRepo = programRepo;
        this.rewardRepo = rewardRepo;
        this.locationRepo = locationRepo;
        this.policyService = policyService;
        this.useLocationPolicy = useLocationPolicy;
    }

    /**
     * Valida PIN do caixa, confere regra de selos e efetua o resgate:
     * - Cria um Reward
     * - Zera a contagem de selos do Card
     */
    @Transactional
    public RedeemResponse redeem(RedeemRequest req) {
        // 1) valida o caixa (PIN)
        StaffUser cashier = staffService.validateCashierPin(req.cashierId(), req.cashierPin());

        // 2) (opcional) valida location se informado
        Location location = null;
        if (req.locationId() != null) {
            location = locationRepo.findById(req.locationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found: " + req.locationId()));
            if (!location.getMerchant().getId().equals(cashier.getMerchant().getId())) {
                throw new IllegalArgumentException("Cashier and location belong to different merchants");
            }
        }

        // 3) policy (latente; só leitura por enquanto)
        LocationPolicy policy = policyService.fromFlags(location != null ? location.getFlags() : null);
        if (useLocationPolicy) {
            if (policy.requirePinOnRedeem()) {
                // já validamos o PIN acima
            } else {
                // futuro: com JWT, permitir sem PIN se autenticado com role adequada
            }
        }

        // 4) carrega card e programa
        Card card = cardRepo.findById(req.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + req.cardId()));
        var program = programRepo.findById(card.getProgram().getId())
                .orElseThrow(() -> new IllegalStateException("Program not found for card " + req.cardId()));

        // 5) checa se tem selos suficientes
        int needed = program.getRuleTotalStamps();
        if (card.getStampsCount() < needed) {
            throw new IllegalStateException(
                    "Not enough stamps to redeem (has=%d needed=%d)".formatted(card.getStampsCount(), needed));
        }

        // 6) cria reward
        Reward reward = new Reward();
        reward.setCard(card);
        reward.setLocation(location);
        reward.setCashier(cashier);
        reward = rewardRepo.save(reward);

        // 7) zera contagem do cartão (MVP: rola o mesmo card)
        card.setStampsCount(0);
        cardRepo.save(card);

        return new RedeemResponse(true, reward.getId(), card.getId(), card.getStampsCount());
    }

}
