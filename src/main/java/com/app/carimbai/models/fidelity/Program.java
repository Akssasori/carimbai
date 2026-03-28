package com.app.carimbai.models.fidelity;

import com.app.carimbai.models.core.Merchant;
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
@Table(name="programs", schema="fidelity")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=120)
    private String name;

    @Builder.Default
    @Column(name="rule_total_stamps", nullable=false)
    private Integer ruleTotalStamps = 10;

    @Column(name="reward_name", nullable=false, length=120)
    private String rewardName;

    @Column(name="expiration_days")
    private Integer expirationDays;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(nullable=false)
    private Boolean active = true;

    @Column(name="start_at")
    private OffsetDateTime startAt;

    @Column(name="end_at")
    private OffsetDateTime endAt;

    @Column(length=50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name="image_url", length=500)
    private String imageUrl;

    @Builder.Default
    @Column(name="sort_order", nullable=false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="merchant_id", nullable=false)
    private Merchant merchant;
}
