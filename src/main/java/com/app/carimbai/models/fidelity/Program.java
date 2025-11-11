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
    @Column(name="rule_total_stamps", nullable=false)
    private Integer ruleTotalStamps = 10;
    @Column(name="reward_name", nullable=false, length=120)
    private String rewardName;
    @Column(name="expiration_days")
    private Integer expirationDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="merchant_id", nullable=false)
    private Merchant merchant;
}
