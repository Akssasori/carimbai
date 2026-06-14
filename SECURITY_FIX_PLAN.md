# Plano de Correção Priorizado — carimbai

> Derivado de `SECURITY_REPORT.md` (SEC-001…SEC-035). **Priorizado por severidade**:
> as correções **Críticas e Altas vêm primeiro** (§1), depois Média/Baixa (§3).
> Estado das correções em `RETEST_REPORT.md`.
>
> **Regra de ouro:** mudanças em **autenticação, autorização, banco ou contrato de
> API** são **apenas propostas** aqui — **não implementadas** sem aprovação. As
> correções **de baixo risco com teste** já foram aplicadas (§4).

## Legenda
- **Tipo:** 🟢 baixa/segura (aplicável) · 🟡 média · 🔴 grande/impacto funcional (**propor antes**).
- **Risco de quebra:** chance de afetar fluxo existente.

---

## 1. PRIORIDADE MÁXIMA — Críticas e Altas (🔴 propor antes de implementar)

> Nenhuma destas foi implementada: todas tocam auth/autz/contrato/segredo. São as
> **bloqueantes para comercializar**.

| FIX | Sev. | Achado | Correção proposta | Arquivos | Risco | Status |
|---|---|---|---|---|---|---|
| **FIX-02** | 🔴 Crítica | SEC-001/002/004/021 | **Autenticar o cliente** (sessão/JWT próprio pós social-login ou OTP) e **escopar `/api/cards/**`, `/api/qr/**`, `login-or-register` ao titular** | `SecurityConfig`, `CardsController`, `QrCodeController`, `CardService`, novo fluxo de sessão de cliente, **frontend** | **Alto** (contrato + PWA) | **Proposto** |
| **FIX-01** | 🔴 Alta | SEC-003 (+028/005/011) | **Rotacionar** JWT/HMAC/DB; **remover fallbacks** de segredo; criar **`application-prod.yaml`** (Swagger/`show-sql` off, sem default sensível); fixar `SPRING_PROFILES_ACTIVE=prod`; limpar histórico git (BFG) | `application*.yaml`, novo `application-prod.yaml`, env/GitHub Secrets, systemd | Médio (rotação invalida JWTs) | **Proposto** |
| **FIX-03** | 🔴 Alta | SEC-020 (+017 cross-tenant) | **Escopar endpoints ADMIN ao merchant do token** (validar `merchantId(path/body) == token`); `setPin` só para staff do mesmo merchant; papel `PLATFORM_ADMIN` para `createMerchant` | `MerchantController`/services, `StaffService`, `AdminController`, `SecurityUtils` | **Alto** (autorização) | **Proposto** |
| **FIX-23** | 🟡 Alta* | SEC-034 | Resgate: exigir PIN por **política de merchant/programa** (não por `locationId`) e/ou **token REDEEM_QR obrigatório** | `RedeemService`, `RedeemRequest`, `RedeemController` | Médio-Alto (fluxo de resgate/PWA) | **Proposto** |

\* SEC-034 é Média no relatório, mas listada aqui por ser **impacto financeiro direto** e tocar o fluxo de resgate.

**Sequência sugerida para o bloco crítico:** FIX-01 (destrava segredo/profile) → FIX-03 (isolamento entre lojistas) → FIX-02 (auth de cliente, maior obra, coordena com o PWA) → FIX-23 (resgate).

---

## 2. Próximas (Médias — 🟡, propor/planejar)

