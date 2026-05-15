package com.app.carimbai.repositories;

import com.app.carimbai.models.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoga todos os refresh tokens nao expirados de um staff. Usado no logout
     * "de todas as sessoes" e no caso de seguranca (reuso de token antigo).
     */
    @Modifying
    @Query("""
        update RefreshToken rt
           set rt.revokedAt = :now
         where rt.staffUser.id = :staffUserId
           and rt.revokedAt is null
    """)
    int revokeAllForStaff(Long staffUserId, OffsetDateTime now);
}
