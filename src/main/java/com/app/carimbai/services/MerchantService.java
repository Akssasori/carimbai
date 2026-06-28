package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateMerchantRequest;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.repositories.MerchantRepository;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditMask;
import com.app.carimbai.security.audit.AuditSecurityService;
import com.app.carimbai.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final AuditSecurityService audit;

    public Merchant createMerchant(@Valid CreateMerchantRequest request) {

        Merchant saved = merchantRepository.save(Merchant.builder()
                .name(request.name())
                .document(request.document())
                .active(true)
                .build());
        audit.success(AuditEvent.MERCHANT_CREATE, Map.of(
                "merchantId", saved.getId(),
                "document", AuditMask.tail4(request.document()),
                "actorStaffId", SecurityUtils.getRequiredStaffUser().getId()));
        return saved;
    }

    public Merchant findById(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
    }
}
