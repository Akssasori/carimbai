# Tier 3 — Backend hardening (audit log + rate limit + refresh token)

## Contexto

Tier 1 (segurança) e Tier 2 (UX/funcional) fechados. Esta entrega cobre 3 dos 5 itens do Tier 3 — os que **não dependem de SMTP ou decisões de produto sobre push notifications**:

1. **Audit log** — tabela `ops.audit_log` + service centralizado + plugado em 8 services críticos.
2. **Rate limit no login** — Bucket4j em memória (5 tentativas / 15min por email+IP).
3. **Refresh token** — só staff, access 1h + refresh 30d com rotação. Frontend faz refresh transparente em 401.

**Fora desta entrega** (Tier 3 fase 2, sessão dedicada):
- Reset de senha do staff (precisa SMTP).
- Push notifications backend (precisa decisão de produto sobre eventos).

## 1. Audit log

### 1.1 Schema (`ops.audit_log`)

Migration: [`V2026.19.05.07.12.00__create_audit_log.sql`](carimbai/src/main/resources/db.migration/V2026.19.05.07.12.00__create_audit_log.sql).

```
ops.audit_log
├── id BIGINT PK
├── occurred_at TIMESTAMPTZ DEFAULT NOW()
├── actor_type VARCHAR(20) (STAFF | CUSTOMER | SYSTEM | ANONYMOUS)
├── actor_id BIGINT (nullable)
├── action VARCHAR(60) (enum textual — ver AuditAction)
├── entity_type VARCHAR(40) (nullable)
├── entity_id BIGINT (nullable)
├── merchant_id BIGINT (nullable)
├── success BOOLEAN DEFAULT TRUE
├── details JSONB DEFAULT '{}'::jsonb
├── ip INET (nullable)
└── user_agent TEXT (nullable)
```

3 índices para queries de investigação típicas: por tempo (`occurred_at DESC`), por contexto (`merchant_id + action + occurred_at`), por ator (`actor_type + actor_id + occurred_at`).

### 1.2 Ações rastreadas

Enum [`AuditAction`](carimbai/src/main/java/com/app/carimbai/enums/AuditAction.java):

| Ação | Disparada em | Detalhes JSON |
|---|---|---|
| `LOGIN_SUCCESS` | `AuthService.login` ok | `email`, `role` |
| `LOGIN_FAILED` | `AuthService.login` falha (3 sub-razões) | `email`, `reason` (`USER_NOT_FOUND`, `USER_INACTIVE`, `WRONG_PASSWORD`, `NO_ACTIVE_MERCHANT`) |
| `LOGIN_RATE_LIMITED` | Rate limit dispara | `email`, `retryAfterSeconds` |
| `LOGOUT` | `AuthService.logout` | — |
| `TOKEN_REFRESHED` | `AuthService.refresh` ok | — |
| `STAMP_APPLIED` | `StampsService.applyStamp` ok | `cardId`, `stamps`, `needed`, `rewardIssued`, `locationId?` |
| `REWARD_REDEEMED` | `RedeemService.redeem` ok | `cardId`, `rewardId`, `programId`, `rewardName`, `locationId?` |
| `STAFF_CREATED` | `StaffService.createStaffUser` | `email`, `role` |
| `STAFF_ROLE_CHANGED` | `StaffService.updateStaffInMerchant` (role mudou) | `newRole` |
| `STAFF_ACTIVATED` / `STAFF_DEACTIVATED` | `StaffService.updateStaffInMerchant` (active mudou) | — |
| `STAFF_PIN_SET` | `StaffService.setPin` | `targetStaffId` |
| `PROGRAM_CREATED` | `ProgramService.createProgram` | `name`, `ruleTotalStamps`, `rewardName` |
| `PROGRAM_UPDATED` | `ProgramService.updateProgram` | só campos efetivamente alterados |
| `LOCATION_CREATED` | `LocationService.saveLocationByMerchantId` | `name`, `address` |
| `LOCATION_UPDATED` | `LocationService.updateLocation` | só campos efetivamente alterados (incluindo flags) |

### 1.3 `AuditService`

[`AuditService.java`](carimbai/src/main/java/com/app/carimbai/services/AuditService.java) — centraliza a escrita. Pontos importantes:

- **Engole exceções**: se a gravação no banco falhar, loga via SLF4J mas **não quebra a operação de negócio**. Audit não pode bloquear stamp/redeem.
- **Captura IP e User-Agent automaticamente** via `HttpServletRequest` injetado (quando disponível). Em jobs/background, esses campos ficam null.
- **API fluent**: `AuditService.AuditEntry.builder()...build()` para passar detalhes opcionais.

