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
| **Fechado** | 6 | SEC-014, SEC-015, SEC-016, SEC-017(formato), SEC-023(backend), SEC-035 |
| **Parcial** | 3 | SEC-007, SEC-022, SEC-029 |
| **Reenquadrado** (precisão) | 3 | SEC-005 (↓Baixa), SEC-011, SEC-028 |
| **Aberto** (sem correção) | restante | ver §3 (inclui todos os Crítico/Alto) |

> **Cobertura de teste:** `DtoValidationTest` (12 casos) torna executável a
> verificação de SEC-016/017/022/023/035 — antes só estática. Total `./mvnw test`
> = **21/21 verde**.

## 5. Critério de aceite da Fase 15

- [x] Cada achado corrigido teve o teste/caso correspondente reexecutado
      (compilação + testes verdes + inspeção; dinâmico indicado onde aplicável).
- [x] Achados reabertos/parciais sinalizados (§1, §2).
- [x] Risco residual documentado (§3) — decisão de aceite pendente do responsável.

> **Mudança de código nesta fase:** correções *quick win* em `src/main` + testes
> em `src/test`. `./mvnw test` → BUILD SUCCESS (9/9). Fluxos de auth/autz e
> contrato de API **não** foram alterados.
