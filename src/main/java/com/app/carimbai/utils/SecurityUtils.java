package com.app.carimbai.utils;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Customer;
import org.springframework.security.access.AccessDeniedException;
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

    /**
     * Garante que o recurso (identificado por {@code merchantId}) pertence ao
     * merchant ativo do token. Lança {@link AccessDeniedException} (→ 403) em caso
     * de tentativa cross-tenant. Isolamento multi-tenant — SEC-020.
     */
    public static void requireActiveMerchant(Long merchantId) {
        Long active = getActiveMerchantId();
        if (merchantId == null || !merchantId.equals(active)) {
            throw new AccessDeniedException("Resource does not belong to the active merchant");
        }
    }

    // ── Cliente autenticado (FIX-02) ──

    public static Customer getCurrentCustomerOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Customer customer)) {
            return null;
        }
        return customer;
    }

    public static Long getRequiredCustomerId() {
        Customer customer = getCurrentCustomerOrNull();
        if (customer == null) {
            throw new AccessDeniedException("No authenticated customer");
        }
        return customer.getId();
    }

    /**
     * Garante que o recurso pertence ao cliente autenticado. Lança
     * {@link AccessDeniedException} (→403) em acesso a dados de outro cliente. SEC-001.
     */
    public static void requireActiveCustomer(Long customerId) {
        if (customerId == null || !customerId.equals(getRequiredCustomerId())) {
            throw new AccessDeniedException("Resource does not belong to the authenticated customer");
        }
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

    public static Long getRequiredCustomerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context");
        }
        Object credentials = auth.getCredentials();
        if (credentials instanceof Map<?, ?> map) {
            Object cid = map.get("customerId");
            if (cid instanceof Long l) return l;
            if (cid instanceof Integer i) return i.longValue();
        }
        throw new IllegalStateException("No customerId in security context");
    }

    /**
     * Garante que o merchantId no path bate com o merchant ativo do staff logado.
     * Evita que um ADMIN do merchant A passe merchantId=B na URL e opere no merchant errado.
     * Chamar no inicio de qualquer endpoint admin que recebe `merchantId` como path var.
     */
    public static void requirePathMerchantMatchesActive(Long pathMerchantId) {
        Long active = getActiveMerchantId();
        if (!active.equals(pathMerchantId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Path merchantId does not match the staff's active merchant");
        }
    }
}
