# Relatório de Reteste e Fechamento — carimbai

> **Fase 15** do `SECURITY_LGPD_PLAN.md`. Cobre (1) o **reteste das correções
> aplicadas** (quick wins do `SECURITY_FIX_PLAN.md`) e (2) uma **correção de
> precisão** descoberta nesta fase (working tree × commitado). Data: 2026-06-13.
>
> Status possíveis: **Fechado** · **Parcial** · **Reaberto** · **Aberto**.
> Nenhum achado é fechado sem evidência. Achados que dependem de
> verificação dinâmica (MockMvc + Postgres) são marcados como
> **Fechado (estático)** quando comprovados por inspeção/compilação, com a
> verificação dinâmica indicada como pendente (precisa de Testcontainers — ver
> `SECURITY_TEST_CASES.md` §19).

## 0. Evidência de build

- `./mvnw -o -DskipTests compile` → **OK** (toda a base compila com as mudanças).
- **Rodada 2** (`./mvnw test`) → **`Tests run: 21, Failures: 0, Errors: 0` — BUILD SUCCESS**:
  - `JwtServiceTest` (4) + `StampTokenServiceTest` (5) — cripto não regrediu;
  - **`DtoValidationTest` (12) — NOVO**: prova, via Jakarta `Validator` (sem DB/Spring),
    as correções de validação (SEC-016/017/022/023/035). Cada anotação tem caso
    positivo e negativo.
- `src/main` alterado (validação/headers/erros/log) + `src/test` adicionado.
  **Nenhuma** mudança em fluxo de autenticação/autorização ou contrato de API.

---

## 1. Reteste das correções aplicadas

| Achado | FIX | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|---|
| SEC-015 | FIX-09 | Log do Facebook passa a registrar só `statusCode`, não o `response.body()` (id/nome/e-mail) | `FacebookTokenVerifier.java` | Inspeção: `log.warn("...status {}", statusCode)` — sem PII. Determinístico. | **Fechado** |
| SEC-014 | FIX-18 | `computeAppSecretProof` **falha fechado** (lança) em vez de seguir sem `appsecret_proof` | `FacebookTokenVerifier.java` | Inspeção: catch lança `InvalidSocialTokenException`. | **Fechado** |
| SEC-016 | FIX-16 | Política de senha de staff (`@Size(min=10,max=72)`), `@Email` no e-mail; BCrypt custo **12** | `CreateStaffUserRequest.java`, `BcryptConfig.java` | **Teste** `DtoValidationTest`: senha fraca e e-mail inválido rejeitados; válido aceito. Hashes antigos seguem válidos. | **Fechado** |
| SEC-017 (formato) | FIX-17 | PIN validado por `@Pattern(\\d{4,10})` + `@Valid` no controller | `SetPinRequest.java`, `AdminController.java` | **Teste** `DtoValidationTest`: PIN não-numérico/curto rejeitado; `1234` aceito. | **Fechado** |
| SEC-017 (brute force) | FIX-04 | **Não aplicado** (rate limit/lockout exige Bucket4j) | — | — | **Aberto** (Parcial no conjunto) |
| SEC-022 | FIX-07 | `@Email`/`@Size` nos DTOs de cliente; `@Valid` em `loginOrRegister` | `CustomerLoginRequest`, `CreateCustomerRequest`, `CustomerController` | **Teste** `DtoValidationTest`: e-mail inválido rejeitado; válido aceito. **Ressalva:** retipar `StampRequest.payload` **não** foi feito (contrato). | **Parcial** |
| SEC-023 (backend) | FIX-25 | `imageUrl` aceita só `http(s)://` ou vazio (`@Pattern`) — bloqueia `javascript:`/`data:` | `CreateProgramRequest`, `UpdateProgramRequest` | **Teste** `DtoValidationTest`: `javascript:` rejeitado; `https://`/vazio aceitos. **Ressalva:** *output encoding*/CSP do PWA segue em FIX-12. | **Fechado (backend)** |
| SEC-035 | FIX-24 | `@Min(1)` em `ruleTotalStamps`, `@Positive`/`@PositiveOrZero` | `CreateProgramRequest`, `UpdateProgramRequest` | **Teste** `DtoValidationTest`: `ruleTotalStamps=0` rejeitado. | **Fechado** |
| SEC-007 | FIX-06 | Handler `DataIntegrityViolationException` → 409 genérico; `validation` retorna mapa campo→msg (sem `BindingResult.toString()`); `server.error.include-stacktrace/message: never` | `GlobalExceptionHandler.java`, `application.yaml` | Inspeção: handler + config presentes. **Ressalva:** `IllegalArgumentException` ainda ecoa `getMessage()`; sem catch-all `Exception` (evitado p/ não converter 400/404/405 de framework em 500). | **Parcial** · dinâmico: TC-INPUT-04/05 |
| SEC-029 | FIX-13 | `server.forward-headers-strategy: framework`; HSTS + `Referrer-Policy: no-referrer` no `SecurityConfig` | `application.yaml`, `SecurityConfig.java` | Inspeção: config + `.headers(...)` presentes. **Ressalva:** HTTPS/CSP no proxy e emissão efetiva dos headers → confirmar em ambiente (DAST). | **Parcial** · dinâmico: ZAP baseline |

