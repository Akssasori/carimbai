package com.app.carimbai.controllers;

import com.app.carimbai.dtos.CustomerQrPayload;
import com.app.carimbai.dtos.RequestMeta;
import com.app.carimbai.dtos.StampRequest;
import com.app.carimbai.dtos.StampResponse;
import com.app.carimbai.services.StampsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stamp")
public class StampsController {

    private final ObjectMapper mapper;
    private final StampsService service;

    public StampsController(ObjectMapper mapper, StampsService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @Operation(summary = "Aplica o carimbo usando o token que veio do QR.", description = "Consome o token que foi lido " +
            "do QR e carimba 1 selo no cartão. — Quem chama: Painel do lojista (após ler o QR do cliente OU depois, " +
            "na Opção B, quando o cliente ler o QR do balcão). — Uso: validar token (HMAC+TTL+anti-replay), incrementar " +
            "stamps_count, registrar stamps e retornar a nova contagem.")
    @ApiResponse(
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "Exemple request",
                                    value = """
                                            {
                                               "type": "CUSTOMER_QR",
                                               "payload": {
                                                 "cardId": 123,
                                                 "nonce": "7d7c2b7a-0a1a-4e8c-9d5e-...",
                                                 "exp": 1731370000,
                                                 "sig": "b64url-hmac..."
                                               }
                                             }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Exemple response",
                                    value = """
                                            {
                                                "ok": true,
                                                "cardId": 123,
                                                "stamps": 7,
                                                "needed": 10,
                                                "rewardIssued": false
                                              }
                                            """
                            )
                    }
            )
    )
    @PostMapping
    public ResponseEntity<StampResponse> stamp(
            @Valid @RequestBody StampRequest req,
            @RequestHeader(name = "User-Agent", required = false) String ua,
            @RequestHeader(name = "X-Location-Id", required = false) Long locationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idemKey
    ) throws Exception {

        return switch (req.type()) {
            case CUSTOMER_QR -> {
                var p = mapper.convertValue(req.payload(), CustomerQrPayload.class);
                var meta = new RequestMeta(ua, locationId);
                // 👉 passa a idemKey para o service
                yield ResponseEntity.ok(service.handleCustomer(p, meta, idemKey));
            }
            case STORE_QR -> {
                // (B) quando ativar, mesma ideia
                yield ResponseEntity.status(501).build();
            }
            default -> ResponseEntity.badRequest().build();
        };
    }
}
