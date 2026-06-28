package com.app.carimbai.services;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.execption.InvalidCredentialsException;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.repositories.StaffUserMerchantRepository;
import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.audit.AuditSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX-08 / SEC-008 — anti-enumeração no /auth/login: e-mail inexistente, senha
 * incorreta, usuário inativo e sem vínculo a merchant produzem a MESMA
 * {@link InvalidCredentialsException} (→ 401 + body uniforme no handler).
 * Adicionalmente, bcrypt é avaliado SEMPRE — inclusive quando o e-mail não
 * existe — para nivelar o tempo de resposta (defesa contra timing attack).
 */
class AuthServiceLoginTest {

    private StaffUserRepository staffRepo;
    private StaffUserMerchantRepository linkRepo;
    private BCryptPasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        staffRepo = mock(StaffUserRepository.class);
        linkRepo = mock(StaffUserMerchantRepository.class);
        encoder = mock(BCryptPasswordEncoder.class);
        when(encoder.encode("never-matches-anything")).thenReturn("$2a$12$dummy");
        service = new AuthService(staffRepo, linkRepo, encoder, mock(JwtService.class), mock(AuditSecurityService.class));
        service.init();
    }

    @Test
    void emailNotFound_throwsInvalidCredentials_andStillRunsBcrypt() {
        when(staffRepo.findByEmail("ghost@x.com")).thenReturn(Optional.empty());
        when(encoder.matches("anything", "$2a$12$dummy")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@x.com", "anything", null)))
                .isInstanceOf(InvalidCredentialsException.class);

        // bcrypt foi chamado mesmo sem user (anti-timing — FIX-08).
        verify(encoder).matches("anything", "$2a$12$dummy");
    }

    @Test
    void badPassword_throwsInvalidCredentials() {
        var user = StaffUser.builder().id(7L).passwordHash("REAL").active(true).build();
        when(staffRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "REAL")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void inactiveUser_throwsInvalidCredentials_sameAsBadPassword() {
        var user = StaffUser.builder().id(7L).passwordHash("REAL").active(false).build();
        when(staffRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(encoder.matches("right", "REAL")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "right", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void noActiveMerchantLink_throwsInvalidCredentials() {
        var user = StaffUser.builder().id(7L).passwordHash("REAL").active(true).build();
        when(staffRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(encoder.matches("right", "REAL")).thenReturn(true);
        when(linkRepo.findByStaffUserIdAndActiveTrue(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "right", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void bcryptCalledOnce_perLogin_evenWhenNotFound() {
        // Defesa: nada mais de bcrypt além do esperado (evita oráculo de duas vias).
        when(staffRepo.findByEmail("ghost@x.com")).thenReturn(Optional.empty());
        when(encoder.matches("x", "$2a$12$dummy")).thenReturn(false);
        try { service.login(new LoginRequest("ghost@x.com", "x", null)); } catch (RuntimeException ignored) {}
        verify(encoder, times(1)).matches("x", "$2a$12$dummy");
    }

    @Test
    void successfulLogin_returnsResponse() {
        var merchant = Merchant.builder().id(3L).name("M").build();
        var user = StaffUser.builder().id(7L).email("a@b.com").passwordHash("REAL").active(true).build();
        var link = StaffUserMerchant.builder().staffUser(user).merchant(merchant).role(StaffRole.ADMIN)
                .active(true).isDefault(true).build();
        when(staffRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(encoder.matches("right", "REAL")).thenReturn(true);
        when(linkRepo.findByStaffUserIdAndActiveTrue(7L)).thenReturn(List.of(link));

        var resp = service.login(new LoginRequest("a@b.com", "right", null));
        assertThat(resp).isNotNull();
        assertThat(resp.merchantId()).isEqualTo(3L);
        assertThat(resp.role()).isEqualTo("ADMIN");
    }
}
