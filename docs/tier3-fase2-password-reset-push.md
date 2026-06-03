# Tier 3 — Fase 2: password reset (Resend) + push notifications

## Contexto

Tier 3 fase 1 (audit log + rate limit + refresh token) já estava em produção. Esta entrega fecha as duas features que dependiam de decisões externas:

1. **Reset de senha do staff** via **Resend (SMTP relay)** — usuário tem domínio verificado, integração via `spring-boot-starter-mail`.
2. **Push notifications** com gatilho de negócio: notifica o cliente quando **falta 1 carimbo** para fechar o cartão e quando o **cartão fica cheio**.

Ambas as features tinham boa parte do frontend pronta (`StaffLogin.tsx` aguardando link de reset, `usePushNotifications.ts` intacto com chamadas pendentes, `sw.js` já tratando payload `{title, body}`). O backend era o lado faltante; agora completo.

---

## 1. Password reset

### 1.1 Fluxo

```
[StaffLogin]
   ↓ clica "Esqueceu a senha?"
[ForgotPassword]
   ↓ digita email + submit
POST /api/auth/forgot-password { email }   → 204 sempre (anti-enumeration)
   │
   ├─ se email existe: emite token, envia email com link
   │     ↓
   │  [Caixa de entrada]
   │     ↓ clica no link
   │  [ResetPassword?token=XXX]
   │     ↓ nova senha + confirmar + submit
   │  POST /api/auth/reset-password { token, newPassword }   → 204 (sucesso) ou 401 (token inválido)
   │     ↓
   │  Senha trocada + TODOS os refresh tokens daquele staff revogados
   │     ↓
   │  Redireciona para /staff (login)
   │
   └─ se email não existe: log audit, mas resposta ao cliente é 204 igual
```

### 1.2 Schema

Migration [`V2026.20.05.07.14.00__create_password_reset_tokens.sql`](carimbai/src/main/resources/db.migration/V2026.20.05.07.14.00__create_password_reset_tokens.sql):

```
ops.password_reset_tokens
├── id BIGINT PK
├── token_hash VARCHAR(64) UNIQUE       -- SHA-256 do token cru
├── staff_user_id BIGINT FK
├── issued_at, expires_at TIMESTAMPTZ
├── used_at TIMESTAMPTZ (nullable; one-shot)
├── ip INET
└── user_agent TEXT
```

Token: 32 bytes aleatórios → base64url → enviado ao usuário no link. Apenas SHA-256 no banco.

### 1.3 Endpoints

| Endpoint | Auth | Body | Response |
|---|---|---|---|
| `POST /api/auth/forgot-password` | permitAll | `{email}` | **Sempre 204** (anti-enumeration) |
| `POST /api/auth/reset-password` | permitAll | `{token, newPassword}` | 204 ou 401 |

### 1.4 Configuração Resend

`application.yaml`:
```yaml
spring:
  mail:
    host: ${CARIMBAI_MAIL_HOST:smtp.resend.com}
    port: ${CARIMBAI_MAIL_PORT:465}
    username: ${CARIMBAI_MAIL_USERNAME:resend}
    password: ${CARIMBAI_RESEND_API_KEY:}
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: true
      mail.smtp.starttls.enable: false
carimbai:
  mail:
    from: ${CARIMBAI_MAIL_FROM:}             # ex: noreply@seudominio.com
    from-name: ${CARIMBAI_MAIL_FROM_NAME:Carimbai}
  app-base-url: ${CARIMBAI_APP_BASE_URL:http://localhost:5173}
  password-reset:
    token-ttl-hours: 1
```

Env vars necessárias em prod:
- `CARIMBAI_RESEND_API_KEY` — API key gerada no painel do Resend.
- `CARIMBAI_MAIL_FROM` — `noreply@seudominio.com` (domínio precisa estar verificado no Resend).
- `CARIMBAI_APP_BASE_URL` — `https://carimbai-app.vercel.app` em prod.

