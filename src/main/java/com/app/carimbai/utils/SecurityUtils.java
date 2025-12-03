package com.app.carimbai.utils;

import com.app.carimbai.models.core.StaffUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static StaffUser getCurrentStaffUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof StaffUser staff)) {
            return null;
        }
        return staff;
    }

    public static StaffUser getRequiredStaffUser() {
        StaffUser staff = getCurrentStaffUserOrNull();
        if (staff == null) {
            throw new IllegalStateException("No authenticated staff user");
        }
        return staff;
    }

    public static StaffUser getCurrentStaffUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof StaffUser staff)) {
            throw new IllegalStateException("No authenticated staff user");
        }
        return staff;
    }
}
