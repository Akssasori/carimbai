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

import static com.app.carimbai.enums.StampType.CUSTOMER_QR;

@RestController
@RequestMapping("/api/stamp")
public class StampsController {

    private final ObjectMapper objectMapper;
    private final StampsService service;

    public StampsController(ObjectMapper objectMapper, StampsService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @Operation(summary = "Aplica o carimbo usando o token que veio do QR.", description = "Consome o token que foi lido " +
            "do QR e carimba 1 selo no cartão. Validação: HMAC+TTL+anti-replay+idempotência, incrementar " +
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
            @Valid @RequestBody StampRequest stampRequest,
            @RequestHeader(name = "User-Agent", required = false) String userAgent,
            @RequestHeader(name = "X-Location-Id", required = false) Long locationId,
            @RequestHeader(name = "Idempotency-Key") String idemKey
    ) {
        return ResponseEntity.ok(service.handleCustomer(stampRequest, userAgent, locationId, idemKey));
    }
}
