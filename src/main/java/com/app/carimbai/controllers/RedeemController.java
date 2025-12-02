package com.app.carimbai.controllers;

import com.app.carimbai.dtos.RedeemRequest;
import com.app.carimbai.dtos.RedeemResponse;
import com.app.carimbai.services.RedeemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/redeem")
public class RedeemController {

    private final RedeemService service;

    public RedeemController(RedeemService service) {
        this.service = service;
    }

    @Operation(summary = "Resgata recompensa após atingir número necessário de carimbos",
            description = "Cliente resgata prêmio após acumular carimbos suficientes (padrão: 10). " +
                    "Após resgate, contador de carimbos é zerado.")
    @ApiResponse(
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "Exemple request",
                                    value = """
                                            {
                                                "cardId": 123,
                                                "locationId": 9
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Exemple response",
                                    value = """
                                            {
                                                 "ok": true,
                                                 "rewardId": 777,
                                                 "cardId": 123,
                                                 "stampsAfter": 0
                                            }
                                            """
                            )
                    }
            )
    )
    @PreAuthorize("hasAnyAuthority('CASHIER','ADMIN')")
    @PostMapping
    public ResponseEntity<RedeemResponse> redeem(@Valid @RequestBody RedeemRequest redeemRequest) {
        return ResponseEntity.ok(service.redeem(redeemRequest));
    }
}