**Fallback silencioso**: se `password` ou `from` vazios, `MailService` loga WARN com o link de reset (utilizável em dev) mas **não quebra a operação**. Audit log continua gravando.

### 1.5 Comportamento ao trocar senha

Em `AuthService.resetPassword`:
1. Consome token (one-shot, marca `used_at = NOW()`).
2. Atualiza `staff_users.password_hash`.
3. **Chama `refreshRepo.revokeAllForStaff(staffId, now)`** — invalida sessões ativas em todos os devices. Defesa contra "alguém roubou minha conta e trocou minha senha".

### 1.6 Audit

2 actions novas:
- `PASSWORD_RESET_REQUESTED` — `success=true` se email existe, `success=false` + reason se não.
- `PASSWORD_RESET_COMPLETED` — após troca efetiva. `details.revokedRefreshTokens` mostra quantos foram revogados.

---

## 2. Push notifications

### 2.1 Fluxo de subscribe

```
[CustomerScreen]
   ↓ cliente clica "Ativar"
GET /api/notifications/vapid-public-key  → { publicKey }
   ↓
navigator.pushManager.subscribe({ applicationServerKey })
   ↓ navegador devolve { endpoint, keys: {p256dh, auth} }
POST /api/notifications/subscribe  → 204
   ↓
Backend: UPSERT em ops.push_subscriptions (idempotente)
```

### 2.2 Fluxo de envio (perto de fechar / cartão cheio)

```
[StampsService.applyStamp]
   ↓ persiste Stamp, atualiza Card
   ↓ publishEvent(StampAppliedEvent)
   ↓ ...transação commita...
[PushNotificationListener] @TransactionalEventListener(AFTER_COMMIT)
   ↓ avalia: remaining == 1?  OU stampsCount >= needed?
   ↓ se sim: PushNotificationService.sendToCustomer (@Async)
   ↓ + audit PUSH_NOTIFICATION_TRIGGERED
[PushNotificationService] (thread "push-N")
   ↓ busca subs do customer
   ↓ envia para cada uma via web-push (VAPID assinado)
   ↓ 404/410 → deleta a sub (auto-cleanup)
```

A transação do carimbo **commita primeiro** e só então o push é despachado. Garante que push nunca sai para um stamp que rolou rollback. Envio em `@Async` num pool dedicado (`pushExecutor`: 2-10 threads, queue 50) — caller (staff) nunca espera.

### 2.3 Schema

Migration [`V2026.20.05.07.15.00__create_push_subscriptions.sql`](carimbai/src/main/resources/db.migration/V2026.20.05.07.15.00__create_push_subscriptions.sql):

```
ops.push_subscriptions
├── id BIGINT PK
├── customer_id BIGINT FK
├── endpoint TEXT
├── p256dh VARCHAR(255)
├── auth VARCHAR(255)
├── created_at, last_used_at TIMESTAMPTZ
└── UNIQUE (customer_id, endpoint)
```

### 2.4 Endpoints

| Endpoint | Auth | Body | Response |
|---|---|---|---|
| `GET /api/notifications/vapid-public-key` | permitAll | — | `{publicKey: "BLB..."}` |
| `POST /api/notifications/subscribe` | **CUSTOMER** | `{endpoint, keys: {p256dh, auth}, customerId?}` | 204 |

**`customerId` no body é IGNORADO** — vem do JWT (`SecurityUtils.getRequiredCustomerId()`). Mantido por compatibilidade com o front existente.

### 2.5 Geração de VAPID keys (operacional)

**Uma vez por ambiente**, fora da app:

```bash
# Tem node? então:
npx web-push generate-vapid-keys

# Output:
# =======================================
# Public Key:  BLB...
# Private Key: tIQ...
# =======================================
```

Salvar como env vars no servidor:
```bash
CARIMBAI_VAPID_PUBLIC_KEY=BLB...
CARIMBAI_VAPID_PRIVATE_KEY=tIQ...
CARIMBAI_VAPID_SUBJECT=mailto:contato@seudominio.com
```

