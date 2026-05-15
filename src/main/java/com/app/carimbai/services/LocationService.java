package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.staff.admin.LocationFlags;
import com.app.carimbai.dtos.staff.admin.UpdateLocationRequest;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.repositories.LocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final MerchantService merchantService;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    public Location saveLocationByMerchantId(Long merchantId, CreateLocationRequest request) {

        Merchant merchant = merchantService.findById(merchantId);

        Location loc = new Location();
        loc.setMerchant(merchant);
        loc.setName(request.name());
        loc.setAddress(request.address());
        // flags default ja vem do DDL, mas se quiser:
        loc.setFlags("{}");
        loc.setActive(true);

        return locationRepository.save(loc);
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

        return locationRepository.save(loc);
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