| FIX | Achado | Correção | Arquivos | Risco | Status |
|---|---|---|---|---|---|
| FIX-04 | SEC-006/017/032 | Rate limiting/anti-automação (login, cadastro, social, cards/qr); lockout de PIN; limite de tamanho de requisição | filtro/bucket (Bucket4j), `RedeemService`/`StaffService`, config | Médio | Proposto |
| FIX-05 | SEC-018 | MFA (TOTP) para `ADMIN` | `staff_users`(+col), `AuthService`, **frontend** | Alto | Proposto |
| FIX-08 | SEC-008 | Login anti-enumeração (respostas uniformes p/ inexistente/inativo/sem vínculo) | `AuthService`, `GlobalExceptionHandler` | Médio (muda corpo de erro/contrato) | **Proposto** (auth) |
| FIX-11 | SEC-012 | JWT: `iss`/`aud`, TTL menor, revogação/idle, logout | `JwtService`, filtro, `AuthService`, **frontend** | Médio-Alto | Proposto |
| FIX-12 | SEC-023/024/025 | Frontend: tirar JWT/PII do `localStorage`; *output encoding*; CSP do PWA | `../carimbai-app` | Médio (frontend) | Proposto (outro repo) |
| FIX-14 | SEC-013 | Cripto/pseudonimização de `document` em repouso; `sslmode=require` no DB | entidades/migrations, config, infra | Médio (migração) | Proposto |
| FIX-10 | SEC-026/027 | Logging de auditoria + gestão de logs (retenção/rotação/acesso/mascaramento) | novo `audit`/logback, config | Baixo | Proposto |

---

## 3. Baixas / operacionais

| FIX | Achado | Correção | Risco | Status |
|---|---|---|---|---|
| FIX-15 | SEC-030 | CORS por profile (só origem do PWA em prod) | Baixo | **Adiado** (depende de FIX-01) |
| FIX-19 | SEC-031 | Backups documentados, criptografados, restauração testada | — | Proposto (operacional) |
| FIX-20 | SEC-010 | Docker não-root, alinhar porta, fixar imagens | Baixo (dev) | Proposto |
| FIX-21 | SEC-019 | Reset/troca de senha seguro (quando houver provedor de e-mail) | — | Futuro |
| FIX-22 | SEC-033 | Paginação com tamanho máximo nas listagens | Baixo (muda formato de lista = contrato) | **Proposto** (contrato) |

---

## 4. ✅ Aplicadas (baixo risco, com teste) — Fases 14/15

Implementadas em `src/main` **sem** tocar auth/autz/contrato; cobertas por testes
unitários (`./mvnw test` → **BUILD SUCCESS, 21/21**). Detalhe e ressalvas em `RETEST_REPORT.md`.

| FIX | Achado | Mudança | Teste | Status |
|---|---|---|---|---|
| FIX-09 | SEC-015 | Log do Facebook só `statusCode` (sem PII) | inspeção | **Fechado** |
| FIX-18 | SEC-014 | `appsecret_proof` falha-fechado | inspeção | **Fechado** |
| FIX-16 | SEC-016 | Política de senha staff + `@Email` + BCrypt(12) | `DtoValidationTest` | **Fechado** |
| FIX-17 | SEC-017 (formato) | PIN `@Pattern(\d{4,10})` + `@Valid` | `DtoValidationTest` | **Fechado** |
| FIX-24 | SEC-035 | `@Min(1)`/`@Positive` no programa | `DtoValidationTest` | **Fechado** |
| FIX-07 | SEC-022 | `@Email`/`@Size`/`@Valid` nos DTOs de cliente | `DtoValidationTest` | **Parcial** (payload de stamp não retipado) |
| FIX-25 | SEC-023 (backend) | `imageUrl` só `http(s)`/vazio (`@Pattern`) | `DtoValidationTest` | **Fechado** (frontend/CSP segue em FIX-12) |
| FIX-06 | SEC-007 | Handler `DataIntegrityViolation`→409; validação em mapa; `include-stacktrace/message: never` | inspeção | **Parcial** (sem catch-all `Exception`) |
| FIX-13 | SEC-029 | `forward-headers-strategy` + HSTS + `Referrer-Policy` | inspeção | **Parcial** (HTTPS/CSP no proxy) |

> **Por que só estas:** as demais quebram autenticação, autorização, banco ou
> contrato de API e, conforme a regra de ouro, **só estão propostas** (§1–§3).

---

## 5. Rastreio

Cada `FIX-xx` referencia os `SEC-xxx`. Ao implementar, atualizar o `Status` aqui,
no `SECURITY_REPORT.md` e registrar o reteste em `RETEST_REPORT.md`. Casos de teste
de regressão em `SECURITY_TEST_CASES.md` (tornam-se *guards* verdes após cada FIX).
