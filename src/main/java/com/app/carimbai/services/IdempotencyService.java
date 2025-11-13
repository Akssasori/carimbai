package com.app.carimbai.services;

import com.app.carimbai.models.IdempotencyKey;
import com.app.carimbai.repositories.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repo;

    public IdempotencyService(IdempotencyKeyRepository repo) { this.repo = repo; }

    @Transactional
    public void acquireOrThrow(String key) {
        try {
            var entity = new IdempotencyKey();
            entity.setKey(key);
            repo.save(entity);
        } catch (DataIntegrityViolationException dup) {
            // unique violation => já processado
            throw new IllegalStateException("Duplicate Idempotency-Key");
        }
    }
}