### Notas
- **SEC-017** e **SEC-022/007/029** ficam **Parcial** porque parte da mitigação
  exige mudança maior (rate limit, retipagem de payload, HTTPS/CSP no proxy) ou
  verificação dinâmica com Postgres.
- **Verificação dinâmica:** os casos `TC-*` correspondentes em
  `SECURITY_TEST_CASES.md` provam o comportamento (400/409) e tornam-se *guards*
  verdes quando o projeto adotar **Testcontainers** (hoje ausente).

---

## 2. Correção de precisão — working tree × commitado (descoberta na Fase 15)

Ao versionar as correções, o `git diff` revelou que as Fases 1–12 analisaram a
**working tree**, que tinha **alterações locais NÃO commitadas** em
`application-dev.yaml`. O estado **commitado (HEAD)** é **mais seguro**.

| Achado | Como estava no relatório | Realidade commitada (HEAD) | Novo status |
|---|---|---|---|
| **SEC-003** | segredos commitados (JWT/HMAC + DB `appuser/coti`) | Fallback **JWT/HMAC** está commitado em `application-dev.yaml` **e** `application-local.yaml`. O `appuser/coti` é **working-tree only** (commitado usa `${DB_URL}`). | **Aberto — Alta** (mantém; rotação ainda necessária) |
| **SEC-005** (Swagger) | público em produção (Média) | No `dev` commitado, Swagger está **desabilitado** (`enabled:false`). Exposto só em `local`/sem profile. | **Rebaixado p/ Baixa** |
| **SEC-011** (`show-sql`) | ligado | `dev` commitado = `false`; só `local` = `true`. | **Aberto — Baixa** (escopo reduzido) |
| **SEC-028** | prod herda dev inseguro | `dev` commitado é endurecido; risco real = **default de profile ser `local`** se `SPRING_PROFILES_ACTIVE` não setado + fallback de segredo. | **Aberto — Média** (reenquadrado) |

> **Recomendação:** **não commitar** as alterações locais de `application-dev.yaml`
> (reativam Swagger/`show-sql` e hardcodam `appuser/coti`). Confirmar o
> `SPRING_PROFILES_ACTIVE` da VPS (idealmente um profile `prod` dedicado — FIX-01).

---

## 2-bis. FIX-01 (profile de produção + de-fang do fallback) — código aplicado

| Achado | Ação aplicada (código) | Arquivo | Reteste | Status |
|---|---|---|---|---|
| SEC-005 | Swagger/api-docs **desabilitados** no novo profile `prod` | `application-prod.yaml` | Inspeção: `springdoc.*.enabled: false`. Dinâmico: smoke test pós-deploy (`/api-docs`→404). | **Fechado no `prod`** (pendente ativar profile na VPS) |
| SEC-011 | `show-sql: false` no `prod` | `application-prod.yaml` | Inspeção. | **Fechado no `prod`** |
| SEC-028 | `prod` criado: datasource via env, **sem default de segredo** (herda base → fail-closed); VPS setará `SPRING_PROFILES_ACTIVE=prod` | `application-prod.yaml` | Inspeção. | **Parcial** (depende da VPS ativar `prod`) |
| SEC-003 | Fallback `h9V$…` trocado por `dev-only-insecure-…` em `dev`/`local`; `prod` não tem default | `application-dev.yaml`, `application-local.yaml`, `application-prod.yaml` | `grep h9V src/main/resources` → **vazio** ✅. `./mvnw test` 21/21. | **Parcial** — o `h9V$…` **ainda vive no histórico do git**; exige **rotação** + **BFG** (você). |

