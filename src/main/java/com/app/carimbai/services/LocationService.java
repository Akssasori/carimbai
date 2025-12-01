package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.repositories.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final MerchantService merchantService;
    private final LocationRepository locationRepository;

    public Location saveLocationByMerchantId(Long merchantId, CreateLocationRequest request) {

        Merchant merchant = merchantService.findById(merchantId);

        Location loc = new Location();
        loc.setMerchant(merchant);
        loc.setName(request.name());
        loc.setAddress(request.address());
        // flags default já vem do DDL, mas se quiser:
        loc.setFlags("{}");

        return locationRepository.save(loc);
    }
}
