package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateMerchantRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.repositories.MerchantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public Merchant createMerchant(@Valid CreateMerchantRequest request) {

        return merchantRepository.save(Merchant.builder()
                .name(request.name())
                .document(request.document())
                .active(true)
                .build());
    }

    public Merchant findById(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
    }
}