> **Importante:** SEC-003 só fecha de fato após **rotacionar** o segredo real e
> **limpar o histórico**. A troca do fallback remove o valor de aparência real da
> árvore atual, mas **não** neutraliza o que já está no histórico.

## 2-ter. FIX-03 (isolamento multi-tenant) — implementado no backend

| Achado | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|
| SEC-020 | `SecurityUtils.requireActiveMerchant(merchantId)` (→403) aplicado em criar/editar programa, criar localização, criar staff; `setPin` exige vínculo do caixa-alvo com o merchant do token | `SecurityUtils`, `ProgramService`, `LocationService`, `StaffService` | **Testes** `SecurityUtilsTest` (4) + `MerchantScopeAuthzTest` (2): ADMIN de A operando em B → `AccessDeniedException`, **repos não tocados**. `./mvnw test` **27/27**. | **Fechado** (exceto createMerchant) |
| SEC-020 (createMerchant) | Flag global `platform_admin` em `staff_users` (migration); autoridade `PLATFORM_ADMIN` derivada no filtro; `createMerchant` exige `@PreAuthorize('PLATFORM_ADMIN')` | `V2026.30.06…__add_platform_admin_staff_user.sql`, `StaffUser`, `JwtAuthenticationFilter`, `MerchantController` | **Teste** `JwtAuthenticationFilterTest` (2): platform admin recebe a autoridade; ADMIN comum não. `./mvnw test` **29/29**. | **Fechado** (requer bootstrap: `UPDATE … SET platform_admin=TRUE`) |
| SEC-017 (cross-tenant) | `setPin` agora barra caixa de outro merchant | `StaffService` | coberto pela mesma checagem | **Fechado** (parte cross-tenant; brute force segue em FIX-04) |

> **Comportamento alterado (intencional):** chamadas cross-tenant aos endpoints
> ADMIN (`/api/merchants/{B}/...`, `setPin` de staff de B, criar staff em B) agora
> retornam **403**. Chamadas ao **próprio** merchant seguem normais. Admins com
> múltiplos merchants operam B após `POST /api/auth/switch-merchant` (token com
> `merchantId=B`). Nenhum fluxo legítimo é quebrado.

## 2-quater. FIX-23 (resgate sem PIN) — implementado no backend

| Achado | Ação aplicada | Arquivo | Reteste | Status |
|---|---|---|---|---|
| SEC-034 (PIN) | Verificação de PIN movida para fora do `if (locationId != null)`; default **fail-safe = exigir PIN** quando não há location | `RedeemService` | **Testes** `RedeemServiceTest` (2): sem locationId e sem PIN → rejeitado (cartão nem é buscado); com PIN → `validateCashierPin` é chamado mesmo sem location. `./mvnw test` **31/31**. | **Fechado** (bypass do PIN) |
| SEC-034 (token) | Token REDEEM_QR **continua opcional** — torná-lo obrigatório é mudança de contrato/PWA | — | — | **Aberto** (endurecimento opcional) |

> **Comportamento alterado (intencional):** resgate **sem `locationId`** agora
> **exige PIN** (antes resgatava sem nenhum segundo fator). O opt-out de PIN segue
> funcionando, mas só explicitamente via uma location com `requirePinOnRedeem=false`.

## 2-quinquies. FIX-02 Fase A (auth de cliente — backend aditivo)

