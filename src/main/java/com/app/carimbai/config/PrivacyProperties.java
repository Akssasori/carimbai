package com.app.carimbai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.Map;

@Component @ConfigurationProperties(prefix="carimbai.privacy") @Getter @Setter
public class PrivacyProperties {
    private String controllerName="Carimbai - identificacao juridica pendente";
    private String controllerDocument="00.000.000/0000-00 (FICTICIO)";
    private String contactEmail="diniz3003@gmail.com";
    private String noticeVersion="draft-2026-10-26";
    private String noticeResource="privacy/notice-draft-2026-10-26.txt";
    private boolean noticeApproved;
    private String noticeApprovalRef="";
    private boolean destructiveActionsApproved;
    private String destructiveApprovalRef="";
    private boolean restorationQuarantine;
    private boolean collectNetworkMetadata;
    private String journalOrigin="";
    private String journalSigningSecret="";
    private String restoreJournalPath="";
    private boolean restoreDryRun=true;
    private long restoreExpectedEntries=-1;
    private long restoreExpectedLastId=-1;
    private boolean retentionEnabled;
    private boolean retentionDryRun=true;
    private String retentionApprovalRef="";
    private int retentionBatchSize=100;
    private Map<RetentionCategory,Integer> retentionDays=new EnumMap<>(RetentionCategory.class);
    public enum RetentionCategory {
        REFRESH_TOKENS, RESET_TOKENS, QR_TOKENS, IDEMPOTENCY_KEYS,
        AUDIT_NETWORK_METADATA, STAMP_NETWORK_METADATA, AUDIT_EVENTS,
        PUSH_SUBSCRIPTIONS, CLOSED_REQUEST_CONTENT
    }
}
