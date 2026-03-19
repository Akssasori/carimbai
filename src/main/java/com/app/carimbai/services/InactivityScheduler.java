package com.app.carimbai.services;

import com.app.carimbai.repositories.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InactivityScheduler {

    private final CardRepository cardRepo;
    private final PushNotificationService pushService;

    @Scheduled(cron = "0 0 10 * * *")
    public void notifyInactiveCustomers() {
        OffsetDateTime since = OffsetDateTime.now().minusDays(15);
        List<Long> customerIds = cardRepo.findInactiveCustomerIds(since);
        log.info("Clientes inativos (15d+): {}", customerIds.size());

        for (Long customerId : customerIds) {
            pushService.sendToCustomer(
                    customerId,
                    "Sentimos sua falta!",
                    "Volte e continue acumulando carimbos para ganhar sua recompensa!"
            );
        }
    }
}