### 1.4 Como consultar (MVP)

Não há endpoint de leitura nesta entrega — escolha consciente. Para investigação pontual:

```sql
-- Tentativas de login recentes (e quais falharam)
SELECT occurred_at, action, success, details->>'email' AS email, details->>'reason' AS reason, ip
FROM ops.audit_log
WHERE action IN ('LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGIN_RATE_LIMITED')
ORDER BY occurred_at DESC LIMIT 50;

-- Todos os carimbos e resgates de um merchant hoje
SELECT occurred_at, action, actor_id AS cashier_id, entity_id, details
FROM ops.audit_log
WHERE merchant_id = $1
  AND action IN ('STAMP_APPLIED', 'REWARD_REDEEMED')
  AND occurred_at >= CURRENT_DATE
ORDER BY occurred_at DESC;

-- Histórico de mudanças de um staff
SELECT occurred_at, action, actor_id AS by, details
FROM ops.audit_log
WHERE entity_type = 'StaffUser' AND entity_id = $1
ORDER BY occurred_at DESC;
```

## 2. Rate limit no login

### 2.1 Mecanismo

[`LoginRateLimitService.java`](carimbai/src/main/java/com/app/carimbai/services/LoginRateLimitService.java) usa **Bucket4j em memória** (dep nova: `com.bucket4j:bucket4j-core:8.10.1`).

- Token bucket por chave `email_lowercased|ip`.
- Capacity: `carimbai.login-rate-limit.max-attempts` (default **5**).
- Janela: `carimbai.login-rate-limit.window-minutes` (default **15** min).
- IP detectado via `X-Forwarded-For` (primeiro IP da chain) ou `remoteAddr`.

### 2.2 Comportamento

1. `AuthService.login` chama `loginRateLimitService.checkOrThrow(email)` antes de qualquer outra coisa.
2. Se o bucket está esgotado, joga `LoginRateLimitedException` com `retryAfterSeconds`.
3. `GlobalExceptionHandler` mapeia para **HTTP 429** com header `Retry-After` e body JSON `{error, message, retryAfterSeconds}`.
4. Se o login é **bem-sucedido**, o bucket daquela chave é **resetado** (`recordSuccess`) — quem digita errado 3x e acerta na 4ª não fica "carregado".
5. Toda dispara um `AUDIT_LOG` com action `LOGIN_RATE_LIMITED`.

### 2.3 Escala futura

Bucket4j em memória funciona em single-instance. Se você escalar para múltiplas instâncias atrás de um load balancer, o limite vira "por instância" — efetivamente mais frouxo. Mitigação: trocar `ConcurrentHashMap<String, Bucket>` por `Bucket4j-redis` (`bucket4j-redis-spring-boot-starter`). A API pública do service não muda.

## 3. Refresh token

### 3.1 Design

| | Antes (Tier 2) | Depois (Tier 3) |
|---|---|---|
| Access token TTL | 12h | **1h** |
| Refresh token | — | **30 dias com rotação** |
| Logout server-side | — | revoga refresh |

**Decisão**: só **staff** ganha refresh. Customer continua com JWT de 30 dias sem refresh (clientes raramente logam de novo no app; UX seria contraproducente).

### 3.2 Schema (`ops.refresh_tokens`)

Migration: [`V2026.19.05.07.13.00__create_refresh_tokens.sql`](carimbai/src/main/resources/db.migration/V2026.19.05.07.13.00__create_refresh_tokens.sql).

```
ops.refresh_tokens
├── id BIGINT PK
├── token_hash VARCHAR(64) UNIQUE  -- SHA-256 do token cru
├── staff_user_id BIGINT FK
├── merchant_id BIGINT FK
├── issued_at, expires_at TIMESTAMPTZ
├── revoked_at TIMESTAMPTZ (nullable)
├── replaced_by_id BIGINT (nullable, FK self) -- aponta para o sucessor após rotação
├── ip INET, user_agent TEXT
└── idx_refresh_tokens_staff_active (partial: WHERE revoked_at IS NULL)
```

**Segurança crítica**: armazenamos só o **hash SHA-256** do token. Vazamento do banco não expõe os tokens (atacante precisaria quebrar SHA-256). Cliente armazena o token cru em `localStorage`.

