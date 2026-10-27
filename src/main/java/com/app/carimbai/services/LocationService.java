package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.staff.admin.LocationFlags;
import com.app.carimbai.dtos.staff.admin.LocationItem;
import com.app.carimbai.dtos.staff.admin.UpdateLocationRequest;
import com.app.carimbai.mappers.LocationMapper;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditSecurityService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final MerchantService merchantService;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;
    private final LocationMapper locationMapper;
    private final AuditSecurityService audit;

    public Location saveLocationByMerchantId(Long merchantId, CreateLocationRequest request) {

        SecurityUtils.requireActiveMerchant(merchantId); // SEC-020
        Merchant merchant = merchantService.findById(merchantId);

        Location loc = new Location();
        loc.setMerchant(merchant);
        loc.setName(request.name());
        loc.setAddress(request.address());
        // flags default já vem do DDL, mas se quiser:
        loc.setFlags("{}");

        return locationRepository.save(loc);
    }

    @Transactional(readOnly = true)
    public List<LocationItem> list(Long merchantId, int page, int size) {
        SecurityUtils.requireActiveMerchant(merchantId);
        return locationRepository.findByMerchantIdOrderByIdAsc(merchantId,
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))))
                .stream().map(this::toItem).toList();
    }

    @Transactional
    public LocationItem update(Long merchantId, Long locationId, UpdateLocationRequest request) {
        SecurityUtils.requireActiveMerchant(merchantId);
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        if (!merchantId.equals(location.getMerchant().getId())) {
            throw new AccessDeniedException("Location does not belong to the active merchant");
        }
        if (request.name() != null) {
            if (request.name().isBlank()) throw new IllegalArgumentException("Location name cannot be blank");
            location.setName(request.name());
        }
        if (request.address() != null) location.setAddress(request.address());
        if (request.active() != null) location.setActive(request.active());
        if (request.flags() != null) {
            try {
                location.setFlags(objectMapper.writeValueAsString(request.flags()));
            } catch (JacksonException ex) {
                throw new IllegalArgumentException("Invalid location flags");
            }
        }
        locationRepository.save(location);
        audit.success(AuditEvent.LOCATION_UPDATE, Map.of("locationId", locationId, "merchantId", merchantId,
                "actorStaffId", SecurityUtils.getRequiredStaffUser().getId()));
        return toItem(location);
    }

    private LocationItem toItem(Location location) {
        try {
            var flags = objectMapper.readTree(location.getFlags() == null ? "{}" : location.getFlags());
            return locationMapper.toLocationItem(location, new LocationFlags(
                    flags.path("requirePinOnRedeem").asBoolean(true), flags.path("enableScanA").asBoolean(true),
                    flags.path("enableScanB").asBoolean(false)));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Invalid stored location flags");
        }
    }
}
