package com.app.carimbai.services;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.execption.TooManyStampsException;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.StampRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class StampsService {

    private final StampTokenService tokenService;
    private final CardRepository cardRepo;
    private final StampRepository stampRepo;
    private final ProgramRepository programRepo;
    private final LocationRepository locationRepo;

    // Janela de rate limit (segundos)
    private final int rateWindowSeconds;

    public StampsService(StampTokenService tokenService,
                         CardRepository cardRepo,
                         StampRepository stampRepo,
                         ProgramRepository programRepo,
                         LocationRepository locationRepo,
                         @Value("${carimbai.rate-limit.seconds:120}") int rateWindowSeconds) {
        this.tokenService = tokenService;
        this.cardRepo = cardRepo;
        this.stampRepo = stampRepo;
        this.programRepo = programRepo;
        this.locationRepo = locationRepo;
        this.rateWindowSeconds = rateWindowSeconds;
    }



    /**
     * Fluxo A (CUSTOMER_QR): valida token e aplica +1 selo.
     * Devolve a contagem atual e se gerou recompensa.
     */
    @Transactional
    public StampResponse handleCustomer(CustomerQrPayload p, RequestMeta meta) throws Exception {

        // 1) RATE LIMIT — cheque ANTES de consumir o token
        Long locId = (meta != null ? meta.locationId() : null);
        checkRateLimit(p.cardId(), locId);

        // 2) validar e consumir token efêmero (HMAC + TTL + anti-replay)
        var payload = new TokenPayload("CUSTOMER_QR", p.cardId(), p.nonce(), p.exp(), p.sig());
        tokenService.validateAndConsume(payload);

        // 3) localizar card + regra do programa
        Card card = cardRepo.findById(p.cardId())
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + p.cardId()));

        var program = programRepo.findById(card.getProgram().getId())
                .orElseThrow(() -> new IllegalStateException("Program not found for card " + p.cardId()));

        // 4) incrementar contagem (negócio simples; regra de limites pode ser evoluída aqui)
        card.setStampsCount(card.getStampsCount() + 1);
        card = cardRepo.save(card);

        // 5) registrar stamp para auditoria
        var stamp = new Stamp();
        stamp.setCard(card);
        stamp.setSource(StampSource.A);
        if (meta != null) {
            stamp.setUserAgent(meta.userAgent());
            if (meta.locationId() != null) {
                // pega referência sem fazer SELECT
                var locRef = locationRepo.getReferenceById(meta.locationId());
                stamp.setLocation(locRef);
            }
        }
        stampRepo.save(stamp);

        // 6) checar se emitiu recompensa (MVP não cria Reward automático; só informa)
        boolean rewardIssued = card.getStampsCount() >= program.getRuleTotalStamps();

        return new StampResponse(true, card.getId(), card.getStampsCount(),
                program.getRuleTotalStamps(), rewardIssued);
    }

    private void checkRateLimit(Long cardId, Long locationId) throws Exception {
        var since = OffsetDateTime.now().minusSeconds(rateWindowSeconds);
        boolean recent = stampRepo.existsRecentByCardAndLocation(cardId, locationId, since);
        if (recent) {
            // escolha sua exceção. Se tiver um handler para 429, melhor criar uma específica:
            throw new TooManyStampsException("Rate limit: stamp too soon for this card/location");
        }
    }

}
