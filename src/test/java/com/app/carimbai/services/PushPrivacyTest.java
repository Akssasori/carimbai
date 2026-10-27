package com.app.carimbai.services;

import com.app.carimbai.events.StampAppliedEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PushPrivacyTest {
    @Test void lockscreenMessageDoesNotExposeMerchantProgressOrRewards() {
        var push=mock(PushNotificationService.class);
        var listener=new PushNotificationListener(push,mock(AuditService.class));
        listener.onStampApplied(new StampAppliedEvent(1L,1L,1L,1L,"Private store","Private program",10,10));
        verify(push).sendToCustomer(1L,"Carimbai","Ha uma atualizacao no seu cartao. Abra o aplicativo para consultar.");
        verifyNoMoreInteractions(push);
    }
}
