package com.app.carimbai.dtos.privacy;

import com.app.carimbai.enums.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PrivacyDtos {
    private PrivacyDtos() {}
    public record Profile(String subjectType,Long id,String name,String email,String phone,
                          boolean privacyOfficer) {}
    public record UpdateProfile(@NotBlank @Size(max=120) String name,@Size(max=30) String phone) {
        @JsonAnySetter public void rejectUnknown(String key,Object value) {
            throw new IllegalArgumentException("Only name and phone can be corrected here");
        }
    }
    public record OpenRequest(@NotNull PrivacyRequestType type,@NotNull PrivacyScope scope,
                              @Positive Long merchantId,@Size(max=1000) String details) {}
    public record RequestView(UUID id,PrivacyRequestType type,PrivacyScope scope,Long merchantId,
                              PrivacyRequestStatus status,String details,String response,
                              Instant createdAt,Instant updatedAt,Instant dueAt,Instant completedAt) {}
    public record Review(@NotNull PrivacyAction action,@Size(max=2000) String response,
                         @Pattern(regexp="[A-Za-z0-9._:/-]{1,200}") String evidenceRef,
                         Instant dueAt,@Size(max=60) String confirmation) {}
    public record Hold(@NotNull AuditActorType subjectType,@NotNull UUID subjectKey,
                       @NotBlank @Pattern(regexp="[A-Z_]{1,60}") String reasonCode,
                       @NotBlank @Pattern(regexp="[A-Za-z0-9._:/-]{1,200}") String evidenceRef,
                       @NotNull @Future Instant reviewAt) {}
    public record ExportPage(String section,List<Map<String,Object>> items,Long nextAfterId,
                             List<String> redactions) {}
    public record PushChoice(@NotNull Boolean enabled,@Size(max=80) String noticeVersion) {}
    public record PushDevice(@NotBlank @Size(max=2048) String endpoint) {}
    public record PushState(boolean enabled,String noticeVersion,Instant updatedAt,String purpose) {}
    public record ReleaseHold(@NotBlank @Pattern(regexp="[A-Za-z0-9._:/-]{1,200}") String evidenceRef) {}
    public record JournalEntry(String origin,long id,AuditActorType subjectType,long subjectId,UUID subjectKey,
                               String action,Long merchantId,Instant occurredAt,String signature) {}
    public record Notice(boolean published,String version,String controllerName,String controllerDocument,
                         String contactEmail,String text,List<String> pendingDecisions) {}
}
