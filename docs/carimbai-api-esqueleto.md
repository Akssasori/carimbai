# carimbai-api — Esqueleto mínimo funcional

Show! Vamos abrir o carimbai-api com o mínimo funcional para já compilar e começar a testar os fluxos A→B depois. Tudo em Java 21 + Spring Boot 3, IDs Long, schemas core, fidelity, ops.

Abaixo vai o esqueleto: entidades, repositórios, enums, DTOs e os dois primeiros endpoints (`/cards/{id}/qr` e `/stamp`) com o `StampTokenService` (HMAC+TTL+anti-replay). É só colar nos pacotes sugeridos.

## Pacotes (sugestão)

```
app.carimbai
├─ core
│  ├─ merchant
│  ├─ location
│  └─ staff
├─ fidelity
│  ├─ program
│  ├─ customer
│  ├─ card
│  ├─ stamp
│  └─ reward
├─ ops
│  └─ token
└─ api
   ├─ cards
   └─ stamps
```

## Enums (reutilizáveis)

```java
package app.carimbai.fidelity.card;
public enum CardStatus { ACTIVE, BLOCKED, EXPIRED }
```

```java
package app.carimbai.core.staff;
public enum StaffRole { ADMIN, CASHIER }
```

```java
package app.carimbai.fidelity.stamp;
public enum StampSource { A, B } // A = CUSTOMER_QR, B = STORE_QR
```

## CORE

### Merchant

```java
package app.carimbai.core.merchant;
import jakarta.persistence.*;

@Entity
@Table(name = "merchants", schema = "core")
public class Merchant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=120)
    private String name;

    @Column(length=20)
    private String document;

    @Column(nullable=false)
    private Boolean active = true;
    // getters/setters
}
```

### Location

```java
package app.carimbai.core.location;
import app.carimbai.core.merchant.Merchant;
import jakarta.persistence.*;

@Entity
@Table(name = "locations", schema = "core")
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable=false, length=120)
    private String name;

    private String address;

    @Column(columnDefinition = "jsonb not null default '{}'::jsonb")
    private String flags = "{}";
    // getters/setters
}
```

### StaffUser

```java
package app.carimbai.core.staff;
import app.carimbai.core.merchant.Merchant;
import jakarta.persistence.*;

@Entity
@Table(name = "staff_users", schema = "core")
public class StaffUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="merchant_id", nullable=false)
    private Merchant merchant;

    @Column(nullable=false, unique=true, length=160)
    private String email;

    @Column(name="password_hash", nullable=false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private StaffRole role;

    @Column(nullable=false)
    private Boolean active = true;
    // getters/setters
}
```

### Repos CORE

```java
package app.carimbai.core.merchant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MerchantRepository extends JpaRepository<Merchant, Long> {}
```

```java
package app.carimbai.core.location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByMerchantId(Long merchantId);
}
```

```java
package app.carimbai.core.staff;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {}
```

## FIDELITY

### Program

```java
package app.carimbai.fidelity.program;
import app.carimbai.core.merchant.Merchant;
import jakarta.persistence.*;

@Entity
@Table(name="programs", schema="fidelity")
public class Program {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="merchant_id", nullable=false)
    private Merchant merchant;

    @Column(nullable=false, length=120)
    private String name;

    @Column(name="rule_total_stamps", nullable=false)
    private Integer ruleTotalStamps = 10;

    @Column(name="reward_name", nullable=false, length=120)
    private String rewardName = "Refeição grátis";

    @Column(name="expiration_days")
    private Integer expirationDays;
    // getters/setters
}
```

### Customer

```java
package app.carimbai.fidelity.customer;
import jakarta.persistence.*;

@Entity
@Table(name="customers", schema="fidelity")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length=160)
    private String email;

    @Column(length=30)
    private String phone;

    @Column(name="provider_id", length=80)
    private String providerId;
    // getters/setters
}
```

### Card

```java
package app.carimbai.fidelity.card;
import app.carimbai.fidelity.program.Program;
import app.carimbai.fidelity.customer.Customer;
import jakarta.persistence.*;

@Entity
@Table(name="cards", schema="fidelity", uniqueConstraints = @UniqueConstraint(name="uq_cards_program_customer", columnNames = {"program_id","customer_id"}))
public class Card {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="program_id", nullable=false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="customer_id", nullable=false)
    private Customer customer;

    @Column(name="stamps_count", nullable=false)
    private Integer stampsCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name="expires_at")
    private java.time.OffsetDateTime expiresAt;

    @Column(name="created_at")
    private java.time.OffsetDateTime createdAt = java.time.OffsetDateTime.now();
    // getters/setters
}
```

### Stamp

