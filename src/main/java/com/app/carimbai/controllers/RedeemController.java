package com.app.carimbai.controllers;

import com.app.carimbai.dtos.RedeemRequest;
import com.app.carimbai.dtos.RedeemResponse;
import com.app.carimbai.services.RedeemService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @ApiResponse(
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "Exemple request",
                                    value = """
                                            {
                                                "cardId": 123,
                                                "cashierId": 45,
                                                "cashierPin": "1234",
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
    @PostMapping
    public ResponseEntity<RedeemResponse> redeem(@Valid @RequestBody RedeemRequest req) {
        return ResponseEntity.ok(service.redeem(req));
    }
}
