package com.app.carimbai.models.fidelity;

import com.app.carimbai.models.core.Location;
import com.app.carimbai.models.core.StaffUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name="rewards", schema="fidelity")
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="issued_at", nullable=false)
    private OffsetDateTime issuedAt = OffsetDateTime.now();

    @Column(name="redeemed_at")
    private OffsetDateTime redeemedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="card_id", nullable=false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="cashier_id")
    private StaffUser cashier;
}
