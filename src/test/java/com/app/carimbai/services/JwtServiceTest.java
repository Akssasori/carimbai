package com.app.carimbai.services;

import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.Merchant;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.models.fidelity.Customer;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de segurança (unitários, sem contexto Spring/DB) do core de JWT.
 * Verificam controles positivos confirmados na Fase 5: emissão/leitura de claims,
 * detecção de expiração, rejeição de assinatura adulterada e de token `alg=none`.
 * Guardam contra regressão — NÃO dependem dos achados em aberto.
 */
class JwtServiceTest {

    private JwtService newService(long expirationSeconds) {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "secret", "test-secret-0123456789-0123456789-abcdef");
        ReflectionTestUtils.setField(s, "expirationSeconds", expirationSeconds);
        ReflectionTestUtils.setField(s, "customerExpirationSeconds", 3600L);
        s.init();
        return s;
    }

    private StaffUserMerchant link(long staffId, long merchantId, StaffRole role) {
        StaffUser u = StaffUser.builder().id(staffId).email("a@b.com").build();
        Merchant m = Merchant.builder().id(merchantId).name("M").build();
        return StaffUserMerchant.builder().staffUser(u).merchant(m).role(role).build();
    }

    @Test
    void generatesAndParsesClaims() {
        JwtService s = newService(3600L);
        StaffUserMerchant l = link(7L, 3L, StaffRole.ADMIN);

        String token = s.generateToken(l.getStaffUser(), l);

        assertThat(s.extractStaffId(token)).isEqualTo(7L);
        assertThat(s.extractRole(token)).isEqualTo("ADMIN");
        assertThat(s.extractMerchantId(token)).isEqualTo(3L);
        assertThat(s.isExpired(token)).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService s = newService(-10L); // emitido já expirado
        StaffUserMerchant l = link(1L, 1L, StaffRole.CASHIER);
        String token = s.generateToken(l.getStaffUser(), l);

        // jjwt lança ExpiredJwtException (subtipo de JwtException) ao parsear um
        // token expirado — comportamento *fail-closed*: o filtro captura
        // JwtException e trata como não-autenticado. (Nota: por isso isExpired()
        // lança em vez de retornar true para tokens já expirados.)
        assertThatThrownBy(() -> s.isExpired(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedSignature() {
        JwtService s = newService(3600L);
        StaffUserMerchant l = link(1L, 1L, StaffRole.ADMIN);
        String token = s.generateToken(l.getStaffUser(), l);

        int dot = token.lastIndexOf('.');
        String sig = token.substring(dot + 1);
        char first = sig.charAt(0);
        char replaced = (first == 'a') ? 'b' : 'a';
        String tampered = token.substring(0, dot + 1) + replaced + sig.substring(1);

        assertThatThrownBy(() -> s.parseToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void customerToken_hasCustomerTypeAndSubject() {
        JwtService s = newService(3600L);
        Customer c = Customer.builder().id(42L).email("c@x.com").build();

        String token = s.generateCustomerToken(c);

        assertThat(s.extractType(token)).isEqualTo("CUSTOMER");
        assertThat(s.extractCustomerId(token)).isEqualTo(42L);
        assertThat(s.isExpired(token)).isFalse();
    }

    @Test
    void staffToken_hasNoType() {
        JwtService s = newService(3600L);
        StaffUserMerchant l = link(1L, 1L, StaffRole.ADMIN);

        String token = s.generateToken(l.getStaffUser(), l);

        assertThat(s.extractType(token)).isNull(); // distingue de token de cliente
    }

    @Test
    void staffToken_hasJti_iss_audStaff() {
        JwtService s = newService(3600L);
        StaffUserMerchant l = link(1L, 1L, StaffRole.ADMIN);
        String token = s.generateToken(l.getStaffUser(), l);

        var claims = s.parseToken(token).getPayload();
        assertThat(claims.getId()).isNotBlank();                 // jti
        assertThat(claims.getIssuer()).isEqualTo(JwtService.ISSUER);
        assertThat(claims.getAudience()).contains(JwtService.AUDIENCE_STAFF);
        assertThat(s.extractJti(token)).isEqualTo(claims.getId());
    }

    @Test
    void customerToken_hasJti_iss_audCustomer() {
        JwtService s = newService(3600L);
        Customer c = Customer.builder().id(42L).email("c@x.com").build();
        String token = s.generateCustomerToken(c);

        var claims = s.parseToken(token).getPayload();
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuer()).isEqualTo(JwtService.ISSUER);
        assertThat(claims.getAudience()).contains(JwtService.AUDIENCE_CUSTOMER);
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        // FIX-11: token assinado com a mesma chave mas iss diferente é rejeitado.
        JwtService s = newService(3600L);
        String secret = (String) ReflectionTestUtils.getField(s, "secret");
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = io.jsonwebtoken.Jwts.builder()
                .subject("1")
                .issuer("other-issuer")
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();
        assertThatThrownBy(() -> s.parseToken(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAlgNoneUnsignedToken() {
        JwtService s = newService(3600L);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = enc.encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes());
        String unsigned = header + "." + payload + ".";

        assertThatThrownBy(() -> s.parseToken(unsigned)).isInstanceOf(JwtException.class);
    }
}
