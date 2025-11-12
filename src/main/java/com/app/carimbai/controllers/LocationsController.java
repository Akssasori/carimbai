package com.app.carimbai.controllers;

import com.app.carimbai.dtos.QrTokenResponse;
import com.app.carimbai.services.StampTokenService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LocationsController {

    private final StampTokenService tokenService;

    public LocationsController(StampTokenService tokenService) {
        this.tokenService = tokenService;
    }
    @Operation(summary = "Emite o QR da loja (gera o token).", description = "Gera o token efêmero (que você " +
            "renderiza como QR) da loja. — Quem chama: PWA do caixa (tela de escanear o QR do cliente)." +
            "— Uso: mostrar o QR na tela para o caixa escanear (Opção B).")
    @GetMapping("/api/locations/{id}/qr")
    public ResponseEntity<QrTokenResponse> locationQr(@PathVariable Long id) {
        // (opcional) checar flag enableScanB antes
        var t = tokenService.issueStore(id);
        return ResponseEntity.ok(new QrTokenResponse(t.type(), t.idRef(), t.nonce(), t.exp(), t.sig()));
    }

}
