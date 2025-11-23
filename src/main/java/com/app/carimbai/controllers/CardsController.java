package com.app.carimbai.controllers;

import com.app.carimbai.dtos.CardListResponse;
import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.services.CardsService;
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
    private final CardsService cardsService;

    public CardsController(StampTokenService tokenService, CardsService cardsService) {
        this.tokenService = tokenService;
        this.cardsService = cardsService;
    }

    @Operation(summary = "Lista todos os cartões do cliente",
               description = "Retorna todos os cartões de fidelidade do cliente com informações do programa e progresso.")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CardListResponse> listCustomerCards(@PathVariable Long customerId) {
        return ResponseEntity.ok(cardsService.getCustomerCards(customerId));
    }

    @Operation(summary = "Emite o QR do cliente (gera o token).", description = "Gera o token efêmero (que você " +
            "renderiza como QR) do cliente. — Quem chama: PWA do cliente (tela \"Meu Cartão\")." +
            "— Uso: mostrar o QR na tela para o caixa escanear (Opção A).")
    @GetMapping("/{id}/qr")
    public ResponseEntity<QrTokenResponse> qr(@PathVariable Long id) {
        return ResponseEntity.ok(tokenService.issueCustomer(id));
    }
}
