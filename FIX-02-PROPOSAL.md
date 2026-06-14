# Proposta FIX-02 — Autenticação do cliente e escopo de dados (SEC-001/002/004)

> Avalia o impacto nos **dois repositórios** (`carimbai` backend + `carimbai-app`
> PWA). É a maior obra de segurança restante e a única Crítica em aberto.
> Referências: `SECURITY_REPORT.md` (SEC-001/002/004/021), `SECURITY_FIX_PLAN.md`
> (FIX-02), `PRIVACY_ACTION_ITEMS.md` (PRV-T01).
>
> ## Status do rollout
> - ✅ **Fase A (backend aditivo) — IMPLEMENTADA** (2026-06-14). `social-login`
>   emite JWT de cliente; filtro autentica o token de cliente como `ROLE_CUSTOMER`
>   (type-aware, não confunde com staff); endpoints **ainda `permitAll`** (não-quebra).
>   `./mvnw test` → **34/34**. **SEC-001 continua ABERTO** — fecha só na Fase C.
> - ✅ **Fase B (frontend) — IMPLEMENTADA** (2026-06-14). `carimbai-app` passa a
>   guardar o `token` (já vem no `CustomerLoginResponse`) e a enviar
>   `Authorization: Bearer` (opcional) nas chamadas do cliente
>   (`getCustomerCards`/`getCardQR`/`getRedeemQR`/`subscribePush`), **mantendo as
>   URLs atuais**. `npm run build` ✅ / `npm run lint` ✅ (0 erros). Não-quebra:
>   login-light (sem token) segue funcionando; backend ainda `permitAll`.
> - ✅ **Fase C (backend, corte) — IMPLEMENTADA** (2026-06-14). `GET /api/cards/**`
>   e `/api/qr/**` exigem `ROLE_CUSTOMER`; `CardService` valida **posse**; enroll →
>   staff escopado ao merchant. URLs mantidas (sem `/cards/me`). `./mvnw test`
>   **39/39**. **SEC-001/002/004 fechados para cartões/QR.**
>   - ⚠️ **Deploy:** a Fase C **quebra** login-light (sem token → 403 nos cartões).
>     O PWA precisa estar **social-only** antes do corte em produção.
>     **Residual:** retirar/`demote` do `login-or-register` (onboarding social-only).
> - ✅ **Fase D (fecha o resíduo) — IMPLEMENTADA** (2026-06-14). **Backend:**
>   `POST /api/customers/login-or-register` agora exige staff
>   (`@PreAuthorize("hasAnyAuthority('CASHIER','ADMIN','PLATFORM_ADMIN')")` +
>   `/api/customers/**` em `authenticated`); `POST /api/customers` restrito a
>   `PLATFORM_ADMIN`. **Frontend:** `CustomerOnboarding` é **social-only**
>   (formulário de e-mail/nome/telefone removido); `useCustomer.loginOrRegister`
>   e `apiService.loginOrRegisterCustomer` removidos; sessões antigas sem token
>   são descartadas no boot. `./mvnw test` **41/41**, `npm run build` ✅,
>   `npm run lint` 0 erros, verificação visual no preview confirma a tela
>   social-only. **SEC-001 totalmente fechado.**
>   - ⚠️ Sequência de deploy: PWA (Fase D) antes do corte de backend (Fase D).
>     `login-or-register` agora exige Bearer de staff — chamadas sem token (PWA
>     antigo em cache) recebem **401/403**, que é o comportamento desejado.

## 1. Problema

Hoje **o cliente não tem autenticação**. A "sessão" é o `CustomerLoginResponse`
(com `customerId`) guardado no `localStorage`; **nenhum token** é emitido. As
chamadas do cliente vão **sem `Authorization`**, com o id na URL:

| Chamada (frontend `api.ts`) | Endpoint | Hoje |
|---|---|---|
| `getCustomerCards(customerId)` | `GET /api/cards/customer/{id}` | público, id na URL |
| `getCardQR(cardId)` | `GET /api/qr/{cardId}` | público |
| `getRedeemQR(cardId)` | `GET /api/cards/{cardId}/redeem-qr` | público |
| `enrollCustomer(programId, customerId)` | `POST /api/cards` | público |
| `subscribePush(customerId, …)` | `POST /api/notifications/subscribe` | público |
| `loginOrRegisterCustomer(payload)` | `POST /api/customers/login-or-register` | **sem credencial** |

