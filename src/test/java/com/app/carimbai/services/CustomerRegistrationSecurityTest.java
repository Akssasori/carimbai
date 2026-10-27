package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CounterCustomerRequest;
import com.app.carimbai.enums.SocialProvider;
import com.app.carimbai.execption.EmailAlreadyLinkedException;
import com.app.carimbai.mappers.CustomerMapper;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.services.social.VerifiedSocialIdentity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerRegistrationSecurityTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(repository, Mappers.getMapper(CustomerMapper.class));

    @Test void providerCannotOverwriteACorrectionMadeByTheOwner() {
        Customer existing=Customer.builder().id(1L).providerId("google:subject").name("Corrected name")
                .profileUpdatedAt(java.time.Instant.now()).build();
        when(repository.findByProviderId("google:subject")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        Customer result=service.socialLoginOrRegister(SocialProvider.GOOGLE,
                new VerifiedSocialIdentity("subject","test@example.test","Old provider name",true));
        assertThat(result.getName()).isEqualTo("Corrected name");
    }

    @Test void erasedOrRestrictedCustomerCannotReturnViaExistingSocialLink() {
        Customer existing=Customer.builder().id(1L).providerId("google:subject").active(false).build();
        when(repository.findByProviderId("google:subject")).thenReturn(Optional.of(existing));
        assertThatThrownBy(()->service.socialLoginOrRegister(SocialProvider.GOOGLE,
                new VerifiedSocialIdentity("subject","test@example.test","Name",true))).isInstanceOf(com.app.carimbai.execption.InvalidCredentialsException.class);
    }

    @Test
    void counterCannotRetrieveOrOverwriteAnExistingCustomer() {
        when(repository.existsByEmail("existing@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.registerAtCounter(new CounterCustomerRequest("Changed", "existing@example.com", "1")))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).findByEmail(any());
        verify(repository, never()).findByProviderId(any());
    }

    @Test
    void counterOnlyCreatesUnlinkedCustomers() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        Customer customer = service.registerAtCounter(new CounterCustomerRequest("New", "new@example.com", null));
        assertThat(customer.getProviderId()).isNull();
    }

    @Test
    void evenPlatformCreationCannotAssignAnUnverifiedProviderId() {
        assertThatThrownBy(() -> service.createCustomer(new CreateCustomerRequest("a@example.com", null, "google:forged")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void unverifiedEmailCannotClaimAnExistingAccount() {
        Customer existing = Customer.builder().id(1L).email("a@example.com").build();
        when(repository.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.socialLoginOrRegister(SocialProvider.GOOGLE,
                new VerifiedSocialIdentity("subject", existing.getEmail(), "Name", false)))
                .isInstanceOf(EmailAlreadyLinkedException.class);
        assertThat(existing.getProviderId()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void verifiedEmailCanClaimOnlyAnUnlinkedAccount() {
        Customer existing = Customer.builder().id(1L).email("a@example.com").build();
        when(repository.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        Customer claimed = service.socialLoginOrRegister(SocialProvider.GOOGLE,
                new VerifiedSocialIdentity("subject", existing.getEmail(), "Name", true));
        assertThat(claimed.getProviderId()).isEqualTo("google:subject");
    }
}
