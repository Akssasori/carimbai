package com.app.carimbai.services;

import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import com.app.carimbai.models.fidelity.Customer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.Set;
import io.jsonwebtoken.JwtException;

@Service
@RequiredArgsConstructor
public class JwtService {

    /**
     * Issuer + audiences fixos (FIX-11 / SEC-012). Issuer estável protege contra
     * reuso entre ambientes; audience separa staff de cliente — um JWT emitido
     * para um nunca passa pelo path do outro.
     */
    public static final String ISSUER = "carimbai";
    public static final String AUDIENCE_STAFF = "carimbai:staff";
    public static final String AUDIENCE_CUSTOMER = "carimbai:customer";

    private Key signingKey;

    @Value("${carimbai.jwt.secret}")
    private String secret;

    @Value("${carimbai.jwt.expiration-seconds:28800}")
    private long expirationSeconds;

    // Customer sessions expire within one working day unless explicitly configured.
    @Value("${carimbai.jwt.customer-expiration-seconds:28800}")
    private long customerExpirationSeconds;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(StaffUser user, StaffUserMerchant activeLink) {
        return generateToken(user, activeLink, null);
    }

    public String generateToken(StaffUser user, StaffUserMerchant activeLink, UUID familyId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE_STAFF).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("role", activeLink.getRole().name())
                .claim("version", user.getTokenVersion())
                .claim("merchantId", activeLink.getMerchant().getId())
                .signWith(signingKey);
        if (familyId != null) builder.claim("sid", familyId.toString());
        return builder.compact();
    }

    /** Token de CLIENTE (FIX-02, Fase A): sub = customerId, aud = staff vs customer. */
    public String generateCustomerToken(Customer customer) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(customerExpirationSeconds);
        return Jwts.builder()
                .subject(customer.getId().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE_CUSTOMER).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("type", "CUSTOMER")
                .claim("version", customer.getTokenVersion())
                .signWith(signingKey)
                .compact();
    }

    /** Tipo do token: "CUSTOMER" para cliente; null para tokens de staff (legado). */
    public String extractType(String token) {
        return parseToken(token).getPayload().get("type", String.class);
    }

    public Long extractCustomerId(String token) {
        return Long.parseLong(parseToken(token).getPayload().getSubject());
    }

    /**
     * Parse + verificação completa: assinatura, exp, iss (==carimbai), aud (∈ {staff,customer}).
     * Token sem iss/aud (legado) é rejeitado como inválido.
     */
    public Jws<Claims> parseToken(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith((SecretKey) signingKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        String type = claims.get("type", String.class);
        String audience;
        if ("CUSTOMER".equals(type)) {
            audience = AUDIENCE_CUSTOMER;
            if (!(claims.get("version") instanceof Number)) throw new JwtException("Customer session must be renewed");
        } else if (type == null) {
            audience = AUDIENCE_STAFF;
        } else {
            throw new JwtException("Invalid token type");
        }
        if (!Set.of(audience).equals(claims.getAudience()) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getId() == null
                || claims.getId().length() > 64 || claims.getId().isBlank()) {
            throw new JwtException("Invalid token claims");
        }
        try {
            if (Long.parseLong(claims.getSubject()) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw new JwtException("Invalid token subject");
        }
        return parsed;
    }

    public Long extractStaffId(String token) {
        Claims claims = parseToken(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public String extractRole(String token) {
        Claims claims = parseToken(token).getPayload();
        return claims.get("role", String.class);
    }

    public Long extractMerchantId(String token) {
        Claims claims = parseToken(token).getPayload();
        Number value = claims.get("merchantId", Number.class);
        return value == null ? null : value.longValue();
    }

    public long extractTokenVersion(String token) {
        Number value = parseToken(token).getPayload().get("version", Number.class);
        return value == null ? 0L : value.longValue();
    }

    public UUID extractFamilyId(String token) {
        String value = parseToken(token).getPayload().get("sid", String.class);
        return value == null ? null : UUID.fromString(value);
    }

    /** jti (token id) — chave usada para revogação (logout). */
    public String extractJti(String token) {
        return parseToken(token).getPayload().getId();
    }

    /** Instante de expiração — usado para definir o TTL da entrada na denylist. */
    public Instant extractExpiration(String token) {
        return parseToken(token).getPayload().getExpiration().toInstant();
    }

    public boolean isExpired(String token) {
        Date exp = parseToken(token).getPayload().getExpiration();
        return exp.before(new Date());
    }

}
