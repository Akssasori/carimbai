package com.app.carimbai.controllers;

import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.services.StampTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardsController {

    private final StampTokenService tokenService;

    public CardsController(StampTokenService tokenService) { this.tokenService = tokenService; }

    @GetMapping("/{id}/qr")
    public ResponseEntity<QrTokenResponse> qr(@PathVariable Long id) {
        var t = tokenService.issueCustomer(id);
        return ResponseEntity.ok(new QrTokenResponse(t.type(), t.idRef(), t.nonce(), t.exp(), t.sig()));
    }
}
