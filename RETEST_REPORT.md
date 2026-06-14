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
| SEC-001 (residual) | `login-or-register` por e-mail ainda reivindica/atualiza cliente sem auth | — | — | **Aberto** — fechar com onboarding social-only no PWA + remover/restringir o endpoint |

> ⚠️ **Coordenação de deploy (crítico):** a Fase C **quebra** clientes de
> login-light (sem token → **403** ao carregar cartões). O PWA precisa estar
> **social-only** (onboarding sem o formulário de e-mail) **antes** de a Fase C ir
> a produção. Sequência segura: deploy da Fase B (PWA manda Bearer) → PWA
> social-only → **então** deploy da Fase C (backend).

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
| **Fechado** | 12 | **SEC-001/002/004 (cartões/QR, FIX-02 A+B+C)**, SEC-014, SEC-015, SEC-016, SEC-017(formato+cross-tenant), **SEC-020 (completo)**, **SEC-034 (bypass do PIN)**, SEC-023(backend), SEC-035, + SEC-005/011 no `prod` |
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
