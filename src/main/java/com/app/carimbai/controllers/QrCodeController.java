package com.app.carimbai.controllers;

import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.services.StampTokenService;
import com.app.carimbai.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrCodeController {

    private final StampTokenService tokenService;
    private final CardRepository cardRepository;

    @Operation(summary = "Emite o QR do cliente (gera o token).", description = "Gera o token efêmero (que você " +
            "renderiza como QR) do cliente. — Quem chama: PWA do cliente (tela \"Meu Cartão\")." +
            "— Uso: mostrar o QR na tela para o caixa escanear (Opção A).")
    @GetMapping("/{id}")
    public ResponseEntity<QrTokenResponse> qr(@PathVariable Long id) {
        // O parâmetro `id` é o cardId. Cliente só pode pedir QR do próprio cartão.
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + id));

        Long authenticatedCustomerId = SecurityUtils.getRequiredCustomerId();
        if (!card.getCustomer().getId().equals(authenticatedCustomerId)) {
            throw new AccessDeniedException("Customer can only generate QR for their own card");
        }

        return ResponseEntity.ok(tokenService.generateQrCustomer(id));
    }
}
