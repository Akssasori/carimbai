package com.app.carimbai.models.fidelity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name="customers", schema="fidelity")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length=160)
    private String email;
    @Column(length=30)
    private String phone;
    @Column(name="provider_id", length=80)
    private String providerId;
    @Column(length = 120)
    private String name;

    @Builder.Default @Column(nullable=false)
    private Boolean active=true;
    @Builder.Default @Column(name="token_version",nullable=false)
    private Long tokenVersion=0L;
    @Builder.Default @Column(name="privacy_key",nullable=false)
    private java.util.UUID privacyKey=java.util.UUID.randomUUID();
    @Column(name="profile_updated_at") private java.time.Instant profileUpdatedAt;
    @Builder.Default @Column(name="push_enabled",nullable=false) private Boolean pushEnabled=false;
    @Column(name="push_notice_version") private String pushNoticeVersion;
    @Column(name="push_updated_at") private java.time.Instant pushUpdatedAt;
}
