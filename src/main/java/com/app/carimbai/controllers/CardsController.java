package com.app.carimbai.controllers;

import com.app.carimbai.dtos.CardListResponse;
import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.dtos.admin.AdminCardResponse;
import com.app.carimbai.dtos.admin.CreateCardRequest;
import com.app.carimbai.mappers.CardMapper;
import com.app.carimbai.services.CardService;
import com.app.carimbai.services.StampTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardsController {

    private final StampTokenService tokenService;
    private final CardService cardService;
    private final CardMapper cardMapper;

    @Operation(summary = "Lista todos os cartões do cliente",
               description = "Retorna todos os cartões de fidelidade do cliente com informações do programa e progresso.")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CardListResponse> listCustomerCards(@PathVariable Long customerId) {
        return ResponseEntity.ok(cardService.getCustomerCards(customerId));
    }

    @Operation(summary = "Emite o QR do cliente (gera o token).", description = "Gera o token efêmero (que você " +
            "renderiza como QR) do cliente. — Quem chama: PWA do cliente (tela \"Meu Cartão\")." +
            "— Uso: mostrar o QR na tela para o caixa escanear (Opção A).")
    @GetMapping("/{id}/qr")
    public ResponseEntity<QrTokenResponse> qr(@PathVariable Long id) {
        return ResponseEntity.ok(tokenService.issueCustomer(id));
    }

    @Operation(summary = "Cria um novo cartão de fidelidade para um cliente em um programa.",
               description = "Cria um novo cartão de fidelidade associando um cliente a um programa específico.")
    @PostMapping
    public ResponseEntity<AdminCardResponse> createOrGetCard(@Valid @RequestBody CreateCardRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(cardMapper
                .cardToAdminCardResponse(cardService
                        .getOrCreateCard(request.programId(), request.customerId())));
    }
}
