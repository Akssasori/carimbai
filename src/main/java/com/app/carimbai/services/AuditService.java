package com.app.carimbai.services;

import com.app.carimbai.enums.AuditAction;
import com.app.carimbai.enums.AuditActorType;
import com.app.carimbai.models.AuditLog;
import com.app.carimbai.repositories.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditRepo;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public void log(AuditAction action, AuditActorType actorType, Long actorId) {
        log(AuditEntry.builder()
                .action(action)
                .actorType(actorType)
                .actorId(actorId)
                .build());
    }

    public void log(AuditEntry entry) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(entry.action)
                    .actorType(entry.actorType)
                    .actorId(entry.actorId)
                    .entityType(entry.entityType)
                    .entityId(entry.entityId)
                    .merchantId(entry.merchantId)
                    .success(entry.success != null ? entry.success : Boolean.TRUE)
                    .details(serializeDetails(entry.details))
                    .ip(resolveIp())
                    .userAgent(resolveUserAgent())
                    .build();

            auditRepo.save(auditLog);
        } catch (Exception e) {
            log.warn("Falha ao gravar audit log (action={}, actor={}:{}): {}",
                    entry.action, entry.actorType, entry.actorId, e.getMessage());
        }
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar details do audit log: {}", e.getMessage());
            return "{}";
        }
    }

    private InetAddress resolveIp() {
        HttpServletRequest request = requestProvider.getIfAvailable();

        if (request == null) {
            return null;
        }

        try {
            String forwarded = request.getHeader("X-Forwarded-For");

            String rawIp = forwarded != null && !forwarded.isBlank()
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr();

            return rawIp != null && !rawIp.isBlank()
                    ? InetAddress.getByName(rawIp)
                    : null;
        } catch (UnknownHostException | IllegalStateException e) {
            return null;
        }
    }

    private String resolveUserAgent() {
        HttpServletRequest request = requestProvider.getIfAvailable();

        if (request == null) {
            return null;
        }

        try {
            return request.getHeader("User-Agent");
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @Builder
    public static class AuditEntry {
        public AuditAction action;
        public AuditActorType actorType;
        public Long actorId;
        public String entityType;
        public Long entityId;
        public Long merchantId;
        public Boolean success;
        public Map<String, Object> details;
    }
}