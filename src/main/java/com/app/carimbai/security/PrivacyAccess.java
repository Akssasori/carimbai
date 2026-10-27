package com.app.carimbai.security;

import com.app.carimbai.enums.AuditActorType;
import com.app.carimbai.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class PrivacyAccess {
    public record Subject(AuditActorType type,Long id,UUID key) {}
    public Subject current() {
        var customer=SecurityUtils.getCurrentCustomerOrNull();
        if (customer!=null) return new Subject(AuditActorType.CUSTOMER,customer.getId(),customer.getPrivacyKey());
        var staff=SecurityUtils.getCurrentStaffUserOrNull();
        if (staff!=null) return new Subject(AuditActorType.STAFF,staff.getId(),staff.getPrivacyKey());
        throw new AccessDeniedException("Authenticated data subject required");
    }
    public Long requireOfficer() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (SecurityUtils.getCurrentStaffUserOrNull()==null || authentication==null
                || authentication.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("PRIVACY_OFFICER"))) {
            throw new AccessDeniedException("Designated privacy officer required");
        }
        return SecurityUtils.getRequiredStaffUser().getId();
    }
}
