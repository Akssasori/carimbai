package com.app.carimbai.services;

import com.app.carimbai.models.PushSubscription;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CRUD basico de push_subscriptions. Subscribe e idempotente: se ja existe
 * row para (customer, endpoint), atualiza last_used_at em vez de duplicar.
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repo;
    private final CustomerRepository customerRepo;

    @Transactional
    public PushSubscription subscribe(Long customerId, String endpoint, String p256dh, String auth) {
        // Idempotencia: se ja existe (customer, endpoint), so atualiza last_used_at + keys.
        return repo.findByCustomerIdAndEndpoint(customerId, endpoint)
                .map(existing -> {
                    existing.setP256dh(p256dh);
                    existing.setAuth(auth);
                    existing.setLastUsedAt(OffsetDateTime.now());
                    return repo.save(existing);
                })
                .orElseGet(() -> {
                    Customer customer = customerRepo.findById(customerId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Customer not found: " + customerId));
                    PushSubscription sub = PushSubscription.builder()
                            .customer(customer)
                            .endpoint(endpoint)
                            .p256dh(p256dh)
                            .auth(auth)
                            .createdAt(OffsetDateTime.now())
                            .lastUsedAt(OffsetDateTime.now())
                            .build();
                    return repo.save(sub);
                });
    }

    @Transactional(readOnly = true)
    public List<PushSubscription> listByCustomer(Long customerId) {
        return repo.findByCustomerId(customerId);
    }

    @Transactional
    public void delete(PushSubscription sub) {
        repo.delete(sub);
    }
}
