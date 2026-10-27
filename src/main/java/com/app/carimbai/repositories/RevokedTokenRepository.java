package com.app.carimbai.repositories;

import com.app.carimbai.models.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    @Modifying
    @Query(value = "INSERT INTO ops.revoked_tokens (jti, expires_at) VALUES (:jti, :expiry) "
            + "ON CONFLICT (jti) DO NOTHING", nativeQuery = true)
    int revokeIfAbsent(@Param("jti") String jti, @Param("expiry") Instant expiry);

    boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);
    long deleteByExpiresAtBefore(Instant now);
}
