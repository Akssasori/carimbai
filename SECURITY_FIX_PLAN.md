# Plano de Correção Priorizado — carimbai

> Gerado na **Fase 10** do `SECURITY_LGPD_PLAN.md`, consolidando os 30 achados de
> `SECURITY_REPORT.md` (SEC-001…SEC-031) em itens de correção (`FIX-xx`),
> priorizados e com risco de quebra. **Nada foi implementado** — este é o plano.
>
> Regra de ouro do plano: correções com impacto em **autenticação, autorização,
> banco ou contrato de API** são **propostas antes** de implementar. Correções
> **pequenas e seguras** podem ser aplicadas mediante sua autorização.

## Legenda
- **Prioridade:** P0 (antes de comercializar / crítico) · P1 (alta) · P2 (média) · P3 (baixa/operacional).
- **Tipo:** 🟢 pequena e segura · 🟡 média · 🔴 grande/impacto funcional (**propor antes**).
- **Risco de quebra:** chance de afetar fluxo existente se aplicado sem cuidado.

---

> **Atualização (Fase 15):** aplicados os *quick wins* **FIX-06, FIX-07, FIX-09,
> FIX-13, FIX-16, FIX-17, FIX-18, FIX-24** (`./mvnw test` → BUILD SUCCESS 9/9).
> Status e ressalvas por achado em `RETEST_REPORT.md`. **FIX-15 (CORS por profile)
> adiado** — depende do profile de produção (FIX-01). Demais itens 🔴 seguem como
> proposta.

## 1. Matriz de correção

| FIX | Prioridade | Correção | Achados | Arquivos afetados (prováveis) | Tipo | Risco de quebra | Status |
|---|---|---|---|---|---|---|---|
| FIX-01 | **P0** | Rotacionar JWT/HMAC/DB, remover fallbacks de segredo e criar **profile de produção** (sem default sensível; Swagger/show-sql off); limpar histórico do git | SEC-003, SEC-028, SEC-005, SEC-011 | `application*.yaml`, novo `application-prod.yaml`, env/GitHub Secrets, serviço systemd | 🟡 | Médio (rotação invalida JWTs; relogin) | Proposto |
| FIX-02 | **P0** | **Autenticar o cliente** e escopar `/api/cards/**`, `/api/qr/**`, `login-or-register` ao dono | SEC-001, SEC-002, SEC-004, SEC-021 | `SecurityConfig`, `CardsController`, `QrCodeController`, `CardService`, novo mecanismo de sessão de cliente, **frontend** | 🔴 | **Alto** (muda contrato e PWA) | **Propor antes** |
| FIX-03 | **P0** | Escopar endpoints ADMIN ao merchant do token; `setPin` só para staff do mesmo merchant; papel `PLATFORM_ADMIN` para `createMerchant` | SEC-020, SEC-017(cross-tenant) | `MerchantController`/services, `StaffService`, `AdminController`, `SecurityUtils` | 🔴 | **Alto** (autorização) | **Propor antes** |
| FIX-04 | **P1** | Rate limiting/anti-automação em login, cadastro, social-login, cards/qr; lockout de PIN; limite de tamanho de requisição | SEC-006, SEC-017, SEC-032 | filtro/bucket (ex.: Bucket4j), `RedeemService`/`StaffService`, `application-prod.yaml` | 🟡 | Médio | Proposto |
| FIX-05 | **P1** | MFA (TOTP) para contas `ADMIN` | SEC-018 | `staff_users` (+coluna), `AuthService`, novo fluxo de login, **frontend** | 🔴 | **Alto** (login/PWA) | **Propor antes** |
| FIX-06 | **P1** | Higienizar erros: mensagens genéricas + handler `Exception` e `DataIntegrityViolationException`; `include-stacktrace/message=never` | SEC-007 | `GlobalExceptionHandler`, `application-prod.yaml` | 🟢 | Baixo | Pronta p/ aplicar |
| FIX-07 | **P1** | Validação de entrada: `@Email`/`@Size`/`@Pattern`/`@Valid` cascateado; tipar `StampRequest.payload` | SEC-022 | DTOs de cliente/admin, `CustomerController`, `StampRequest` | 🟢 | Baixo (passa a rejeitar 400 onde hoje 500/aceita) | Pronta p/ aplicar |
| FIX-08 | **P1** | Login anti-enumeração: respostas uniformes para usuário inexistente/inativo/sem vínculo | SEC-008 | `AuthService`, `GlobalExceptionHandler` | 🟡 | Baixo-Médio (muda corpo de erro) | Proposto |
| FIX-09 | **P2** | Remover PII do log do Facebook (`statusCode` em vez de `body`) | SEC-015 | `FacebookTokenVerifier.java:57` | 🟢 | **Nenhum** | **Quick win** |
| FIX-10 | **P2** | Logging de auditoria (eventos mínimos) + gestão de logs (retenção/rotação/acesso/mascaramento) | SEC-026, SEC-027 | novo `audit`/logback, `application-prod.yaml`, serviços | 🟡 | Baixo | Proposto |
| FIX-11 | **P2** | JWT hardening: `iss`/`aud`, TTL menor, revogação/idle, logout | SEC-012 | `JwtService`, `JwtAuthenticationFilter`, `AuthService`, **frontend** | 🔴 | Médio-Alto | **Propor antes** |
| FIX-12 | **P2** | Frontend: tirar JWT/PII do `localStorage`, validar esquema de `imageUrl`, *output encoding*, CSP do PWA | SEC-024, SEC-025, SEC-023 | `../carimbai-app` (`api.ts`, `useCustomer.ts`, componentes) | 🟡 | Médio (frontend) | Proposto (outro repo) |
| FIX-13 | **P2** | Headers de segurança + `forward-headers-strategy` + HTTPS/HSTS no proxy; `Referrer-Policy` | SEC-029 | `SecurityConfig`, `application-prod.yaml`, proxy/VPS | 🟢 | Baixo | Pronta p/ aplicar (app) + infra |
| FIX-14 | **P2** | Cripto/pseudonimização de `document` em repouso (ou cripto de disco) + `sslmode=require` no DB de produção | SEC-013 | entidades/migrations, `application-prod.yaml`, infra DB | 🟡 | Médio (migração de dados) | Proposto |
| FIX-15 | **P2** | CORS por profile (só origem do PWA em produção) | SEC-030 | `SecurityConfig`, `application-prod.yaml` | 🟢 | Baixo | Pronta p/ aplicar |
| FIX-16 | **P3** | Política de senha de staff (mínimo/complexidade; avaliar BCrypt(12)) | SEC-016 | `CreateStaffUserRequest`, `BcryptConfig` | 🟢 | Baixo (cadastros novos exigem senha forte) | Pronta p/ aplicar |
| FIX-17 | **P3** | Validar formato do PIN (`@Pattern`/`@Valid`) | SEC-017 (formato) | `SetPinRequest`, `AdminController` | 🟢 | Baixo | Pronta p/ aplicar |
| FIX-18 | **P3** | `appsecret_proof` do Facebook obrigatório quando configurado | SEC-014 | `FacebookTokenVerifier` | 🟢 | Baixo | Pronta p/ aplicar |
| FIX-19 | **P3** | Backups documentados, criptografados e com restauração testada | SEC-031 | infra/VPS, docs | 🧭 | — (operacional) | Proposto |
| FIX-20 | **P3** | Hardening de Docker (não-root, alinhar porta, fixar imagens) — só se conteinerizar prod | SEC-010 | `Dockerfile`, `docker-compose.yaml` | 🟢 | Baixo (dev) | Proposto |
| FIX-21 | **Futuro** | Reset/troca de senha seguro (token único, expira, genérico, rate limit) | SEC-019 | novo fluxo + provedor de e-mail | 🔴 | — (feature nova) | Futuro |
| FIX-22 | **P3** | Paginação com tamanho máximo nas listagens (e em futuras) | SEC-033 | `CardService`, `ProgramService`, repos, controllers | 🟢 | Baixo (muda formato de resposta de lista) | Proposto |
| FIX-23 | **P1** | Resgate: exigir PIN com base no merchant/programa (não em `locationId`) e/ou token REDEEM_QR obrigatório | SEC-034 | `RedeemService`, `RedeemRequest`, `RedeemController` | 🔴 | Médio-Alto (fluxo de resgate/PWA) | **Propor antes** |
| FIX-24 | **P3** | Validação de regra de programa (`@Min(1)` em `ruleTotalStamps`, etc.) | SEC-035 | `CreateProgramRequest`/`UpdateProgramRequest` | 🟢 | Baixo | Pronta p/ aplicar |