### 3.3 `RefreshTokenService`

[`RefreshTokenService.java`](carimbai/src/main/java/com/app/carimbai/services/RefreshTokenService.java) tem 3 operações:

- **`issue(staff, merchant)`**: gera 32 bytes aleatórios (`SecureRandom`), encoda em base64url, persiste o hash. Retorna `IssuedToken(rawToken, persisted)`.
- **`rotate(rawToken)`**: valida (existe, não expirado, não revogado, staff ainda ativo), emite novo, marca o antigo como revogado com `replaced_by_id` apontando para o novo. Detecta uso de token revogado (sinal de comprometimento) — **a ação reativa** (revogar a família inteira) **fica para Tier 4**.
- **`revoke(rawToken)`**: idempotente. Usado pelo logout.

### 3.4 Endpoints novos

| Endpoint | Auth | Body | Response |
|---|---|---|---|
| `POST /api/auth/refresh` | **permitAll** (auth pelo próprio refresh) | `{refreshToken}` | `{token, refreshToken}` (par novo) |
| `POST /api/auth/logout` | permitAll (revoga via refresh) | `{refreshToken}` | 204 |

`/api/auth/login` e `/api/auth/switch-merchant` agora retornam `LoginResponse` com **campo `refreshToken` adicional**. Contrato anterior continua válido (campos antigos inalterados) — clientes que ignoram campos desconhecidos não quebram.

### 3.5 Frontend — refresh transparente

[`services/api.ts`](carimbai-app/src/services/api.ts) ganhou três peças:

**`tryStaffRefresh()`** — single-flight refresh:
- Lê refresh token do localStorage.
- Chama `POST /api/auth/refresh`.
- Em sucesso, atualiza localStorage com par novo. Retorna novo access token.
- Em falha, retorna `null`.
- **Deduplication**: se múltiplos requests simultâneos disparam 401, apenas o primeiro inicia o refresh — os outros aguardam o mesmo `Promise`. Sem isso, todos rotacionariam em paralelo e os "perdedores" receberiam tokens já revogados.

**`authedFetch(url, init, kind?)`** — wrapper de `fetch`:
- Se kind undefined → fetch puro (endpoints públicos).
- Se status 401 e kind 'staff' → tenta refresh + retry com novo Authorization header.
- Se ainda 401 (refresh falhou ou kind 'customer') → limpa localStorage + redireciona.
- 403 nunca dispara redirect (ownership violation continua sendo erro normal).

**Refactor**: todos os 21 métodos autenticados do `apiService` foram migrados de `fetch(...)` + `handleUnauthorized(response, kind)` para `authedFetch(url, init, kind)`. Endpoints públicos continuam usando `authedFetch(url, init)` sem kind (= fetch puro).

**Logout**: `logoutStaff(refreshToken)` chama `POST /api/auth/logout` antes (ou em paralelo a) de limpar localStorage. Best-effort: se a rede falhar, logout local continua. [`useStaffSession.logout`](carimbai-app/src/hooks/useStaffSession.ts) e [`StaffScreen.handleLogout`](carimbai-app/src/components/StaffScreen.tsx) ambos foram atualizados.

### 3.6 UX resultante

Cenários:

1. **Staff trabalha por horas, JWT expira (1h)**:
   - Próxima ação dispara 401.
   - `authedFetch` chama `/auth/refresh` transparentemente.
   - localStorage é atualizado, requisição original é re-executada.
   - **Usuário não percebe nada** (delay de ~100ms).

2. **Refresh token expira (30 dias) ou foi revogado**:
   - Refresh retorna 401.
   - `authedFetch` limpa localStorage e redireciona para `/staff`.
   - Usuário vê tela de login.

3. **Logout explícito**:
   - Frontend chama `POST /api/auth/logout` com o refresh.
   - Backend revoga (`revoked_at = NOW()`).
   - Frontend limpa localStorage e redireciona.
   - Se o usuário voltar com o access token antigo, ele vale até expirar (no máximo 1h restante). **Trade-off conhecido**: revogação imediata do access JWT exigiria blacklist server-side; aceitamos a janela de até 1h.

4. **Brute force no login**:
   - 5 tentativas erradas em 15min para o mesmo `email|ip`.
   - Próxima retorna **429 Too Many Requests** com `Retry-After`.

## 4. Arquivos novos/modificados

### Backend (`carimbai`)

