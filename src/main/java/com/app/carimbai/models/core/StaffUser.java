package com.app.carimbai.models.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "staff_users", schema = "core")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=160)
    private String email;

    @Column(name="password_hash", nullable=false)
    private String passwordHash;

    @Builder.Default
    @Column(nullable=false)
    private Boolean active = true;

    @Column(name="pin_hash")
    private String pinHash;

    @OneToMany(mappedBy = "staffUser", fetch = FetchType.LAZY)
    @Builder.Default
    private List<StaffUserMerchant> merchantLinks = new ArrayList<>();
}
