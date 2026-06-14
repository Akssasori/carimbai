package com.app.carimbai.security;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.services.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifica a autenticação no filtro: autoridade PLATFORM_ADMIN (SEC-020) e o
 * tratamento type-aware do token de cliente (FIX-02, Fase A). Unitário, sem DB.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwt;
    private StaffUserRepository staffRepo;
    private CustomerRepository customerRepo;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        staffRepo = mock(StaffUserRepository.class);
        customerRepo = mock(CustomerRepository.class);
        filter = new JwtAuthenticationFilter(jwt, staffRepo, customerRepo);
        when(jwt.isExpired("tok")).thenReturn(false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest reqWithToken() {
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        return req;
    }

    private void runFilter() throws Exception {
        filter.doFilter(reqWithToken(), new MockHttpServletResponse(), new MockFilterChain());
    }

    private void stubStaff(StaffUser user) {
        when(jwt.extractType("tok")).thenReturn(null); // token de staff (legado, sem type)
        when(jwt.extractStaffId("tok")).thenReturn(user.getId());
        when(jwt.extractRole("tok")).thenReturn("ADMIN");
        when(jwt.extractMerchantId("tok")).thenReturn(1L);
        when(staffRepo.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void platformAdmin_getsPlatformAdminAuthority() throws Exception {
        stubStaff(StaffUser.builder().id(1L).email("p@x.com").active(true).platformAdmin(true).build());

        runFilter();

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("PLATFORM_ADMIN"));
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }

    @Test
    void regularAdmin_hasNoPlatformAdminAuthority() throws Exception {
        stubStaff(StaffUser.builder().id(2L).email("a@x.com").active(true).platformAdmin(false).build());

        runFilter();

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).noneMatch(a -> a.getAuthority().equals("PLATFORM_ADMIN"));
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }

    @Test
    void customerToken_authenticatesAsCustomer_notStaff() throws Exception {
        when(jwt.extractType("tok")).thenReturn("CUSTOMER");
        when(jwt.extractCustomerId("tok")).thenReturn(77L);
        when(customerRepo.findById(77L)).thenReturn(Optional.of(
                Customer.builder().id(77L).email("c@x.com").build()));

        runFilter();

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isInstanceOf(Customer.class);
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
        verifyNoInteractions(staffRepo); // token de cliente não é tratado como staff
    }
}