**Não rotacionar levianamente**: trocar VAPID keys invalida TODAS as subscriptions existentes (clientes precisam reativar).

**Fallback silencioso**: se as keys vierem vazias, `PushNotificationService` loga WARN e **não envia push** (operação de carimbo segue normal). Permite rodar local sem VAPID configurado.

### 2.6 Configuração

```yaml
carimbai:
  vapid:
    public-key: ${CARIMBAI_VAPID_PUBLIC_KEY:}
    private-key: ${CARIMBAI_VAPID_PRIVATE_KEY:}
    subject: ${CARIMBAI_VAPID_SUBJECT:mailto:contato@example.com}
  notification:
    remind-at-remaining: 1   # quantos carimbos restantes disparam o lembrete
```

### 2.7 Texto das notificações

Hardcoded em `PushNotificationListener` (i18n é Tier 4):

- **REMIND** (`stamps_count == needed - 1`):
  - Título: `"Falta 1 carimbo!"`
  - Body: `"Em {merchantName}, falta 1 para fechar seu cartão de {programName}."`

- **CARD_FULL** (`stamps_count >= needed`):
  - Título: `"Cartao cheio!"`
  - Body: `"Em {merchantName}, seu cartão de {programName} está pronto pra resgate."`

### 2.8 Audit

Uma action nova: `PUSH_NOTIFICATION_TRIGGERED`. Gravada **por trigger** (não por sub individual — evita ruído quando customer tem múltiplos devices). Inclui `customerId`, `kind` (`REMIND` ou `CARD_FULL`), `title`, `merchantName`, `programName`.

### 2.9 Frontend reativado

- [`CustomerScreen.tsx`](carimbai-app/src/components/CustomerScreen.tsx): import + destructuring + botão "Ativar" descomentados. Botão aparece se `supported && permission !== 'granted'`. Badge "ativa" aparece após sucesso.
- [`usePushNotifications.ts`](carimbai-app/src/hooks/usePushNotifications.ts): aceita `token` agora; passa para `apiService.subscribePush`.
- [`api.ts`](carimbai-app/src/services/api.ts): `subscribePush(customerId, sub, token)` envia `Authorization: Bearer ${token}` e usa `authedFetch(..., 'customer')`.

Service worker `sw.js` **não muda** — já tratava payload `{title, body}` corretamente desde antes.

---

## 3. Arquivos novos/modificados

### Backend (`carimbai`)

**Novos** (17):
- `src/main/resources/db.migration/V2026.20.05.07.14.00__create_password_reset_tokens.sql`
- `src/main/resources/db.migration/V2026.20.05.07.15.00__create_push_subscriptions.sql`
- `src/main/java/com/app/carimbai/models/PasswordResetToken.java`
- `src/main/java/com/app/carimbai/models/PushSubscription.java`
- `src/main/java/com/app/carimbai/repositories/PasswordResetTokenRepository.java`
- `src/main/java/com/app/carimbai/repositories/PushSubscriptionRepository.java`
- `src/main/java/com/app/carimbai/services/PasswordResetTokenService.java`
- `src/main/java/com/app/carimbai/services/MailService.java`
- `src/main/java/com/app/carimbai/services/PushSubscriptionService.java`
- `src/main/java/com/app/carimbai/services/PushNotificationService.java`
- `src/main/java/com/app/carimbai/services/PushNotificationListener.java`
- `src/main/java/com/app/carimbai/events/StampAppliedEvent.java`
- `src/main/java/com/app/carimbai/config/AsyncConfig.java`
- `src/main/java/com/app/carimbai/controllers/NotificationsController.java`
- `src/main/java/com/app/carimbai/dtos/login/ForgotPasswordRequest.java`
- `src/main/java/com/app/carimbai/dtos/login/ResetPasswordRequest.java`
- `src/main/java/com/app/carimbai/dtos/notifications/SubscribePushRequest.java`
- `src/main/java/com/app/carimbai/dtos/notifications/VapidPublicKeyResponse.java`

