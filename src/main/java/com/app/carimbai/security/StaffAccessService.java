package com.app.carimbai.security;

import com.app.carimbai.execption.InvalidCredentialsException;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffAccessService {
    private final StaffUserMerchantRepository links;

    @Transactional(readOnly = true)
    public StaffUserMerchant requireActiveLink(Long staffId, Long merchantId) {
        if (staffId == null || merchantId == null) throw new InvalidCredentialsException();
        StaffUserMerchant link = links.findByStaffUserIdAndMerchantIdAndActiveTrue(staffId, merchantId)
                .orElseThrow(InvalidCredentialsException::new);
        if (!Boolean.TRUE.equals(link.getActive())
                || !Boolean.TRUE.equals(link.getStaffUser().getActive())
                || !Boolean.TRUE.equals(link.getMerchant().getActive())
                || link.getRole() == null) {
            throw new InvalidCredentialsException();
        }
        return link;
    }
}
