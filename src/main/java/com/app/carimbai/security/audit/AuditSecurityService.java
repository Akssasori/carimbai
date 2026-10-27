package com.app.carimbai.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trilha de auditoria mínima (FIX-10 / SEC-026/027). Escreve para o logger
 * dedicado {@code audit}, que o {@code logback-spring.xml} roteia para um arquivo
 * próprio com rotação/retenção independentes (e nunca para o stdout cru).
 *
 * <p>API: {@link #event(AuditEvent, String, Map)} — evento (enum), outcome
 * ({@code SUCCESS}/{@code FAILURE}/{@code DENIED}), e um mapa de atributos. PII
 * deve ser sempre mascarada via {@link AuditMask} antes de virar atributo.
 */
@Service
@lombok.RequiredArgsConstructor
public class AuditSecurityService {
    private final com.app.carimbai.security.NetworkMetadata metadata;

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");

    public void event(AuditEvent type, String outcome, Map<String, Object> attrs) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("event", type.name());
        safe.put("outcome", outcome);
        addRequestContext(safe);
        if (attrs != null) safe.putAll(com.app.carimbai.security.AuditDataMinimizer.project(attrs));
        AUDIT.info("{}", format(safe));
    }

    public void success(AuditEvent type, Map<String, Object> attrs) {
        event(type, "SUCCESS", attrs);
    }

    public void failure(AuditEvent type, Map<String, Object> attrs) {
        event(type, "FAILURE", attrs);
    }

    public void denied(AuditEvent type, Map<String, Object> attrs) {
        event(type, "DENIED", attrs);
    }

    private void addRequestContext(Map<String, Object> attrs) {
        var customer=com.app.carimbai.utils.SecurityUtils.getCurrentCustomerOrNull();
        var staff=com.app.carimbai.utils.SecurityUtils.getCurrentStaffUserOrNull();
        if (customer!=null) { attrs.put("actorType","CUSTOMER"); attrs.put("actorId",customer.getId()); }
        else if (staff!=null) { attrs.put("actorType","STAFF"); attrs.put("actorId",staff.getId()); }
        try {
            var req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            var ip=metadata.ip(req);
            if (ip!=null) attrs.put("ip",ip.getHostAddress());
            attrs.put("path", req.getRequestURI());
        } catch (IllegalStateException ignored) {
            // Fora de contexto HTTP (jobs/agendados) — apenas omite.
        }
    }

    private static String format(Map<String, Object> attrs) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : attrs.entrySet()) {
            if (!first) sb.append(' ');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
