package com.app.carimbai.models.fidelity;

import com.app.carimbai.enums.CardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name="cards", schema="fidelity",
        uniqueConstraints = @UniqueConstraint(name="uq_cards_program_customer",
                columnNames = {"program_id","customer_id"}))
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="expires_at")
    private OffsetDateTime expiresAt;
    @Column(name="created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name="stamps_count", nullable=false)
    private Integer stampsCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private CardStatus status = CardStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="program_id", nullable=false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="customer_id", nullable=false)
    private Customer customer;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;


}
