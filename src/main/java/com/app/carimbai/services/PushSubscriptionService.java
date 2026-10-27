package com.app.carimbai.services;

import com.app.carimbai.models.PushSubscription;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import com.app.carimbai.security.PushEndpointPolicy;
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
    private final PushEndpointPolicy endpointPolicy;
    private final PrivacyNoticeService notices;
    private final com.app.carimbai.repositories.PrivacyDataRepository privacyData;

    @Transactional
    public PushSubscription subscribe(Long customerId, String endpoint, String p256dh, String auth) {
        endpointPolicy.validateDestination(endpoint);
        endpointPolicy.validateKeys(p256dh, auth);
        Customer current=customerRepo.findForUpdate(customerId).orElseThrow();
        if (!Boolean.TRUE.equals(current.getActive()) || !Boolean.TRUE.equals(current.getPushEnabled())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Choose optional loyalty notifications before subscribing");
        }
        notices.requirePublished(current.getPushNoticeVersion());
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
        if (!canSend(customerId)) return List.of();
        return repo.findByCustomerId(customerId);
    }

    @Transactional(readOnly=true)
    public boolean canSend(Long customerId) {
        return customerRepo.findById(customerId).filter(c->Boolean.TRUE.equals(c.getActive()) && Boolean.TRUE.equals(c.getPushEnabled()))
                .filter(c->notices.current().published() && notices.current().version().equals(c.getPushNoticeVersion())).isPresent();
    }

    @Transactional(readOnly=true)
    public com.app.carimbai.dtos.privacy.PrivacyDtos.PushState preferences(Long customerId) {
        Customer customer=customerRepo.findById(customerId).orElseThrow();
        return new com.app.carimbai.dtos.privacy.PrivacyDtos.PushState(Boolean.TRUE.equals(customer.getPushEnabled()),
                customer.getPushNoticeVersion(),customer.getPushUpdatedAt(),"LOYALTY_PUSH");
    }

    @Transactional
    public com.app.carimbai.dtos.privacy.PrivacyDtos.PushState choose(Long customerId,
            com.app.carimbai.dtos.privacy.PrivacyDtos.PushChoice choice) {
        Customer customer=customerRepo.findForUpdate(customerId).orElseThrow();
        var subject=new com.app.carimbai.security.PrivacyAccess.Subject(com.app.carimbai.enums.AuditActorType.CUSTOMER,
                customer.getId(),customer.getPrivacyKey());
        if (Boolean.TRUE.equals(choice.enabled())) {
            notices.requirePublished(choice.noticeVersion());
            customer.setPushEnabled(true);
            customer.setPushNoticeVersion(choice.noticeVersion());
            customer.setPushUpdatedAt(java.time.Instant.now());
            customerRepo.saveAndFlush(customer);
            privacyData.preference(subject,true,choice.noticeVersion());
        } else {
            customer.setPushEnabled(false);
            customer.setPushUpdatedAt(java.time.Instant.now());
            customerRepo.saveAndFlush(customer);
            privacyData.disablePush(subject);
            privacyData.effect(subject,null,"DISABLE_PUSH",null);
        }
        return preferences(customerId);
    }

    @Transactional
    public void delete(PushSubscription sub) {
        repo.delete(sub);
    }

    @Transactional
    public void unsubscribeDevice(Long customerId, String endpoint) {
        customerRepo.findForUpdate(customerId).orElseThrow();
        repo.findByCustomerIdAndEndpoint(customerId,endpoint).ifPresent(repo::delete);
    }
}
