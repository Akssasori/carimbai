package com.app.carimbai.controllers;

import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.services.StampTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "Emite o QR do cliente (gera o token).", description = "Gera o token efêmero (que você " +
            "renderiza como QR) do cliente. — Quem chama: PWA do cliente (tela “Meu Cartão”)." +
            "— Uso: mostrar o QR na tela para o caixa escanear (Opção A).")
    @GetMapping("/{id}/qr")
    public ResponseEntity<QrTokenResponse> qr(@PathVariable Long id) {
        var t = tokenService.issueCustomer(id);
        return ResponseEntity.ok(new QrTokenResponse(t.type(), t.idRef(), t.nonce(), t.exp(), t.sig()));
    }
}
