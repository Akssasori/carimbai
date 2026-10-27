package com.app.carimbai.services;

import com.app.carimbai.config.PrivacyProperties;
import com.app.carimbai.dtos.privacy.PrivacyDtos.JournalEntry;
import com.app.carimbai.enums.AuditActorType;
import com.app.carimbai.repositories.PrivacyDataRepository;
import com.app.carimbai.security.PrivacyAccess;
import com.app.carimbai.security.PrivacyAccess.Subject;
import com.app.carimbai.security.OpaqueTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.*;

@Service @RequiredArgsConstructor
public class PrivacyJournalService {
    private final PrivacyDataRepository data;
    private final PrivacyProperties settings;
    private final PrivacyAccess access;
    @Transactional
    public void sessionInvalidated(AuditActorType type,Long id) {
        data.findSubjectById(type,id).ifPresent(subject->data.effect(subject,null,"REVOKE_ALL_SESSIONS",null));
    }
    @Transactional(readOnly=true)
    public List<JournalEntry> export(long after,int limit) {
        access.requireOfficer(); requireKey();
        return data.effects(after,limit).stream().map(row-> {
            var entry=new JournalEntry(settings.getJournalOrigin(),((Number)row.get("id")).longValue(),
                    AuditActorType.valueOf((String)row.get("subject_type")),((Number)row.get("subject_id")).longValue(),
                    (UUID)row.get("subject_key"),(String)row.get("action"),row.get("merchant_id")==null ? null : ((Number)row.get("merchant_id")).longValue(),
                    ((Timestamp)row.get("occurred_at")).toInstant(),null);
            return new JournalEntry(entry.origin(),entry.id(),entry.subjectType(),entry.subjectId(),entry.subjectKey(),entry.action(),
                    entry.merchantId(),entry.occurredAt(),sign(canonical(entry)));
        }).toList();
    }
    private void requireKey() {
        if (settings.getJournalSigningSecret().length()<32 || !settings.getJournalOrigin().matches("[A-Za-z0-9_-]{1,80}")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure a separate external reconciliation signing key and origin");
        }
    }
    private String canonical(JournalEntry e) {
        return e.origin()+"|"+e.id()+"|"+e.subjectType()+"|"+e.subjectId()+"|"+e.subjectKey()+"|"+e.action()+"|"+e.merchantId()+"|"+e.occurredAt();
    }
    private String sign(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.getJournalSigningSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Journal signing unavailable",ex); }
    }
    @Transactional
    public boolean replayOffline(JournalEntry entry,boolean dryRun) {
        requireKey();
        if (!settings.isRestorationQuarantine()) throw new IllegalStateException("Restore must remain quarantined");
        if (entry==null || !settings.getJournalOrigin().equals(entry.origin()) || entry.id()<1 || entry.subjectId()<1
                || entry.subjectKey()==null || entry.occurredAt()==null || entry.signature()==null
                || entry.subjectType()==null || !Set.of(AuditActorType.CUSTOMER,AuditActorType.STAFF).contains(entry.subjectType())
                || !MessageDigest.isEqual(sign(canonical(entry)).getBytes(StandardCharsets.US_ASCII),entry.signature().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid reconciliation journal signature or identity");
        }
        if (entry.action()==null || !Set.of("ERASE_ACCOUNT","ERASE_MERCHANT","RESTRICT_ACCOUNT","RESTRICT_MERCHANT","DISABLE_PUSH","REVOKE_ALL_SESSIONS").contains(entry.action())) {
            throw new IllegalArgumentException("Unsupported reconciliation action");
        }
        if ((entry.action().endsWith("MERCHANT"))!=(entry.merchantId()!=null)) throw new IllegalArgumentException("Journal scope mismatch");
        Subject subject=data.findSubject(entry.subjectType(),entry.subjectKey());
        if (!subject.id().equals(entry.subjectId())) throw new IllegalArgumentException("Restored subject identity mismatch");
        if (dryRun) return false;
        if (settings.isRestoreDryRun() || !settings.isDestructiveActionsApproved() || settings.getDestructiveApprovalRef().isBlank()) {
            throw new IllegalStateException("Explicit reviewed restore execution configuration required");
        }
        data.lockSubject(subject);
        if (!data.claimReplay(entry.origin(),entry.id(),OpaqueTokens.hash(canonical(entry)))) return false;
        if (entry.action().equals("REVOKE_ALL_SESSIONS")) data.invalidateSessions(subject);
        else if (entry.action().equals("DISABLE_PUSH")) {
            if (!data.pushUpdatedAfter(subject,entry.occurredAt())) data.disablePush(subject);
        } else {
            if (data.hasHold(subject)) throw new IllegalStateException("Resolve the restored preservation hold before replaying destructive effects");
            data.requireReplaceableStaff(subject);
            data.revokeAndRestrict(subject,entry.merchantId());
            if (entry.action().startsWith("ERASE")) data.erase(subject,entry.merchantId());
        }
        return true;
    }
}
