package com.app.carimbai.services;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.StampRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class StampsService {

    private final StampTokenService tokenService;
    private final CardRepository cardRepo;
    private final StampRepository stampRepo;
    private final ProgramRepository programRepo;

    public StampsService(StampTokenService tokenService,
                         CardRepository cardRepo,
                         StampRepository stampRepo,
                         ProgramRepository programRepo) {
        this.tokenService = tokenService;
        this.cardRepo = cardRepo;
        this.stampRepo = stampRepo;
        this.programRepo = programRepo;
    }

    /**
     * Fluxo A (CUSTOMER_QR): valida token e aplica +1 selo.
     * Devolve a contagem atual e se gerou recompensa.
     */
    @Transactional
    public StampResponse handleCustomer(CustomerQrPayload p, RequestMeta meta) {
        // 1) validar e consumir token efêmero (HMAC + TTL + anti-replay)
        var payload = new TokenPayload("CUSTOMER_QR", p.cardId(), p.nonce(), p.exp(), p.sig());
        tokenService.validateAndConsume(payload);

        // 2) localizar card + regra do programa
        Card card = cardRepo.findById(p.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + p.cardId()));

        var program = programRepo.findById(card.getProgram().getId())
                .orElseThrow(() -> new IllegalStateException("Program not found for card " + p.cardId()));

        // 3) incrementar contagem (negócio simples; regra de limites pode ser evoluída aqui)
        card.setStampsCount(card.getStampsCount() + 1);
        card = cardRepo.save(card);

        // 4) registrar stamp para auditoria
        var stamp = new Stamp();
        stamp.setCard(card);
        stamp.setSource(StampSource.A);
        if (meta != null) {
            stamp.setUserAgent(meta.userAgent());
            // se quiser salvar IP depois, mapeie adequadamente para INET
        }
        stampRepo.save(stamp);

        // 5) checar se emitiu recompensa (MVP não cria Reward automático; só informa)
        boolean rewardIssued = card.getStampsCount() >= program.getRuleTotalStamps();

        return new StampResponse(true, card.getId(), card.getStampsCount(),
                program.getRuleTotalStamps(), rewardIssued);
    }

}
