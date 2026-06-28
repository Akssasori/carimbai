package com.app.carimbai.controllers;

import com.app.carimbai.dtos.staff.DashboardMetricsResponse;
import com.app.carimbai.dtos.staff.RecentRewardsResponse;
import com.app.carimbai.dtos.staff.RecentStampsResponse;
import com.app.carimbai.services.StaffDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffDashboardController {

    private final StaffDashboardService service;

    public StaffDashboardController(StaffDashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Métricas do dashboard do staff (escopo: merchant ativo).",
            description = "Retorna contagens calculadas on-the-fly: carimbos hoje, prêmios hoje e total de clientes (lifetime) do merchant ativo. \"Hoje\" é o início do dia no fuso configurado em carimbai.timezone.")
    @PreAuthorize("hasAnyAuthority('CASHIER','ADMIN')")
    @GetMapping("/api/staff/dashboard/metrics")
    public ResponseEntity<DashboardMetricsResponse> metrics() {
        return ResponseEntity.ok(service.getMetrics());
    }

    @Operation(summary = "Carimbos mais recentes do merchant ativo.",
            description = "Lista os N carimbos mais recentes (ordem decrescente por when_at), independente do cashier. Limit default 10, máximo 50.")
    @PreAuthorize("hasAnyAuthority('CASHIER','ADMIN')")
    @GetMapping("/api/staff/stamps/recent")
    public ResponseEntity<RecentStampsResponse> recentStamps(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(new RecentStampsResponse(service.getRecentStamps(limit)));
    }

    @Operation(summary = "Prêmios resgatados mais recentes do merchant ativo.",
            description = "Lista os N resgates mais recentes (ordem decrescente por issued_at), independente do cashier. Limit default 10, máximo 50.")
    @PreAuthorize("hasAnyAuthority('CASHIER','ADMIN')")
    @GetMapping("/api/staff/rewards/recent")
    public ResponseEntity<RecentRewardsResponse> recentRewards(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(new RecentRewardsResponse(service.getRecentRewards(limit)));
    }
}
