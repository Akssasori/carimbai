package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.staff.admin.LocationFlags;
import com.app.carimbai.dtos.staff.admin.UpdateLocationRequest;
import com.app.carimbai.enums.AuditAction;
import com.app.carimbai.enums.AuditActorType;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.repositories.LocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final MerchantService merchantService;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public Location saveLocationByMerchantId(Long merchantId, CreateLocationRequest request) {

        Merchant merchant = merchantService.findById(merchantId);

        Location loc = new Location();
        loc.setMerchant(merchant);
        loc.setName(request.name());
        loc.setAddress(request.address());
        // flags default ja vem do DDL, mas se quiser:
        loc.setFlags("{}");
        loc.setActive(true);

        Location saved = locationRepository.save(loc);

        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.LOCATION_CREATED)
                .actorType(AuditActorType.STAFF)
                .entityType("Location")
                .entityId(saved.getId())
                .merchantId(merchantId)
                .details(Map.of(
                        "name", saved.getName(),
                        "address", saved.getAddress() != null ? saved.getAddress() : ""
                ))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Location> listByMerchant(Long merchantId) {
        return locationRepository.findByMerchantId(merchantId);
    }

    @Transactional
    public Location updateLocation(Long merchantId, Long locationId, UpdateLocationRequest request) {
        Location loc = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found with id: " + locationId));

        if (!loc.getMerchant().getId().equals(merchantId)) {
            throw new AccessDeniedException("Location does not belong to merchant " + merchantId);
        }

        if (request.name() != null) loc.setName(request.name());
        if (request.address() != null) loc.setAddress(request.address());
        if (request.active() != null) loc.setActive(request.active());
        if (request.flags() != null) {
            loc.setFlags(serializeFlags(request.flags()));
        }

        Location saved = locationRepository.save(loc);

        Map<String, Object> details = new HashMap<>();
        if (request.name() != null) details.put("name", saved.getName());
        if (request.address() != null) details.put("address", saved.getAddress() != null ? saved.getAddress() : "");
        if (request.active() != null) details.put("active", saved.getActive());
        if (request.flags() != null) {
            details.put("flags", Map.of(
                    "requirePinOnRedeem", Boolean.TRUE.equals(request.flags().requirePinOnRedeem()),
                    "enableScanA", Boolean.TRUE.equals(request.flags().enableScanA()),
                    "enableScanB", Boolean.TRUE.equals(request.flags().enableScanB())
            ));
        }

        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.LOCATION_UPDATED)
                .actorType(AuditActorType.STAFF)
                .entityType("Location")
                .entityId(saved.getId())
                .merchantId(merchantId)
                .details(details)
                .build());

        return saved;
    }

    /**
     * Le o JSONB armazenado em Location.flags (uma String) e devolve LocationFlags.
     * Defaults: null vira `false` para os 3 campos boolean.
     */
    public LocationFlags parseFlags(Location loc) {
        if (loc.getFlags() == null || loc.getFlags().isBlank()) {
            return new LocationFlags(false, false, false);
        }
        try {
            LocationFlags parsed = objectMapper.readValue(loc.getFlags(), LocationFlags.class);
            return new LocationFlags(
                    parsed.requirePinOnRedeem() != null && parsed.requirePinOnRedeem(),
                    parsed.enableScanA() != null && parsed.enableScanA(),
                    parsed.enableScanB() != null && parsed.enableScanB()
            );
        } catch (JsonProcessingException e) {
            // Se o JSONB esta corrompido, devolve flags default em vez de quebrar a tela.
            return new LocationFlags(false, false, false);
        }
    }

    private String serializeFlags(LocationFlags flags) {
        try {
            return objectMapper.writeValueAsString(flags);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize LocationFlags to JSON", e);
        }
    }
}
