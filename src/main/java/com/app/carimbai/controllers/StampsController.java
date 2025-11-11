package com.app.carimbai.controllers;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.StampRequest;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.dtos.TokenPayload;
import com.app.carimbai.enums.StampSource;
import com.app.carimbai.models.fidelity.Stamp;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.StampRepository;
import com.app.carimbai.services.StampTokenService;
import com.app.carimbai.utils.ObjectMapperHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stamp")
public class StampsController {

    private final StampTokenService tokenService;
    private final CardRepository cardRepo;
    private final StampRepository stampRepo;
    private final ProgramRepository programRepo;

    public StampsController(StampTokenService tokenService, CardRepository cardRepo,
                            StampRepository stampRepo, ProgramRepository programRepo) {
        this.tokenService = tokenService;
        this.cardRepo = cardRepo;
        this.stampRepo = stampRepo;
        this.programRepo = programRepo;
    }

    @PostMapping
    public ResponseEntity<StampResponse> stamp(@RequestBody StampRequest req) {
        return switch (req.type()) {
            case "CUSTOMER_QR" -> handleCustomer(req);
            default -> ResponseEntity.badRequest().build();
        };
    }

    // Implementação para CUSTOMER_QR (A). Para STORE_QR (B) você só cria um método semelhante.
    private ResponseEntity<StampResponse> handleCustomer(StampRequest req) {
        // desserializa payload para CustomerQrPayload
        var p = ObjectMapperHolder.INSTANCE.convertValue(req.payload(), CustomerQrPayload.class);

        var payload = new TokenPayload("CUSTOMER_QR", p.cardId(), p.nonce(), p.exp(), p.sig());
        tokenService.validateAndConsume(payload);

        var card = cardRepo.findById(p.cardId()).orElseThrow();
        card.setStampsCount(card.getStampsCount() + 1);

        var savedCard = cardRepo.save(card);

        // grava stamp
        var s = new Stamp();
        s.setCard(card);
        s.setSource(StampSource.A);
        stampRepo.save(s);

        var program = programRepo.findById(card.getProgram().getId()).orElseThrow();
        var needed = program.getRuleTotalStamps();

        boolean reward = savedCard.getStampsCount() >= needed;

        return ResponseEntity.ok(new StampResponse(true, card.getId(), savedCard.getStampsCount(), needed, reward));
    }
}
