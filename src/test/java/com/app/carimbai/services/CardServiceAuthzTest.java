package com.app.carimbai.services;

import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.security.audit.AuditSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Escopo de dono dos recursos do cliente (FIX-02 Fase C / SEC-001): um cliente não
 * lê cartões/QRs de outro; enroll é escopado ao merchant do staff. Unitário, sem DB.
 */
class CardServiceAuthzTest {

    private CardRepository cardRepo;
    private ProgramService programService;
    private CustomerService customerService;
    private StampTokenService stampTokenService;
    private CardService service;

    @BeforeEach
    void setUp() {
        cardRepo = mock(CardRepository.class);
        programService = mock(ProgramService.class);
        customerService = mock(CustomerService.class);
        stampTokenService = mock(StampTokenService.class);
        service = new CardService(cardRepo, programService, customerService, stampTokenService, mock(AuditSecurityService.class));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setCustomer(Long id) {
        var auth = new UsernamePasswordAuthenticationToken(
                Customer.builder().id(id).build(),
                Map.of("customerId", id, "type", "CUSTOMER"),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setStaffMerchant(Long merchantId) {
        var auth = new UsernamePasswordAuthenticationToken(
                StaffUser.builder().id(1L).build(),
                Map.of("merchantId", merchantId, "role", "CASHIER"),
                List.of(new SimpleGrantedAuthority("CASHIER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void getCustomerCards_otherCustomer_denied() {
        setCustomer(10L);
        assertThatThrownBy(() -> service.getCustomerCards(99L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cardRepo);
    }

    @Test
    void getCustomerCards_ownId_ok() {
        setCustomer(10L);
        when(cardRepo.findByCustomerIdWithProgram(10L)).thenReturn(List.of());
        assertThat(service.getCustomerCards(10L).cards()).isEmpty();
    }

    @Test
    void generateCustomerQr_otherCustomersCard_denied() {
        setCustomer(10L);
        Card card = Card.builder().id(1L).customer(Customer.builder().id(5L).build()).build();
        when(cardRepo.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.generateCustomerQr(1L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(stampTokenService);
    }

    @Test
    void generateRedeemQr_otherCustomersCard_denied() {
        setCustomer(10L);
        Card card = Card.builder().id(1L).customer(Customer.builder().id(5L).build()).build();
        when(cardRepo.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.generateRedeemQr(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void enroll_crossMerchant_denied() {
        setStaffMerchant(1L); // staff do merchant 1
        Program p = Program.builder().id(5L).merchant(Merchant.builder().id(2L).build()).build();
        when(programService.findById(5L)).thenReturn(p);

        assertThatThrownBy(() -> service.getOrCreateCard(5L, 7L)) // programa do merchant 2
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customerService);
    }
}
