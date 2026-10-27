package com.app.carimbai.models;

import com.app.carimbai.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="privacy_requests",schema="ops")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PrivacyRequest {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING) @Column(name="subject_type",nullable=false) private AuditActorType subjectType;
    @Column(name="subject_id",nullable=false) private Long subjectId;
    @Column(name="subject_key",nullable=false) private UUID subjectKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private PrivacyRequestType type;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private PrivacyScope scope;
    @Column(name="merchant_id") private Long merchantId;
    @Builder.Default @Enumerated(EnumType.STRING) @Column(nullable=false)
    private PrivacyRequestStatus status=PrivacyRequestStatus.RECEIVED;
    @Column(length=1000) private String details;
    @Column(name="assigned_staff_id") private Long assignedStaffId;
    @Column(length=2000) private String response;
    @Column(name="decision_ref",length=200) private String decisionRef;
    @Builder.Default @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Builder.Default @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    @Column(name="due_at") private Instant dueAt;
    @Column(name="completed_at") private Instant completedAt;
    @Version @Builder.Default private Long version=0L;
}
