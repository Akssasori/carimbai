package com.app.carimbai.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="stamp_tokens", schema="ops")
public class StampToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=20)
    private String type; // CUSTOMER_QR | STORE_QR
    @Column(name="id_ref", nullable=false)
    private Long idRef; // cardId (A) ou locationId (B)

    @Column(nullable=false, unique=true)
    private UUID nonce;
    @Column(name="exp_at", nullable=false)
    private OffsetDateTime expAt;
    @Column(name="used_at")
    private OffsetDateTime usedAt;

    @Column(nullable=false)
    private String signature;
}
