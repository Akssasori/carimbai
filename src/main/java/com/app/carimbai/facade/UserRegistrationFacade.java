package com.app.carimbai.facade;

import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.dtos.customer.SocialLoginRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.services.CustomerService;
import com.app.carimbai.services.JwtService;
import com.app.carimbai.services.social.SocialTokenVerifierRegistry;
import com.app.carimbai.services.social.VerifiedSocialIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationFacade {

    private final CustomerService customerService;
    private final SocialTokenVerifierRegistry verifierRegistry;
    private final JwtService jwtService;

    @Transactional
    public Customer registerUser(CustomerLoginRequest request) {
        // login-light: NÃO é autenticação real → não emite token (FIX-02 / SEC-001).
        return customerService.loginOrRegister(request);
    }

    @Transactional
    public CustomerSession socialLogin(SocialLoginRequest request) {
        VerifiedSocialIdentity identity = verifierRegistry.get(request.provider()).verify(request.token());
        Customer customer = customerService.socialLoginOrRegister(request.provider(), identity);
        // social-login é autenticação verificada no servidor → emite JWT de cliente.
        String token = jwtService.generateCustomerToken(customer);
        return new CustomerSession(customer, token);
    }
}
