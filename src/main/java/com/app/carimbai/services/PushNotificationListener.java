package com.app.carimbai.services;

import com.app.carimbai.enums.AuditAction;
import com.app.carimbai.enums.AuditActorType;
import com.app.carimbai.events.StampAppliedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Reage a StampAppliedEvent disparando push para o cliente em dois momentos:
 *  1. Falta N carimbos para fechar (N configuravel via remind-at-remaining; default 1)
 *  2. Cartao cheio (stamps_count == needed)
 *
 * @TransactionalEventListener(AFTER_COMMIT) garante que so dispara se a transacao
 * de StampsService.applyStamp commitou — nunca para um stamp que rolou rollback.
 * O envio em si e @Async no PushNotificationService.
 */
@Component
@RequiredArgsConstructor
public class PushNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationListener.class);

    private final PushNotificationService pushService;
    private final AuditService auditService;

    @Value("${carimbai.notification.remind-at-remaining:1}")
    private int remindAtRemaining;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStampApplied(StampAppliedEvent event) {
        int remaining = event.stampsNeeded() - event.stampsCount();

        if (remaining == remindAtRemaining && remaining > 0) {
            triggerPush(event, "REMIND");
        } else if (event.stampsCount() >= event.stampsNeeded()) {
            triggerPush(event, "CARD_FULL");
        }
    }

    private void triggerPush(StampAppliedEvent event, String kind) {
        log.info("Disparando push customer={} kind={}", event.customerId(), kind);

        // No merchant/program names, progress or reward details on shared lock screens.
        pushService.sendToCustomer(event.customerId(), "Carimbai", "Ha uma atualizacao no seu cartao. Abra o aplicativo para consultar.");

        auditService.log(AuditService.AuditEntry.builder()
                .action(AuditAction.PUSH_NOTIFICATION_TRIGGERED)
                .actorType(AuditActorType.SYSTEM)
                .entityType("Card")
                .entityId(event.cardId())
                .merchantId(event.merchantId())
                .details(Map.of(
                        "customerId", event.customerId(),
                        "kind", kind
                ))
                .build());
    }
}