**Modificados** (7):
- `pom.xml` — `spring-boot-starter-mail`, `nl.martijndwars:web-push:5.1.1`, `bcprov-jdk18on:1.78.1`.
- `src/main/resources/application.yaml` — mail config, app-base-url, password-reset, vapid, notification.
- `src/main/java/com/app/carimbai/services/AuthService.java` — `forgotPassword`, `resetPassword`.
- `src/main/java/com/app/carimbai/services/StampsService.java` — `ApplicationEventPublisher`, publica `StampAppliedEvent`.
- `src/main/java/com/app/carimbai/controllers/AuthController.java` — 2 endpoints de reset.
- `src/main/java/com/app/carimbai/config/SecurityConfig.java` — rotas novas.
- `src/main/java/com/app/carimbai/enums/AuditAction.java` — 3 actions novas.
- `src/main/java/com/app/carimbai/handler/GlobalExceptionHandler.java` — handler de `PasswordResetTokenInvalidException` (401).

### Frontend (`carimbai-app`)

**Novos** (2):
- `src/components/staff/ForgotPassword.tsx`
- `src/components/staff/ResetPassword.tsx`

**Modificados** (5):
- `src/App.tsx` — 2 rotas (`/staff/forgot-password`, `/staff/reset-password`).
- `src/services/api.ts` — `forgotPassword`, `resetPassword`; `subscribePush` aceita token.
- `src/hooks/usePushNotifications.ts` — aceita token.
- `src/components/StaffLogin.tsx` — reativado link "Esqueceu a senha?".
- `src/components/CustomerScreen.tsx` — reativado import + hook + botão "Ativar".

---

## 4. Verificação end-to-end

### Password reset

```bash
# Pre-condicoes (prod ou stg):
export CARIMBAI_RESEND_API_KEY=re_xxx
export CARIMBAI_MAIL_FROM=noreply@seudominio.com
export CARIMBAI_APP_BASE_URL=http://localhost:5173

# 1. Solicitar reset
curl -i -X POST http://localhost:1234/api/auth/forgot-password \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com"}'
# → HTTP/1.1 204 No Content (sempre, independente se email existe)

# 2. Conferir audit
psql -d carimbai -c "SELECT action, success, details FROM ops.audit_log WHERE action = 'PASSWORD_RESET_REQUESTED' ORDER BY id DESC LIMIT 1;"
# → PASSWORD_RESET_REQUESTED success=true (se email existir)

# 3. Conferir caixa de entrada — chega email com link tipo:
#    http://localhost:5173/staff/reset-password?token=<32bytes_base64url>

# 4. Reset com token (substituir TOKEN pelo valor real do email)
curl -i -X POST http://localhost:1234/api/auth/reset-password \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"TOKEN\",\"newPassword\":\"NovaSenha123\"}"
# → 204

# 5. Login com senha nova funciona
curl -X POST http://localhost:1234/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"NovaSenha123"}'
# → 200 com token + refreshToken

# 6. Sessoes antigas foram invalidadas
psql -d carimbai -c "SELECT count(*) FROM ops.refresh_tokens WHERE staff_user_id = <ID> AND revoked_at IS NULL;"
# → 1 (so a recem-criada pelo login do passo 5)

# 7. Reusar o token → 401
curl -i -X POST http://localhost:1234/api/auth/reset-password \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"TOKEN\",\"newPassword\":\"OutraSenha\"}"
# → HTTP/1.1 401 PASSWORD_RESET_TOKEN_INVALID (already used)
```

### Push notifications

**Pré-requisitos**: gerar VAPID keys e configurar env vars (seção 2.5).