**Novos** — 11
- `src/main/resources/db.migration/V2026.19.05.07.12.00__create_audit_log.sql`
- `src/main/resources/db.migration/V2026.19.05.07.13.00__create_refresh_tokens.sql`
- `src/main/java/com/app/carimbai/enums/AuditAction.java`
- `src/main/java/com/app/carimbai/enums/AuditActorType.java`
- `src/main/java/com/app/carimbai/models/AuditLog.java`
- `src/main/java/com/app/carimbai/models/RefreshToken.java`
- `src/main/java/com/app/carimbai/repositories/AuditLogRepository.java`
- `src/main/java/com/app/carimbai/repositories/RefreshTokenRepository.java`
- `src/main/java/com/app/carimbai/services/AuditService.java`
- `src/main/java/com/app/carimbai/services/LoginRateLimitService.java`
- `src/main/java/com/app/carimbai/services/RefreshTokenService.java`
- `src/main/java/com/app/carimbai/execption/LoginRateLimitedException.java`
- `src/main/java/com/app/carimbai/dtos/login/RefreshTokenRequest.java`
- `src/main/java/com/app/carimbai/dtos/login/RefreshTokenResponse.java`

**Modificados** — 10
- `pom.xml` — dependência `com.bucket4j:bucket4j-core:8.10.1`.
- `src/main/resources/application.yaml` — `expiration-seconds` 43200→3600, `refresh-token-days: 30`, `login-rate-limit.{max-attempts,window-minutes}`.
- `src/main/java/com/app/carimbai/services/AuthService.java` — rate limit, refresh emission, refresh+logout methods, audit logs em todos os caminhos de login.
- `src/main/java/com/app/carimbai/services/StampsService.java` — audit log em `applyStamp`.
- `src/main/java/com/app/carimbai/services/RedeemService.java` — audit log em `redeem`.
- `src/main/java/com/app/carimbai/services/StaffService.java` — audit logs (create, pin, role change, activate, deactivate).
- `src/main/java/com/app/carimbai/services/ProgramService.java` — audit log em create/update.
- `src/main/java/com/app/carimbai/services/LocationService.java` — audit log em create/update.
- `src/main/java/com/app/carimbai/controllers/AuthController.java` — endpoints `/refresh` e `/logout`.
- `src/main/java/com/app/carimbai/config/SecurityConfig.java` — `/auth/refresh` e `/auth/logout` em permitAll.
- `src/main/java/com/app/carimbai/dtos/login/LoginResponse.java` — campo `refreshToken`.
- `src/main/java/com/app/carimbai/handler/GlobalExceptionHandler.java` — handlers para `LoginRateLimitedException` (429) e `RefreshTokenInvalidException` (401).

### Frontend (`carimbai-app`)

**Modificados** — 5
- `src/types/index.ts` — `StaffLoginResponse.refreshToken`, `RefreshTokenResponse`.
- `src/services/api.ts` — `tryStaffRefresh`, `authedFetch`, `logoutStaff`; refactor de 21 métodos autenticados.
- `src/components/StaffLogin.tsx` — persiste `refreshToken`.
- `src/components/StaffScreen.tsx` — `StaffSession` ganha `refreshToken`; `switchMerchant` atualiza; `handleLogout` chama `logoutStaff`.
- `src/hooks/useStaffSession.ts` — `StaffSession` ganha `refreshToken`; `logout` chama `logoutStaff`.

## 5. Verificação end-to-end

### Backend

