package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.repositories.ProgramRepository;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.PinLockoutService;
import com.app.carimbai.security.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Isolamento multi-tenant nos endpoints de gestão (SEC-020 / FIX-03): um ADMIN do
 * merchant A não pode operar sobre o merchant B. Unitários, sem Spring/DB —
 * provam que a checagem ocorre ANTES de qualquer acesso a repositório.
 */
class MerchantScopeAuthzTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setActiveMerchant(Long merchantId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "principal",
                Map.of("merchantId", merchantId, "role", "ADMIN"),
                List.of(new SimpleGrantedAuthority("ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void createProgram_crossTenant_denied_andRepoUntouched() {
        ProgramRepository programRepo = mock(ProgramRepository.class);
        MerchantService merchantService = mock(MerchantService.class);
        ProgramService service = new ProgramService(programRepo, merchantService,
                mock(com.app.carimbai.services.AuditService.class));

        setActiveMerchant(1L); // ADMIN do merchant 1
        var req = new CreateProgramRequest("P", 10, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createProgram(2L, req)) // tenta no merchant 2
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(merchantService);
        verify(programRepo, never()).save(any());
    }

    @Test
    void createStaffUser_crossTenant_denied_andReposUntouched() {
        StaffUserRepository staffRepo = mock(StaffUserRepository.class);
        StaffUserMerchantRepository linkRepo = mock(StaffUserMerchantRepository.class);
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        MerchantService merchantService = mock(MerchantService.class);
        StaffService service = new StaffService(staffRepo, linkRepo, encoder, merchantService,
                mock(PinLockoutService.class), mock(AuditService.class));

        setActiveMerchant(1L);
        var req = new CreateStaffUserRequest(2L, "novo@x.com", "senhaforte123", StaffRole.CASHIER);

        assertThatThrownBy(() -> service.createStaffUser(req))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(merchantService, staffRepo, linkRepo);
    }
}