Consequência (SEC-001): qualquer um, sem autenticar, lê/altera dados de qualquer
cliente trocando o id, e emite QR de selo/resgate de qualquer cartão.

## 2. A decisão central — qual é a "autenticação do cliente"?

Para escopar dados ao titular, o backend precisa **emitir um token de cliente**
após um **evento de autenticação real**. Hoje existem dois caminhos de entrada:

- **`social-login`** (Google/Apple/Facebook) — o token do provedor **é verificado
  no servidor** (assinatura/issuer/audience/expiração). **Isto é autenticação real.**
- **`login-or-register`** (nome/e-mail/telefone) — **não é autenticação**: qualquer
  um reivindica qualquer e-mail. É a origem do SEC-001.

**Opções:**

| Opção | Descrição | Viabilidade |
|---|---|---|
| **A (recomendada)** | Emitir **JWT de cliente no `social-login`**. Tornar os recursos do cliente autenticados e escopados ao token. **Demote** o `login-or-register` (vira ação de balcão do staff, não self-login). | Pronta — o PWA já tem login social |
| B | Adicionar **OTP** (e-mail/SMS) ao `login-or-register` para virar auth real | **Inviável agora** — não há provedor de e-mail/SMS (Fase 11). Futuro (liga a PRV/FIX-21) |
| C | Manter o `login-or-register` como está | **Não corrige** o SEC-001 |

> **Recomendação: Opção A.** Social login passa a emitir um JWT de cliente; o
> "login light" por e-mail deixa de dar acesso autenticado (pode permanecer como
> *enrollment* de balcão feito pelo staff). OTP por e-mail fica como evolução.

## 3. Mudanças no BACKEND (`carimbai`)

1. **Emitir JWT de cliente** após `social-login` (e, no futuro, OTP):
   - Estender `JwtService` (ou um análogo) para emitir token com `sub = customerId`
     e claim `type = "CUSTOMER"` (distingue do token de staff). TTL próprio.
   - `CustomerLoginResponse` passa a incluir `token`.
2. **Autenticar o token de cliente** no `JwtAuthenticationFilter` (ou um filtro
   irmão): se `type=CUSTOMER`, carregar `Customer`, setar principal de cliente +
   autoridade `ROLE_CUSTOMER`; senão, fluxo de staff atual.
3. **Escopar os recursos ao cliente do token** (fim do IDOR):
   - `GET /api/cards/me` (novo) → deriva `customerId` do token (substitui
     `/cards/customer/{id}`; **não** confiar em id da URL).
   - `GET /api/qr/{cardId}` e `GET /api/cards/{cardId}/redeem-qr` → validar que o
     cartão pertence ao cliente autenticado.
   - `POST /api/notifications/subscribe` → `customerId` do token.
4. **`SecurityConfig`:** mover `/api/cards/**` e `/api/qr/**` de `permitAll` para
   `authenticated` (com `ROLE_CUSTOMER`); manter `GET /api/merchants/*/programs`
   público.
5. **Enroll** (`POST /api/cards`): hoje é disparado quando o **staff** escaneia o
   QR `CUSTOMER_ID`. Reposicionar como **ação de staff autenticado** (CASHIER/ADMIN
   do merchant), escopando ao merchant — **não** deixar público.
6. **`login-or-register`:** demote — remover do caminho self-service do cliente,
   ou restringir a staff. (Decisão de produto.)

> Reaproveita padrões já existentes: filtro JWT recarregando a entidade, escopo
> por `SecurityUtils`, exceções → handler central.

## 4. Mudanças no FRONTEND (`carimbai-app`)

> Regra do app: **toda chamada passa por `services/api.ts`** — concentra a maior
> parte da mudança ali.

1. **`hooks/useCustomer.ts`:** guardar o **token** do cliente (vindo do
   `social-login`) além do `customerId`; expor para os componentes; limpar no
   `logout`. (Hoje guarda só o `CustomerLoginResponse`.)
2. **`services/api.ts`:**
   - `socialLoginCustomer` → resposta passa a ter `token`.
   - `getCustomerCards(customerId)` → **`getMyCards(token)`** chamando
     `GET /cards/me` com `Authorization: Bearer`.
   - `getCardQR(cardId, token)`, `getRedeemQR(cardId, token)` → enviar Bearer.
   - `subscribePush(token, …)` → enviar Bearer.
   - `enrollCustomer` → **migra para o fluxo de staff** (StaffScreen, com token de staff).
