package com.app.carimbai.services;

import com.app.carimbai.dtos.RedeemRequest;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.CardRepository;
import com.app.carimbai.repositories.LocationRepository;
import com.app.carimbai.repositories.RewardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Resgate: o PIN deve ser exigido mesmo sem locationId (fecha o bypass do SEC-034
 * / FIX-23). Unitário, sem DB — mocka repositórios e monta o SecurityContext.
 */
class RedeemServiceTest {

    private CardRepository cardRepo;
    private RewardRepository rewardRepo;
    private LocationRepository locationRepo;
    private StaffService staffService;
    private StampTokenService stampTokenService;
    private RedeemService service;
    private StaffUser staff;

    @BeforeEach
    void setUp() {
        cardRepo = mock(CardRepository.class);
        rewardRepo = mock(RewardRepository.class);
        locationRepo = mock(LocationRepository.class);
        staffService = mock(StaffService.class);
        stampTokenService = mock(StampTokenService.class);
        service = new RedeemService(cardRepo, rewardRepo, locationRepo, staffService,
                new ObjectMapper(), stampTokenService);

        staff = StaffUser.builder().id(10L).email("c@x.com").active(true).build();
        var auth = new UsernamePasswordAuthenticationToken(
                staff,
                Map.of("merchantId", 1L, "role", "CASHIER"),
                List.of(new SimpleGrantedAuthority("CASHIER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void redeem_withoutLocationId_withoutPin_isRejected() {
        var req = new RedeemRequest(5L, null, null); // sem locationId e sem PIN

        assertThatThrownBy(() -> service.redeem(req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PIN");

        verifyNoInteractions(cardRepo); // nem chegou a buscar o cartão
    }

    @Test
    void redeem_withoutLocationId_withPin_validatesPin() {
        var req = new RedeemRequest(5L, null, null);
        when(staffService.validateCashierPin(eq(10L), eq("1234"))).thenReturn(staff);
        when(cardRepo.findById(5L)).thenReturn(Optional.empty()); // short-circuit após o PIN

        assertThatThrownBy(() -> service.redeem(req, "1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Card not found");

        verify(staffService).validateCashierPin(10L, "1234"); // PIN exigido mesmo sem location
    }
}