```bash
# 1. VAPID public key
curl http://localhost:1234/api/notifications/vapid-public-key
# → {"publicKey":"BLB..."}

# 2. Cliente faz login e ativa push pelo navegador
#    DevTools → Application → Service Workers (deve estar Activated)
#    DevTools → Application → Storage → Subscription deve existir

# 3. Conferir banco
psql -d carimbai -c "SELECT id, customer_id, endpoint, created_at FROM ops.push_subscriptions ORDER BY id DESC LIMIT 1;"

# 4. Aplicar 9 carimbos (assumindo needed=10). Sem push ate aqui.
# 5. Aplicar o 10o carimbo (stamps_count = 9, falta 1)
#    → Cliente recebe notificacao "Falta 1 carimbo!"
# 6. Aplicar o 11o carimbo (stamps_count = 10, cartao cheio)
#    → Cliente recebe "Cartao cheio!"

# 7. Conferir audit
psql -d carimbai -c "SELECT action, details FROM ops.audit_log WHERE action = 'PUSH_NOTIFICATION_TRIGGERED' ORDER BY id DESC LIMIT 5;"
# → 2 rows: kind=REMIND e kind=CARD_FULL

# 8. Simular subscription morta:
#    DevTools → unsubscribe push, OU mexer no endpoint no banco para algo invalido
#    Proximo trigger → backend recebe 410 do FCM → row deletada
```

---

## 5. Riscos e gaps remanescentes

| Risco/gap | Impacto | Mitigação atual / próximo passo |
|---|---|---|
| **`forgot-password` sem rate limit dedicado** | Email-bombing possivel | Resend tem rate limit no provedor; adicionar `LoginRateLimitService` dedicado em Tier 4 |
| **Falha de SMTP não causa erro ao cliente** | Cliente acha que pediu reset mas email não chega | Aceito para MVP; logs do Resend ajudam a investigar |
| **VAPID keys vazadas** | Atacante pode enviar push em nome do app | Configurar apenas via env vars; rotacionar invalida todas subs (custo aceito em emergência) |
| **Push descartado se fila lotar** | Cliente perde notificação se muito tráfego simultâneo | Pool 2-10 threads + queue 50 cobre MVP. Em escala, considerar fila persistente |
| **`@Async` mascara erros** | Falha silenciosa de envio | Logger ERROR em `PushNotificationService.sendOne` capta |
| **Texto do push hardcoded em pt-BR** | Sem i18n | Aceito para MVP brasileiro |
| **Push só notifica perto de fechar (1 carimbo) e quando enche** | Cliente que para nos primeiros carimbos não é re-engajado | Adicionar trigger por inatividade em Tier 4 (job que detecta cards parados há N dias) |
| **Reset de senha revoga TODAS sessões staff** | Re-login obrigatório em todos os devices | Intencional. Documentado. |
| **Sem histórico de envios visível** | Investigação só via audit log no SQL | Endpoint admin de leitura de audit (Tier 4) |

---

## 6. Próximos passos (Tier 4 sugerido)

Em ordem de impacto:

1. **Endpoint de leitura do audit log** + UI admin (`GET /api/admin/audit-log` com filtros).
2. **Job de inatividade** — detecta carrtões "parados há 15 dias" e dispara push de re-engajamento.
3. **Rate limit no `forgot-password`** (Bucket4j com chave `email|ip`).
4. **Notificação por email fallback** quando push falha (para clientes sem push habilitado).
5. **Blacklist de access JWTs** (Redis) para revogação imediata.
6. **Bucket4j-Redis** para rate limit distribuído.
7. **Audit log archive/purge** (manter últimos 90/180 dias).
8. **Customer choose opt-in granular** de notificações.
9. **i18n** dos templates de email e push.

---

*Referência cruzada: [`tier1-correcoes-seguranca.md`](./tier1-correcoes-seguranca.md), [`tier2-quick-wins.md`](./tier2-quick-wins.md), [`tier2-fase2-frontend-ui-gestao.md`](./tier2-fase2-frontend-ui-gestao.md), [`tier3-backend-hardening.md`](./tier3-backend-hardening.md).*