3. **`components/CustomerScreen.tsx`:** receber/usar o token (de `useCustomer`)
   nas chamadas; `customerId` deixa de ser a fonte de verdade (cartões vêm de `/me`).
4. **`components/CustomerLogin.tsx` / `App.tsx`:** onboarding passa a ser
   **social-login** (Google/Apple/Facebook); o formulário de e-mail "login light"
   é removido do self-service (ou vira fallback sem acesso autenticado).
5. **`types/index.ts`:** `CustomerLoginResponse` ganha `token`; novos tipos para `/me`.

## 5. Impacto por endpoint (resumo)

| Endpoint | Antes | Depois | Quebra? |
|---|---|---|---|
| `social-login` | retorna dados | retorna dados **+ token** | aditivo |
| `/cards/customer/{id}` | público | **removido** → `GET /cards/me` (token) | **sim** (frontend muda) |
| `/qr/{cardId}` | público | autenticado + dono | **sim** |
| `/cards/{cardId}/redeem-qr` | público | autenticado + dono | **sim** |
| `POST /cards` (enroll) | público | **staff autenticado** | **sim** (re-home) |
| `notifications/subscribe` | público | autenticado (token) | **sim** |
| `login-or-register` | self-login | demote/staff-only | **sim** (UX) |
| `GET /merchants/*/programs` | público | **continua público** | não |
| Fluxo de **staff** (login/stamp/redeem/switch) | autenticado | **inalterado** | não |

## 6. Rollout faseado (evita cutover quebrando o app)

Como backend e frontend deployam separados (VPS × Vercel), um corte seco quebra
o app se sair fora de sincronia. Sugiro:

- **Fase A (backend aditivo, não-quebra):** `social-login` passa a **também**
  retornar `token`; endpoints do cliente **aceitam** o Bearer mas ainda funcionam
  sem ele (`permitAll` mantido). Deploy backend.
- **Fase B (frontend):** PWA passa a logar via social, guardar o token e **enviar**
  o Bearer + usar `/cards/me`. Deploy Vercel.
- **Fase C (backend, corte):** flip de `/cards/**` e `/qr/**` para `authenticated`
  + escopo de dono; enroll → staff; demote do `login-or-register`. Deploy backend.
- **Fase D:** validar (testes A-vs-B `TC-AUTHZ-05..08` viram verdes) e remover o código morto.

Cada fase é testável; só a Fase C "fecha" o SEC-001.

## 7. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Deploy fora de sincronia quebra o app | Rollout faseado (§6); Fase C só depois do PWA novo em produção |
| Cliente perde onboarding por e-mail | Decisão de produto; destacar login social; OTP como evolução (PRV) |
| Token de cliente no `localStorage` (SEC-024) | Aceitar curto prazo + endurecer XSS (CSP/FIX-12); cookie httpOnly traria CSRF |
| Enroll/push precisam de novo "lar" | Enroll → staff; push → token de cliente (mapeado em §3/§4) |
| `SocialProvider` no PWA só tem Google/Facebook (Apple existe no backend) | Confirmar provedores ativos antes de tornar social o caminho único |

## 8. Decisões necessárias (produto/segurança)

1. **Opção A** (social-login emite JWT + demote do login-by-email) — confirma?
2. **OTP por e-mail** no futuro (exige provedor de e-mail) — entra no roadmap?
3. **Enroll** vira ação de **staff** (CASHIER/ADMIN do merchant) — ok?
4. Provedores sociais ativos em produção (Google? Apple? Facebook?) — quais manter?

## 9. Esforço e split

| Parte | Onde | Tamanho |
|---|---|---|
| JWT de cliente + filtro + escopo `/me`/ownership | `carimbai` (backend) | Médio |
| Re-home enroll (staff) + flip SecurityConfig | `carimbai` | Médio |
| Token no `useCustomer` + `api.ts` + telas | `carimbai-app` | Médio |
| Onboarding social-only / remover login-by-email | `carimbai-app` | Pequeno-Médio |
| Testes (backend A-vs-B; e2e do PWA) | ambos | Médio |

> **Próximo passo se aprovado:** começo pela **Fase A** (backend aditivo, não
> quebra nada) com testes, e só avançamos para B/C quando você validar. Nada
> aqui foi implementado.
