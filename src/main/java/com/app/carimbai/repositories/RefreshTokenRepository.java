package com.app.carimbai.repositories;

import com.app.carimbai.models.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = {"staffUser", "merchant"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select t.staffUser.id from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<Long> findStaffIdByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    boolean existsByFamilyIdAndStaffUserIdAndMerchantIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID familyId, Long staffUserId, Long merchantId, OffsetDateTime now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, OffsetDateTime now);

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
