package com.app.carimbai.repositories;

import com.app.carimbai.models.StampToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StampTokenRepository extends JpaRepository<StampToken, Long> {
    boolean existsByNonce(UUID nonce);
    Optional<StampToken> findByNonce(UUID nonce);
}
