package com.app.carimbai.utils;

import com.app.carimbai.models.core.StaffUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

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
        return getRequiredStaffUser();
    }

    public static Long getActiveMerchantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context");
        }
        Object credentials = auth.getCredentials();
        if (credentials instanceof Map<?, ?> map) {
            Object mid = map.get("merchantId");
            if (mid instanceof Long l) return l;
            if (mid instanceof Integer i) return i.longValue();
        }
        throw new IllegalStateException("No active merchantId in security context");
    }

    public static String getActiveRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context");
        }
        Object credentials = auth.getCredentials();
        if (credentials instanceof Map<?, ?> map) {
            Object role = map.get("role");
            if (role instanceof String s) return s;
        }
        throw new IllegalStateException("No active role in security context");
    }
}