```java
package app.carimbai.fidelity.stamp;
import app.carimbai.fidelity.card.Card;
import app.carimbai.core.location.Location;
import app.carimbai.core.staff.StaffUser;
import jakarta.persistence.*;

@Entity
@Table(name="stamps", schema="fidelity")
public class Stamp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="card_id", nullable=false)
    private Card card;

    @Column(name="when_at", nullable=false)
    private java.time.OffsetDateTime whenAt = java.time.OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="cashier_id")
    private StaffUser cashier;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=1)
    private StampSource source; // A ou B

    @Column(name="token_id")
    private Long tokenId;

    @Column
    private java.net.InetAddress ip; // opcional

    @Column(name="user_agent")
    private String userAgent;
    // getters/setters
}
```

### Reward

```java
package app.carimbai.fidelity.reward;
import app.carimbai.fidelity.card.Card;
import app.carimbai.core.location.Location;
import app.carimbai.core.staff.StaffUser;
import jakarta.persistence.*;

@Entity
@Table(name="rewards", schema="fidelity")
public class Reward {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="card_id", nullable=false)
    private Card card;

    @Column(name="issued_at", nullable=false)
    private java.time.OffsetDateTime issuedAt = java.time.OffsetDateTime.now();

    @Column(name="redeemed_at")
    private java.time.OffsetDateTime redeemedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="cashier_id")
    private StaffUser cashier;
    // getters/setters
}
```

### Repos FIDELITY

```java
package app.carimbai.fidelity.program;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findByMerchantId(Long merchantId);
}
```

```java
package app.carimbai.fidelity.customer;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerRepository extends JpaRepository<Customer, Long> {}
```

```java
package app.carimbai.fidelity.card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByProgramIdAndCustomerId(Long programId, Long customerId);
}
```

```java
package app.carimbai.fidelity.stamp;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StampRepository extends JpaRepository<Stamp, Long> {}
```

```java
package app.carimbai.fidelity.reward;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RewardRepository extends JpaRepository<Reward, Long> {}
```

## OPS – Tokens efêmeros (HMAC + TTL + anti-replay)

### StampToken (entity)

```java
package app.carimbai.ops.token;
import jakarta.persistence.*;

@Entity
@Table(name="stamp_tokens", schema="ops")
public class StampToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=20)
    private String type; // CUSTOMER_QR | STORE_QR

    @Column(name="id_ref", nullable=false)
    private Long idRef; // cardId (A) ou locationId (B)

    @Column(nullable=false, unique=true)
    private java.util.UUID nonce;

    @Column(name="exp_at", nullable=false)
    private java.time.OffsetDateTime expAt;

    @Column(name="used_at")
    private java.time.OffsetDateTime usedAt;

    @Column(nullable=false)
    private String signature;
    // getters/setters
}
```

### Repo

```java
package app.carimbai.ops.token;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StampTokenRepository extends JpaRepository<StampToken, Long> {
    boolean existsByNonce(UUID nonce);
    Optional<StampToken> findByNonce(UUID nonce);
}
```

### Service de Tokens

```java
package app.carimbai.ops.token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class StampTokenService {
    private final StampTokenRepository repo;
    private final ThreadLocal<Mac> macThreadLocal;
    private static final Duration TTL = Duration.ofSeconds(45);

    public StampTokenService(StampTokenRepository repo, @Value("${carimbai.hmac-secret}") String secret) {
        this.repo = repo;
        this.macThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return mac;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public TokenPayload issueCustomer(Long cardId) { return issue("CUSTOMER_QR", cardId); }
    public TokenPayload issueStore(Long locationId) { return issue("STORE_QR", locationId); }

    private TokenPayload issue(String type, Long idRef) {
        var nonce = UUID.randomUUID();
        var exp = OffsetDateTime.now().plus(TTL);
        var payload = payload(idRef, nonce, exp);
        var sig = sign(payload);
        return new TokenPayload(type, idRef, nonce, exp.toEpochSecond(), sig);
    }

    public void validateAndConsume(TokenPayload p) {
        // exp
        var expTime = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(p.exp()), java.time.ZoneOffset.UTC);
        if (OffsetDateTime.now().isAfter(expTime)) throw new IllegalArgumentException("Token expired");

        // assinatura
        var expected = sign(payload(p.idRef(), p.nonce(), expTime));
        if (!constantTimeEquals(expected, p.sig())) throw new IllegalArgumentException("Invalid signature");

        // anti-replay
        if (repo.existsByNonce(p.nonce())) throw new IllegalStateException("Replay detected");

        // persist uso
        var entity = new StampToken();
        entity.setType(p.type());
        entity.setIdRef(p.idRef());
        entity.setNonce(p.nonce());
        entity.setExpAt(expTime);
        entity.setUsedAt(OffsetDateTime.now());
        entity.setSignature(p.sig());
        repo.save(entity);
    }

    private String payload(Long idRef, UUID nonce, OffsetDateTime exp) {
        return idRef + "|" + nonce + "|" + exp.toEpochSecond();
    }

    private String sign(String payload) {
        var mac = macThreadLocal.get();
        mac.reset();
        byte[] out = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i=0; i<a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    public record TokenPayload(String type, Long idRef, UUID nonce, long exp, String sig) {}
}
```

