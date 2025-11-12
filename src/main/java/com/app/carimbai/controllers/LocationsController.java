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
    @Operation(summary = "Gera um token efêmero para uma loja específica, que pode ser renderizado como um QR Code",
            description = """
                Esse QR é usado pelo caixa do estabelecimento para registrar a visita do cliente, garantindo que o processo seja seguro e automatizado.
                O fluxo seria assim:
                O caixa gera o QR Code da loja.
                O cliente escaneia esse QR com seu aplicativo.
                O sistema registra a visita.
                Quando o cliente acumula 10 visitas, ele recebe a recompensa.
                """)
    public ResponseEntity<QrTokenResponse> locationQr(@PathVariable Long id) {
        // (opcional) checar flag enableScanB antes
        var t = tokenService.issueStore(id);
        return ResponseEntity.ok(new QrTokenResponse(t.type(), t.idRef(), t.nonce(), t.exp(), t.sig()));
    }

}
