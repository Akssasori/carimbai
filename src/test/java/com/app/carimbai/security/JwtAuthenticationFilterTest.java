package com.app.carimbai.security;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.repositories.RefreshTokenRepository;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.execption.InvalidCredentialsException;
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
    private TokenRevocationService revocation;
    private JwtAuthenticationFilter filter;
    private StaffAccessService staffAccess;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        staffRepo = mock(StaffUserRepository.class);
        customerRepo = mock(CustomerRepository.class);
        revocation = mock(TokenRevocationService.class);
        staffAccess = mock(StaffAccessService.class);
        filter = new JwtAuthenticationFilter(jwt, staffAccess, customerRepo, revocation, mock(RefreshTokenRepository.class));
        when(jwt.isExpired("tok")).thenReturn(false);
        when(jwt.extractJti("tok")).thenReturn("jti-1");
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
        when(staffAccess.requireActiveLink(user.getId(), 1L)).thenReturn(StaffUserMerchant.builder()
                .staffUser(user).merchant(Merchant.builder().id(1L).active(true).build())
                .role(StaffRole.ADMIN).active(true).build());
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
    void revokedToken_isNotAuthenticated() throws Exception {
        // FIX-11 / SEC-012: depois de revogar, mesmo token válido não autentica.
        when(revocation.isRevoked("jti-1")).thenReturn(true);
        stubStaff(StaffUser.builder().id(1L).email("p@x.com").active(true).platformAdmin(false).build());

        runFilter();

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verifyNoInteractions(staffRepo); // nem chegou a buscar o usuário
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

    @Test
    void removedMerchantLinkRejectsAnOtherwiseValidToken() throws Exception {
        when(jwt.extractStaffId("tok")).thenReturn(1L);
        when(jwt.extractMerchantId("tok")).thenReturn(1L);
        when(staffAccess.requireActiveLink(1L, 1L)).thenThrow(new InvalidCredentialsException());
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void passwordResetInvalidatesPreviouslyIssuedToken() throws Exception {
        stubStaff(StaffUser.builder().id(1L).tokenVersion(2L).active(true).build());
        when(jwt.extractTokenVersion("tok")).thenReturn(1L);
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void derivesRoleFromCurrentLinkRatherThanJwt() throws Exception {
        StaffUser staff = StaffUser.builder().id(1L).active(true).build();
        stubStaff(staff);
        when(staffAccess.requireActiveLink(1L, 1L)).thenReturn(StaffUserMerchant.builder()
                .staffUser(staff).merchant(Merchant.builder().id(1L).active(true).build())
                .role(StaffRole.CASHIER).active(true).build());
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("CASHIER");
    }
}