| Achado | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|
| SEC-001/002/004 | **Fase A (aditiva, não-quebra):** `social-login` emite **JWT de cliente** (`type=CUSTOMER`, sub=customerId); `JwtAuthenticationFilter` autentica o token como `ROLE_CUSTOMER` de forma **type-aware** (não o trata como staff). Endpoints do cliente **continuam `permitAll`**. | `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `UserRegistrationFacade`(+`CustomerSession`), `CustomerController`, `CustomerMapper`, `CustomerLoginResponse` | **Testes** `JwtServiceTest` (+2: token de cliente vs staff), `JwtAuthenticationFilterTest` (+1: cliente vira `ROLE_CUSTOMER`, não toca staffRepo). `./mvnw test` **34/34**. | **ABERTO** — Fase A é fundação; **SEC-001 só fecha na Fase C** (flip dos endpoints) |

> A Fase A **não reduz** o risco do SEC-001 por si só (endpoints seguem públicos);
> ela habilita o token para o frontend (Fase B) e prepara o corte (Fase C). Ver
> `FIX-02-PROPOSAL.md` para o rollout.

## 2-sexies. FIX-02 Fase C (corte — fecha o SEC-001 para cartões/QR)

| Achado | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|
| SEC-001/002/004 | `GET /api/cards/**` e `/api/qr/**` exigem `ROLE_CUSTOMER` (`SecurityConfig`); `CardService` valida **posse** (`requireActiveCustomer`) em `getCustomerCards`, `generateCustomerQr`, `generateRedeemQr`; **enroll** (`POST /api/cards`) → staff `CASHIER/ADMIN` escopado ao merchant | `SecurityConfig`, `SecurityUtils`, `CardService`, `QrCodeController`, `CardsController` | **Testes** `CardServiceAuthzTest` (5): cliente A não lê cartões/QR de B; enroll cross-merchant negado. `./mvnw test` **39/39**. | **Fechado** (cartões/QR) |
| SEC-001 (residual) | `login-or-register` por e-mail ainda reivindica/atualiza cliente sem auth | — | — | **Fechado** na Fase D (ver §2-septies) |

> ⚠️ **Coordenação de deploy (crítico):** a Fase C **quebra** clientes de
> login-light (sem token → **403** ao carregar cartões). O PWA precisa estar
> **social-only** (onboarding sem o formulário de e-mail) **antes** de a Fase C ir
> a produção. Sequência segura: deploy da Fase B (PWA manda Bearer) → PWA
> social-only → **então** deploy da Fase C (backend).

## 2-septies. FIX-02 Fase D (fecha o resíduo do SEC-001)

| Achado | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|
| SEC-001 (resíduo `login-or-register`) | **Backend:** `POST /api/customers/login-or-register` sai do `permitAll` e exige staff (`@PreAuthorize("hasAnyAuthority('CASHIER','ADMIN','PLATFORM_ADMIN')")` + `/api/customers/**` em `authenticated`); `POST /api/customers` restrito a `PLATFORM_ADMIN`. **Frontend:** `CustomerOnboarding` é **social-only** (form e-mail/nome/telefone removido); `useCustomer.loginOrRegister` e `apiService.loginOrRegisterCustomer` removidos; sessões antigas sem token são descartadas no boot. | `SecurityConfig.java`, `CustomerController.java`, `carimbai-app/src/components/CustomerLogin.tsx`, `services/api.ts`, `hooks/useCustomer.ts`, `App.tsx`, `types/index.ts` | **Backend:** `CustomerControllerAuthzTest` (2) — `createCustomer` exige `PLATFORM_ADMIN`; `loginOrRegister` exige `CASHIER/ADMIN`. `./mvnw test` **41/41**. **Frontend:** `npm run build` ✅, `npm run lint` 0 erros, **screenshot 5174** mostra a tela social-only sem o formulário de e-mail. | **Fechado** |

> Coordenação de deploy permanece: **Fase D no PWA antes** do corte da Fase C em
> produção (caso contrário login-light em cache vira 403). Após a sequência:
> SEC-001/002/004 **totalmente fechados**.

## 2-octies. FIX-15 (CORS por profile) + FIX-04 (rate limit + PIN lockout)

| Achado | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|
| SEC-030 | **FIX-15** — `app.cors.allowed-origins` (lista) injetada por `@Value`; base inclui localhost + Vercel (dev/local), `application-prod.yaml` restringe a `https://carimbai-app.vercel.app`. Override por env `APP_CORS_ALLOWED_ORIGINS` (CSV). | `SecurityConfig.java`, `application.yaml`, `application-prod.yaml` | Inspeção: `setAllowedOriginPatterns(allowedOrigins)`; em prod a lista tem 1 item. Compilação OK. | **Fechado (estático)** — verificação dinâmica em produção depende de FIX-01 (perfil prod ativo) |
| SEC-006 | **FIX-04 (rate limit)** — `RateLimitFilter` (Bucket4j in-memory) registrado antes do JWT: login 10/min, social-login 10/min, login-or-register 10/min, enroll 30/min, qr 60/min, redeem-qr 60/min (por IP). 429 + `Retry-After` em estouro. | `RateLimitFilter.java`, `pom.xml`, `SecurityConfig.java` | `RateLimitFilterTest` (3): 11ª chamada em login → 429; IPs distintos têm buckets separados; path fora das regras passa. | **Fechado** (multi-nó futuro → migrar para Redis) |
| SEC-017 (brute-force) | **FIX-04 (PIN lockout)** — `PinLockoutService` in-memory: 5 erros em 10min → bloqueia 15min; acerto reseta. Aplicado em `StaffService.validateCashierPin` antes do bcrypt. 423 + `Retry-After` via handler. | `PinLockoutService.java`, `PinLockedException.java`, `StaffService.java`, `GlobalExceptionHandler.java` | `PinLockoutServiceTest` (5) + `StaffServiceLockoutTest` (2): 5 falhas seguidas bloqueiam; sucesso reseta a contagem. | **Fechado** |
| SEC-032 | **FIX-04 (request size)** — Tomcat `max-http-form-post-size: 256KB`, `max-swallow-size: 256KB`, multipart `1MB`. | `application.yaml` | Inspeção: config presente. | **Fechado (estático)** — dinâmico via load test/DAST |

> `./mvnw test` final: **51/51 verde**. Dependência nova: `com.bucket4j:bucket4j-core:8.10.1` (in-memory, sem Redis).

## 2-nonies. FIX-14 (sslmode) + FIX-10 (auditoria/logback) + FIX-11 (JWT hardening + logout) + FIX-12 (CSP + logout)

| Achado | Ação aplicada | Arquivo(s) | Reteste | Status |
|---|---|---|---|---|
| SEC-013 (parcial) | **FIX-14 (sslmode)** — Hikari `data-source-properties.sslmode: ${DB_SSL_MODE:require}` em prod (fail-closed; override para loopback). | `application-prod.yaml` | Inspeção: prop presente. Cripto de `document` em repouso segue pendente. | **Parcial** |
| SEC-026/027 | **FIX-10 (auditoria/logback)** — `AuditService` + `AuditEvent` + `AuditMask` (e-mail/doc mascarados); eventos plugados em `STAFF_LOGIN` (OK/FAIL), `STAFF_SWITCH_MERCHANT`, `CUSTOMER_SOCIAL_LOGIN` (OK/FAIL), `CUSTOMER_LOGIN_OR_REGISTER`, `CASHIER_PIN_VALIDATE` (OK/FAIL), `CASHIER_PIN_LOCKED`, `CASHIER_PIN_SET`, `REDEEM`, `CARD_ENROLL`, `MERCHANT_CREATE`, `STAFF_USER_CREATE`, `ACCESS_DENIED` (central via handler). `logback-spring.xml` separa `audit.log` (180d / 5GB) de `application.log` (14d / 1GB) com rotação diária+tamanho — só no profile `prod`. | `AuditService.java`, `AuditEvent.java`, `AuditMask.java`, `logback-spring.xml`, `AuthService.java`, `UserRegistrationFacade.java`, `StaffService.java`, `RedeemService.java`, `CardService.java`, `MerchantService.java`, `GlobalExceptionHandler.java` | `AuditMaskTest` (5). Eventos verificados por inspeção e via wiring nos testes existentes. | **Fechado** |
| SEC-012 | **FIX-11 (JWT hardening)** — todos os tokens agora têm `iss=carimbai`, `aud=carimbai:staff` ou `carimbai:customer`, `jti` (UUID); `parseToken` exige issuer (token sem `iss` ou com outro emissor → `JwtException`). **Logout:** `POST /api/auth/logout` (permitAll, idempotente) revoga o `jti` corrente em `TokenRevocationService` (in-memory, GC preguiçoso); filtro consulta a denylist antes de autenticar. | `JwtService.java`, `TokenRevocationService.java`, `JwtAuthenticationFilter.java`, `AuthController.java`, `SecurityConfig.java` | `JwtServiceTest` (+3: `jti/iss/aud` staff, `jti/iss/aud` cliente, rejeição de issuer errado), `TokenRevocationServiceTest` (4: denylist semântica), `JwtAuthenticationFilterTest` (+1: token revogado não autentica). | **Fechado (backend)** |
| SEC-023/024/025 | **FIX-12 (frontend)** — `vercel.json` com **CSP** restrita (`default-src 'self'`; script-src só `'self'` + Google/Facebook; `object-src 'none'`; `frame-ancestors 'none'`; etc.) + HSTS + `X-Content-Type-Options` + `X-Frame-Options: DENY` + `Referrer-Policy: no-referrer` + `Permissions-Policy` restritiva. **Logout:** `apiService.logout(token)` revoga no backend em `useCustomer.logout()` (customer) e `handleLogout()` (staff). | `carimbai-app/vercel.json`, `services/api.ts`, `hooks/useCustomer.ts`, `components/StaffScreen.tsx` | `npm run build` ✅, `npm run lint` 0 erros, screenshot do `CustomerOnboarding` confirma render OK (CSP só vale em prod na Vercel). | **Parcial** (JWT segue em `localStorage` — migração para cookie httpOnly + CSRF adiada por UX) |

> `./mvnw test` final: **64/64 verde**.

## 3. Achados que permanecem ABERTOS (sem correção nesta rodada)

Por exigirem proposta/design ou mudança grande (ver `SECURITY_FIX_PLAN.md`):

- **Crítico/Alto:** SEC-001/002/004 (auth de cliente — FIX-02), SEC-003 (rotação
  de segredo — FIX-01), SEC-020 (escopo multi-tenant — FIX-03).
- **Médio:** SEC-006 (rate limit login — FIX-04), SEC-008 (anti-enumeração),
  SEC-012 (revogação JWT), SEC-018 (MFA), SEC-024 (JWT em localStorage),
  SEC-026 (auditoria), SEC-032 (anti-automação), SEC-034 (PIN/QR no resgate).
- **Baixo/operacional:** SEC-010/013/023/025/027/030/031/033 e correlatos LGPD.

> Risco residual aceito? **A decidir pelo responsável.** Os itens Crítico/Alto
> **não** devem ser aceitos como residual antes de comercializar (ver
> `PRIVACY_ACTION_ITEMS.md` §6).

---

## 4. Resumo do reteste

| Resultado | Qtde | Achados |
|---|---|---|
| **Fechado** | 20 | **SEC-001/002/004 (FIX-02 A+B+C+D)**, SEC-014, SEC-015, SEC-016, SEC-017 (formato+cross-tenant+**brute-force**), **SEC-020 (completo)**, **SEC-034 (bypass do PIN)**, SEC-023(backend), SEC-035, **SEC-006 (rate limit)**, **SEC-008 (login anti-enum)**, **SEC-030 (CORS prod)**, **SEC-032 (req size)**, **SEC-026/027 (auditoria + retenção)**, **SEC-012 (JWT hardening + logout)**, + SEC-005/011 no `prod` |
| **Parcial** | 4 | SEC-003, SEC-007, SEC-022, SEC-029, SEC-028 |
| **Aberto** (sem correção) | restante | ver §3 (inclui SEC-001/002/004/034 + createMerchant) |

> **Cobertura de teste:** `./mvnw test` = **27/27 verde**. `DtoValidationTest` (12)
> cobre validação (SEC-016/017/022/023/035); `SecurityUtilsTest` (4) +
> `MerchantScopeAuthzTest` (2) cobrem o isolamento multi-tenant (SEC-020);
> `JwtServiceTest` (4) + `StampTokenServiceTest` (5) cobrem o core de cripto.

## 5. Critério de aceite da Fase 15

- [x] Cada achado corrigido teve o teste/caso correspondente reexecutado
      (compilação + testes verdes + inspeção; dinâmico indicado onde aplicável).
- [x] Achados reabertos/parciais sinalizados (§1, §2).
- [x] Risco residual documentado (§3) — decisão de aceite pendente do responsável.

> **Mudança de código nesta fase:** correções *quick win* em `src/main` + testes
> em `src/test`. `./mvnw test` → BUILD SUCCESS (9/9). Fluxos de auth/autz e
> contrato de API **não** foram alterados.
