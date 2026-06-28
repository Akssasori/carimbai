package com.app.carimbai.facade;

import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.dtos.customer.SocialLoginRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditMask;
import com.app.carimbai.security.audit.AuditSecurityService;
import com.app.carimbai.services.CustomerService;
import com.app.carimbai.services.JwtService;
import com.app.carimbai.services.social.SocialTokenVerifierRegistry;
import com.app.carimbai.services.social.VerifiedSocialIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserRegistrationFacade {

    private final CustomerService customerService;
    private final SocialTokenVerifierRegistry verifierRegistry;
    private final JwtService jwtService;
    private final AuditSecurityService audit;

    @Transactional
    public Customer registerUser(CustomerLoginRequest request) {
        // login-light: NÃO é autenticação real → não emite token (FIX-02 / SEC-001).
        Customer c = customerService.loginOrRegister(request);
        audit.success(AuditEvent.CUSTOMER_LOGIN_OR_REGISTER, Map.of(
                "customerId", c.getId(),
                "email", AuditMask.email(request.email())));
        return c;
    }

    @Transactional
    public CustomerSession socialLogin(SocialLoginRequest request) {
        VerifiedSocialIdentity identity;
        try {
            identity = verifierRegistry.get(request.provider()).verify(request.token());
        } catch (RuntimeException ex) {
            audit.failure(AuditEvent.CUSTOMER_SOCIAL_LOGIN, Map.of(
                    "provider", request.provider().name(),
                    "reason", ex.getClass().getSimpleName()));
            throw ex;
        }
        Customer customer = customerService.socialLoginOrRegister(request.provider(), identity);
        // social-login é autenticação verificada no servidor → emite JWT de cliente.
        String token = jwtService.generateCustomerToken(customer);
        audit.success(AuditEvent.CUSTOMER_SOCIAL_LOGIN, Map.of(
                "customerId", customer.getId(),
                "provider", request.provider().name()));
        return new CustomerSession(customer, token);
    }
}