---

## 2. Quick wins — pequenas e seguras (posso aplicar mediante autorização)

Sem impacto em autenticação/autorização/contrato; reduzem risco com baixo custo:

- **FIX-09** — remover PII do log do Facebook *(risco nenhum)*.
- **FIX-06** — handler de erro genérico + `DataIntegrityViolationException` + `include-*=never`.
- **FIX-07** — validação de entrada (`@Email`/`@Size`/`@Valid`).
- **FIX-13** — headers (`forward-headers-strategy`, `Referrer-Policy`, HSTS no app).
- **FIX-15** — CORS por profile.
- **FIX-16/17** — política de senha e formato de PIN.
- **FIX-18** — `appsecret_proof` obrigatório.

> Parte do **FIX-01** (criar `application-prod.yaml` com Swagger/show-sql off e
> **sem** default de segredo) é segura no código; a **rotação** e a configuração
> das env/GitHub Secrets dependem de você.

## 3. Exigem proposta/design antes (impacto funcional)

- **FIX-02** (autenticação de cliente) — redesenha o contrato cliente↔API e o PWA.
- **FIX-03** (escopo multi-tenant ADMIN) — muda regras de autorização.
- **FIX-05** (MFA admin) — novo passo de login + tela.
- **FIX-11** (revogação/refresh/logout do JWT) — muda ciclo de sessão.
- **FIX-14** (cripto em repouso) — migração de dados existentes.

## 4. Sequência recomendada

1. **Agora (quick wins):** FIX-09, FIX-06, FIX-07, FIX-13, FIX-15, FIX-16, FIX-17, FIX-18.
2. **P0 de segredo/infra:** FIX-01 (rotação + profile prod) — destrava SEC-003/005/011/028 de uma vez.
3. **P0 de isolamento (com proposta):** FIX-03 (multi-tenant) → FIX-02 (auth de cliente).
4. **P1:** FIX-04 (rate limit) → FIX-08 (anti-enumeração) → FIX-05 (MFA, proposta).
5. **P2:** FIX-10 (auditoria) → FIX-11 (JWT) → FIX-12 (frontend) → FIX-14 (cripto/TLS).
6. **P3/operacional:** FIX-19 (backups), FIX-20 (docker), FIX-21 (reset — feature).

> Dependências: FIX-02/FIX-11/FIX-05 impactam o **frontend** (`../carimbai-app`)
> e devem ser coordenados com aquele repositório. FIX-01 deve preceder qualquer
> deploy de produção que ainda dependa do segredo hardcoded.

## 5. Rastreio

Cada `FIX-xx` referencia os `SEC-xxx` correspondentes. Ao implementar, atualizar
o `Status` aqui e no `SECURITY_REPORT.md`, e registrar o reteste na **Fase 15**
(`RETEST_REPORT.md`). Casos de teste de regressão em `SECURITY_TEST_CASES.md`.
