package com.app.carimbai.services;

import com.app.carimbai.config.PrivacyProperties;
import com.app.carimbai.config.PrivacyProperties.RetentionCategory;
import com.app.carimbai.repositories.PrivacyRetentionRepository;
import com.app.carimbai.security.PrivacyAccess;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class PrivacyRetentionService {
    private final PrivacyProperties settings;
    private final PrivacyRetentionRepository repository;
    private final PrivacyAccess access;
    private final MeterRegistry metrics;
    @Transactional
    public List<Map<String,Object>> run(boolean dryRun) { access.requireOfficer(); return execute(dryRun); }
    @Scheduled(fixedDelayString="${carimbai.privacy.retention-interval-ms:3600000}")
    @Transactional
    public void scheduled() {
        if (!settings.isRetentionEnabled() || settings.isRestorationQuarantine()) return;
        try { execute(settings.isRetentionDryRun()); }
        catch (RuntimeException ex) {
            metrics.counter("carimbai.privacy.retention.failures").increment();
            log.error("Privacy retention job failed: {}",ex.getClass().getSimpleName());
            throw ex;
        }
    }
    private List<Map<String,Object>> execute(boolean dryRun) {
        if (!dryRun && (!settings.isRetentionEnabled() || settings.isRetentionDryRun()
                || settings.getRetentionApprovalRef().isBlank())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Approved retention policy and explicit execution configuration required");
        }
        int limit=settings.getRetentionBatchSize();
        if (limit<1 || limit>1000) throw new IllegalArgumentException("Retention batch must be between 1 and 1000");
        List<Map<String,Object>> result=new ArrayList<>();
        for (var category:RetentionCategory.values()) {
            Integer days=settings.getRetentionDays().get(category);
            if (days==null) { result.add(Map.of("category",category.name(),"status","POLICY_UNDEFINED","dryRun",true)); continue; }
            if (days<1 || days>36500) throw new IllegalArgumentException("Retention days must be a reviewed positive period");
            Map<String,Object> batch=repository.batch(category,Instant.now().minus(days,ChronoUnit.DAYS),limit,dryRun);
            result.add(batch);
            metrics.counter("carimbai.privacy.retention.processed","category",category.name())
                    .increment(((Number)batch.get("processed")).doubleValue());
        }
        metrics.counter("carimbai.privacy.retention.runs","dry_run",Boolean.toString(dryRun)).increment();
        return result;
    }
}
