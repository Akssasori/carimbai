package com.app.carimbai.services;

import com.app.carimbai.models.core.StaffUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    private Key signingKey;

    @Value("${carimbai.jwt.secret}")
    private String secret;

    @Value("${carimbai.jwt.expiration-seconds:28800}")
    private long expirationSeconds;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(StaffUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .setSubject(user.getId().toString()) // sub = staffId
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .addClaims(Map.of(
                        "role", user.getRole().name(),
                        "merchantId", user.getMerchant().getId(),
                        "email", user.getEmail()
                ))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
    }

    public Long extractStaffId(String token) {
        Claims claims = parseToken(token).getBody();
        return Long.parseLong(claims.getSubject());
    }

    public String extractRole(String token) {
        Claims claims = parseToken(token).getBody();
        return claims.get("role", String.class);
    }

    public Long extractMerchantId(String token) {
        Claims claims = parseToken(token).getBody();
        Integer asInt = claims.get("merchantId", Integer.class);
        if (asInt != null) return asInt.longValue();
        return claims.get("merchantId", Long.class);
    }

    public boolean isExpired(String token) {
        Date exp = parseToken(token).getBody().getExpiration();
        return exp.before(new Date());
    }

}
