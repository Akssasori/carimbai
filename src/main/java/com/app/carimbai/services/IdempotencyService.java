package com.app.carimbai.services;

import com.app.carimbai.models.IdempotencyKey;
import com.app.carimbai.repositories.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repo;

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
