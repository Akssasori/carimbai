package com.app.carimbai.facade;

import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.dtos.customer.SocialLoginRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.services.CustomerService;
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

    @Transactional
    public Customer registerUser(CustomerLoginRequest request) {
        return customerService.loginOrRegister(request);
    }

    @Transactional
    public Customer socialLogin(SocialLoginRequest request) {
        VerifiedSocialIdentity identity = verifierRegistry.get(request.provider()).verify(request.token());
        return customerService.socialLoginOrRegister(request.provider(), identity);
    }
}
