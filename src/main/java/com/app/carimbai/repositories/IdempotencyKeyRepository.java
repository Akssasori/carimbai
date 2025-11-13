package com.app.carimbai.repositories;

import com.app.carimbai.models.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    boolean existsByKey(String key);
}