## API – `/cards/{id}/qr` e `/stamp`

### DTOs

```java
package app.carimbai.api.cards;
import java.util.UUID;
public record QrTokenResponse(String type, Long idRef, UUID nonce, long exp, String sig) {}
```

```java
package app.carimbai.api.stamps;
public record StampRequest(String type, Object payload) {} // payload será desserializado conforme type
```

// Payloads:

```java
package app.carimbai.api.stamps;
import java.util.UUID;
public record CustomerQrPayload(Long cardId, UUID nonce, long exp, String sig) {}
public record StoreQrPayload(Long locationId, UUID nonce, long exp, String sig) {}
```

```java
package app.carimbai.api.stamps;
public record StampResponse(boolean ok, Long cardId, int stamps, int needed, boolean rewardIssued) {}
```

### CardsController (gera token A por enquanto)

```java
package app.carimbai.api.cards;
import app.carimbai.ops.token.StampTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
public class CardsController {
    private final StampTokenService tokenService;
    public CardsController(StampTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<QrTokenResponse> qr(@PathVariable Long id) {
        var t = tokenService.issueCustomer(id);
        return ResponseEntity.ok(new QrTokenResponse(t.type(), t.idRef(), t.nonce(), t.exp(), t.sig()));
    }
}
```

### StampsController (aceita A agora; B já “latente”)

```java
package app.carimbai.api.stamps;
import app.carimbai.fidelity.card.Card;
import app.carimbai.fidelity.card.CardRepository;
import app.carimbai.fidelity.program.ProgramRepository;
import app.carimbai.fidelity.stamp.*;
import app.carimbai.ops.token.StampTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stamp")
public class StampsController {
    private final StampTokenService tokenService;
    private final CardRepository cardRepo;
    private final StampRepository stampRepo;
    private final ProgramRepository programRepo;

    public StampsController(StampTokenService tokenService, CardRepository cardRepo, StampRepository stampRepo, ProgramRepository programRepo) {
        this.tokenService = tokenService;
        this.cardRepo = cardRepo;
        this.stampRepo = stampRepo;
        this.programRepo = programRepo;
    }

    @PostMapping
    public ResponseEntity<StampResponse> stamp(@RequestBody StampRequest req) {
        switch (req.type()) {
            case "CUSTOMER_QR" -> {
                var p = (com.fasterxml.jackson.databind.ObjectMapperHolder.INSTANCE) == null; // placeholder
            }
        }
        return handleCustomer(req);
    }

    // Implementação para CUSTOMER_QR (A). Para STORE_QR (B) você só cria um método semelhante.
    private ResponseEntity<StampResponse> handleCustomer(StampRequest req) {
        // desserializa payload para CustomerQrPayload
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var p = mapper.convertValue(req.payload(), CustomerQrPayload.class);
        var payload = new StampTokenService.TokenPayload("CUSTOMER_QR", p.cardId(), p.nonce(), p.exp(), p.sig());
        tokenService.validateAndConsume(payload);

        var card = cardRepo.findById(p.cardId()).orElseThrow();
        card.setStampsCount(card.getStampsCount() + 1);
        var savedCard = cardRepo.save(card);

        // grava stamp
        var s = new Stamp();
        s.setCard(card);
        s.setSource(StampSource.A);
        stampRepo.save(s);

        var program = programRepo.findById(card.getProgram().getId()).orElseThrow();
        var needed = program.getRuleTotalStamps();
        boolean reward = savedCard.getStampsCount() >= needed;
        return ResponseEntity.ok(new StampResponse(true, card.getId(), savedCard.getStampsCount(), needed, reward));
    }
}
```

> Observação: para manter a resposta compacta, deixei a desserialização usando `ObjectMapper#convertValue`. No seu projeto, crie DTOs tipados no `StampRequest` (ou um `@JsonTypeInfo` por `type`) e aplique validações (`@Valid`).

## application.yml (segredo HMAC)

```yaml
carimbai:
  hmac-secret: ${CARIMBAI_HMAC_SECRET:dev-secret-change-me}

spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

## Próximos passos

Se curtir essa base, no próximo passo eu:
- ajusto o `StampsController` para tipagem forte (sem `convertValue`),
- adiciono o endpoint `GET /locations/{id}/qr` (token B, rotativo),
- e coloco regras de resgate (PIN do caixa) + rate limit.