```bash
# Compile
cd carimbai && ./mvnw -DskipTests compile  # → BUILD SUCCESS

# Subir local com Postgres
docker-compose up -d postgres
./mvnw spring-boot:run

# === Audit log ===
# Login bem sucedido → audit
curl -X POST http://localhost:1234/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"correct"}'

psql -d carimbai -c "SELECT action, success, details FROM ops.audit_log ORDER BY id DESC LIMIT 3;"
# → LOGIN_SUCCESS com email/role

# === Rate limit ===
# 6 tentativas erradas seguidas
for i in {1..6}; do
  curl -i -X POST http://localhost:1234/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"admin@example.com","password":"wrong"}' 2>&1 | head -1
done
# → 6ª retorna HTTP/1.1 429 com header Retry-After: NNN
# Audit log tem 5x LOGIN_FAILED + 1x LOGIN_RATE_LIMITED

# === Refresh token ===
# Login emite refresh
RESPONSE=$(curl -s -X POST http://localhost:1234/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"correct"}')
TOKEN=$(echo $RESPONSE | jq -r .token)
REFRESH=$(echo $RESPONSE | jq -r .refreshToken)
echo "access TTL ~1h, refresh ~30 dias"

# Refresh rotativo
NEW=$(curl -s -X POST http://localhost:1234/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}")
NEW_TOKEN=$(echo $NEW | jq -r .token)
NEW_REFRESH=$(echo $NEW | jq -r .refreshToken)
[ "$NEW_TOKEN" != "$TOKEN" ] && echo "✓ access novo"
[ "$NEW_REFRESH" != "$REFRESH" ] && echo "✓ refresh rotacionado"

# Refresh antigo agora deve falhar
curl -i -X POST http://localhost:1234/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" 2>&1 | head -3
# → HTTP/1.1 401 REFRESH_TOKEN_INVALID

# Logout
curl -X POST http://localhost:1234/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$NEW_REFRESH\"}"
# → 204 No Content

# Tentar usar refresh revogado
curl -i -X POST http://localhost:1234/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$NEW_REFRESH\"}" 2>&1 | head -3
# → 401

# Banco confirma
psql -d carimbai -c "SELECT id, staff_user_id, revoked_at, replaced_by_id FROM ops.refresh_tokens ORDER BY id;"
```

### Frontend

1. `cd carimbai-app && npm run dev`.
2. Login como staff → DevTools → `localStorage.carimbai_staff_session` agora contém `refreshToken`.
3. Limpar manualmente o `token` (manter `refreshToken` válido) no localStorage → próxima ação na UI dispara refresh transparente, atualiza o token e retoma operação. Usuário não vê nada de ruim.
4. Limpar AMBOS (sessão zumbi) → próxima ação → 401 → refresh falha → redirect para `/staff`. Limpo.
5. Logout pelo sidebar → backend recebe `/auth/logout` (verifique Network tab) e revoga; localStorage limpo; redirect para `/staff`.

## 6. Riscos e gaps remanescentes

| Risco/gap | Impacto | Próximo passo |
|---|---|---|
| Access JWT sobrevive até expirar mesmo após logout | Janela de até 1h onde token vazado pode ser usado | Blacklist com Redis (Tier 4) ou access TTL ainda menor |
| Bucket4j em memória não distribuído | Multi-instance: limite "por instância" | Migrar para Bucket4j-Redis quando escalar |
| Audit log sem visualização | Investigação só via SQL | Endpoint admin `GET /api/admin/audit-log` + UI (Tier 4) |
| Audit log pode crescer indefinidamente | Após meses, tabela inflada | Job de archive/purge (manter últimos 90/180 dias) — Tier 4 |
| Reuso de refresh token revogado é detectado mas não reage | Sinal de comprometimento ignorado | Quando detectar reuso, revogar família inteira (Tier 4) |
| Password reset ainda não existe | Esqueceu a senha = DBA precisa atualizar | Tier 3 fase 2 (precisa SMTP) |
| Push notifications backend ainda não existe | Botão escondido no front | Tier 3 fase 2 (precisa decisão de produto) |
| `MethodArgumentNotValidException` body verboso | Pouco grave; uso de devs | Limpar handler (1h) |

## 7. Próximos passos

**Tier 3 fase 2 (sessão dedicada):**
1. Reset de senha do staff — precisa decidir SMTP provider, configurar credenciais, depois implementar (endpoint `POST /api/auth/forgot-password` + tabela de tokens de reset + endpoint `POST /api/auth/reset-password` + reativar link no `StaffLogin.tsx`).
2. Push notifications backend — precisa decidir gatilhos (welcome no primeiro stamp? reward earned? redeemed?). Depois implementar (webpush lib + tabela `push_subscriptions` + endpoints `/api/notifications/{vapid-public-key,subscribe}` + sender + reativar botão "Ativar" no `CustomerScreen.tsx`).

**Tier 4 (escala):**
- Blacklist de access JWTs (Redis).
- Audit log archive/purge job.
- Audit log read endpoint + UI.
- Detecção de reuso de refresh token revogado → revogar família.
- Bucket4j-Redis para rate limit distribuído.
- Validação de DTOs com `@Email`, `@Pattern`, força de senha.

---

*Referência cruzada: [`tier1-correcoes-seguranca.md`](./tier1-correcoes-seguranca.md), [`tier2-quick-wins.md`](./tier2-quick-wins.md), [`tier2-fase2-frontend-ui-gestao.md`](./tier2-fase2-frontend-ui-gestao.md).*
