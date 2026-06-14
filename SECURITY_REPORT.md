# Relatório de Segurança e LGPD — carimbai (backend)

> Documento gerado pela execução da **Fase 1 — Reconhecimento** do
> `SECURITY_LGPD_PLAN.md`. Análise **estática, sem alteração de código** e sem
> testes ativos/dinâmicos. Os achados abaixo são observações de revisão de
> código; a confirmação por exploração controlada pertence às fases seguintes
> (5–13) e depende do preenchimento das Regras de Engajamento (seção 2.1 do
> plano), hoje em branco (`PREENCHER`).

## 1. Escopo

- **Aplicação:** carimbai — API de cartão de fidelidade (selos/carimbos via QR).
- **Repositório:** `C:\pessoal\carimbai` (git presente; branch atual).
- **Ambiente analisado:** código-fonte local (nenhum ambiente em execução foi testado).
- **Data:** 2026-06-13.
- **Responsável:** revisão automatizada (Claude Code) — `diniz3003@gmail.com`.
- **Fase executada:** Fase 1 (reconhecimento). Demais fases pendentes.
- **Regras de Engajamento (RoE):** **não preenchidas** no plano. Nenhum teste
  ativo foi executado. Antes das Fases 5–13 (testes dinâmicos), preencher a
  seção 2.1 e definir ambiente/janela autorizados.

---

## 2. Resumo executivo

Achados iniciais identificados na revisão estática (severidade preliminar):

| Severidade | Quantidade |
|---|---:|
| Crítica | 1 |
| Alta | 3 |
| Média | 13 |
| Baixa | 14 |
| Informativa | 3 |

> Total: 34 achados rastreados (SEC-001 a SEC-035; SEC-021 é referência cruzada
> de SEC-001/002/004, não contabilizada na soma). Atualizado até a **Fase 12**.

> ### ⚠️ Correção de precisão (descoberta na Fase 15) — working tree × commitado
>
> As Fases 1–12 analisaram a **working tree**, que contém **alterações locais
> NÃO commitadas** em `src/main/resources/application-dev.yaml` (setup de um
> desenvolvedor). O estado **commitado (HEAD)** é **mais seguro**. Diferenças:
>
> | Item | Working tree (analisado) | Commitado (HEAD) — real |
> |---|---|---|
> | Swagger no profile `dev` | habilitado (público) | **desabilitado** (`enabled: false`) |
> | `show-sql` no `dev` | `true` | **`false`** |
> | Credenciais de banco no `dev` | hardcoded `appuser`/`coti` | **vêm de `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` (env)** |
> | Fallback de JWT/HMAC | commitado | **commitado (confirmado — SEC-003 mantém-se)** |
>
> **Impacto nos achados:**
> - **SEC-003** — **mantém-se Alta**: o fallback hardcoded de `CARIMBAI_JWT_SECRET`
>   (`h9V$…`) e `CARIMBAI_HMAC_SECRET` (`dev-secret-change-me`) está **commitado**
>   em `application-dev.yaml` **e** `application-local.yaml` (HEAD). Porém o
>   `appuser`/`coti` é **só working-tree** (não commitado); o `dev` commitado usa env.
> - **SEC-005 (Swagger)** — **rebaixado para Baixa**: no `dev` commitado o Swagger
>   está **desabilitado**. Exposto apenas sob `local` ou sem profile (default `local`).
> - **SEC-011 (`show-sql`)** — no `dev` commitado é `false`; só `local` tem `true`.
> - **SEC-028** — o `dev` commitado é razoavelmente endurecido; o risco real é o
>   **default de profile ser `local`** (Swagger on + fallback de segredo) se
>   `SPRING_PROFILES_ACTIVE` não for definido, **e** o fallback de segredo (SEC-003).
>
> Recomendação adicional: **não commitar** as alterações locais de
> `application-dev.yaml` (elas reativam Swagger/`show-sql` e hardcodam credenciais).
> Detalhes no `RETEST_REPORT.md`.

> A severidade é **preliminar** (baseada em leitura de código). Pode subir ou
> descer após validação dinâmica nas fases posteriores.

**Tema dominante:** o **lado do cliente (customer) não possui autenticação**.
Endpoints que expõem dados pessoais e que emitem tokens de selo/resgate estão
`permitAll()` e são acessíveis por **ID sequencial** ou por **e-mail**, sem
qualquer credencial. Esse é o maior risco antes da comercialização.

---

## 3. Arquitetura e stack detectada

### 3.1. Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 25 |
| Framework | Spring Boot 3.5.9 (Web, Data JPA, Security, Validation, Actuator) |
| Build | Maven (wrapper `./mvnw`) |
| Banco | PostgreSQL 16 (schemas `core`, `fidelity`, `ops`) |
| Migrations | Flyway (`db.migration/`) — desabilitado no profile `dev`, ativo no `local` |
| Auth | JWT (jjwt 0.12.6) para staff; HMAC-SHA256 para tokens de QR de selo |
| Mapeamento | MapStruct 1.6.3 + Lombok |
| Doc API | springdoc-openapi (Swagger UI) 2.8.9 |
| Login social | Google (`google-api-client`), Apple (`nimbus-jose-jwt`), Facebook (Graph API) |
| Crypto senha | `spring-security-crypto` (BCrypt) |
| Deploy | GitHub Actions → `scp` do JAR para VPS → `systemctl restart` |
| Container | Dockerfile multi-stage (Temurin 25); `docker-compose` (Postgres + pgAdmin) — uso dev |

### 3.2. Estrutura de pacotes (`com.app.carimbai`)

`controllers/` (REST finos) → `services/` + `facade/` (regra de negócio) →
`repositories/` (Spring Data JPA). `dtos/` (entrada/saída, Bean Validation),
`mappers/` (MapStruct), `models/` (`core/`, `fidelity/`, raiz `StampToken`/
`IdempotencyKey`), `security/` (`JwtAuthenticationFilter`), `config/`
(`SecurityConfig`, `SwaggerConfig`, `DevDataSeeder`, …), `handler/`
(`GlobalExceptionHandler`), `enums/`, `utils/`, `services/social/`.

### 3.3. Pontos de entrada

- **HTTP REST** na porta **1234** (profiles `dev`/`local`).
- **Swagger UI** em `/` e **OpenAPI** em `/api-docs` (habilitados e públicos).
- **Actuator** `/actuator/health` (público); demais endpoints actuator caem em `denyAll`.
- Filtro único de autenticação: `JwtAuthenticationFilter` (Bearer no header `Authorization`).

---

## 4. Endpoints e modelo de autorização

Regras em [`SecurityConfig.java`](src/main/java/com/app/carimbai/config/SecurityConfig.java)
(`STATELESS`, CSRF desabilitado, `anyRequest().denyAll()` no final).

| Método | Rota | Proteção configurada | Observação |
|---|---|---|---|
| POST | `/api/auth/login` | público | login staff (e-mail+senha) |
| POST | `/api/auth/switch-merchant` | autenticado | troca merchant ativo |
| POST | `/api/customers/login-or-register` | público | **sem credencial** — retorna `customerId` + PII |
| POST | `/api/customers/social-login` | público | valida token social |
| POST | `/api/customers` | *(nenhuma regra → `denyAll`)* | efetivamente bloqueado (inconsistência) |
| GET | `/api/cards/customer/{customerId}` | **público (`/api/cards/**`)** | **BOLA** — lista cartões de qualquer cliente |
| POST | `/api/cards` | **público** | cria/obtém cartão para qualquer customer/program |
| GET | `/api/cards/{cardId}/redeem-qr` | **público** | emite token REDEEM_QR de qualquer cartão |
| GET | `/api/qr/{id}` | **público (`/api/qr/**`)** | emite token CUSTOMER_QR de qualquer cartão |
| GET | `/api/merchants/{id}/programs` | público (GET) | lista programas/promoções |
| POST/PUT | `/api/merchants/**` | autenticado + `@PreAuthorize('ADMIN')` | cria merchant/location/program |
| POST | `/api/stamp` | autenticado + `hasAnyAuthority('CASHIER','ADMIN')` | aplica selo |
| POST | `/api/redeem` | autenticado + `hasAnyAuthority('CASHIER','ADMIN')` | resgata recompensa |
| POST | `/api/admin/staff-users/{id}/pin` | autenticado + `@PreAuthorize('ADMIN')` | define PIN |
| POST | `/api/staff-users` | autenticado + `@PreAuthorize('ADMIN')` | cria staff |
| `/v3/api-docs/**`, `/swagger-ui/**` | — | público | doc da API |

### 4.1. Autenticação

- **Staff:** JWT HMAC assinado (`JwtService`), claims `sub`(staffId), `role`,
  `merchantId`, `email`; expiração **8h**; segredo `CARIMBAI_JWT_SECRET`.
  Filtro valida expiração, recarrega o `StaffUser` do banco e exige `active=true`
  a cada request (bom — revoga usuário inativo). Sem refresh token / sem
  revogação explícita / logout é apenas client-side (token válido até expirar).
- **Cliente (customer):** **não há autenticação.** `login-or-register` não pede
  senha nem valida token; `social-login` valida token do provedor mas **não
  emite sessão/JWT próprio** — o cliente é identificado apenas pelo `customerId`
  numérico, e as rotas de cartão/QR são públicas.
- **Tokens de selo:** HMAC-SHA256 sobre `type|idRef|nonce|exp`, TTL ~90s,
  anti-replay por `nonce` único persistido (`ops.stamp_tokens`), comparação de
  assinatura em tempo constante. Implementação sólida.

### 4.2. Autorização e isolamento multi-tenant

- Operações de staff usam papel via `StaffRole` (`ADMIN`/`CASHIER`) com
  `@PreAuthorize` e `@EnableMethodSecurity`.
- **Isolamento por merchant é aplicado no backend** em `applyStamp` e `redeem`:
  o cartão/localização é checado contra o `merchantId` do token
  (`card.getProgram().getMerchant().getId().equals(activeMerchantId)`). **Bom
  controle** contra escalonamento horizontal entre lojistas.
- **Lacuna:** o lado público (cards/qr) não tem qualquer dono/escopo — qualquer
  um lê/emite por ID.

---

## 5. Dados pessoais (visão de reconhecimento)

> O mapeamento detalhado é a **Fase 2** (`LGPD_DATA_MAPPING.md`). Aqui apenas o
> levantamento inicial.

| Dado | Entidade / tabela | Tipo |
|---|---|---|
| Nome | `fidelity.customers.name` | PII |
| E-mail | `customers.email`, `core.staff_users.email` | PII |
| Telefone | `customers.phone` | PII |
| ID do provedor social | `customers.provider_id` (`google:sub`, etc.) | PII (identificador) |
| Documento (CNPJ/CPF) | `core.merchants.document` | PII se MEI/pessoa física |
| Endereço | `core.locations.address` | PII do estabelecimento |
| Hash de senha | `staff_users.password_hash` (BCrypt) | credencial |
| Hash de PIN | `staff_users.pin_hash` (BCrypt) | credencial |
| IP | `fidelity.stamps.ip` (INET) | PII (rede) |
| User-Agent | `fidelity.stamps.user_agent` | PII (dispositivo) |
| Tokens | JWT (claim `email`), HMAC de QR | sensível operacional |

Sem indício de dados **sensíveis** (saúde, biometria, origem racial etc.). Há
**dados de contato em escala** e possivelmente **dados de menores** (clientes de
programa de fidelidade não têm verificação de idade — avaliar na Fase 14).

### 5.1. Fluxo de dados (origem → processamento → armazenamento → saída)

```
[Cliente PWA] --(nome/email/telefone)--> POST /api/customers/login-or-register
     |                                         |
     |                                   CustomerService.loginOrRegister
     |                                         v
     |                                 fidelity.customers  (armazena PII)
     |                                         |
     |  <----- CustomerLoginResponse (customerId + nome/email/telefone/providerId)
     |
[Cliente PWA] --GET /api/cards/customer/{id}--> CardService --> fidelity.cards
     |  <----- programas, lojista, recompensa, progresso
     |
[Cliente PWA] --GET /api/qr/{cardId}--> StampTokenService.issue (HMAC)
     |  <----- token efêmero (QR)
     v
[Caixa autenticado] --POST /api/stamp (token + Idempotency-Key + User-Agent)-->
        StampsService.applyStamp  --> valida HMAC+TTL+replay+idempotência
                                  --> grava fidelity.stamps (IP*, User-Agent)
                                  --> incrementa fidelity.cards
        *IP só se preenchido; coluna existe.

[Caixa autenticado] --POST /api/redeem (+PIN)--> RedeemService --> fidelity.rewards

Login social: token Google/Apple/Facebook --> verifier --> e-mail/nome/subject
              --> CustomerService.socialLoginOrRegister --> customers
```

Saídas externas: chamadas a **Google/Apple/Facebook** para verificar tokens
sociais; deploy via **GitHub Actions → VPS**. Sem analytics/storage externos
detectados no backend.

---

## 6. Achados

> IDs `SEC-00x`. Severidade preliminar com vetor CVSS v3.1 indicativo. Status
> inicial: **Aberto**.

### SEC-001 — Lado do cliente sem autenticação: leitura e alteração de dados pessoais por ID/e-mail (BOLA / Broken Authentication)
- **Severidade:** Crítica
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:L/A:N` (~8.2) — eleva-se pela combinação com SEC-002/003.
- **Categoria:** OWASP API1:2023 (BOLA) + API2:2023 (Broken Authentication).
- **Local:** [`SecurityConfig.java:56-57`](src/main/java/com/app/carimbai/config/SecurityConfig.java) (`/api/cards/**`, `/api/qr/**` em `permitAll`); [`CustomerController.java:43`](src/main/java/com/app/carimbai/controllers/CustomerController.java); [`CardsController.java:33`](src/main/java/com/app/carimbai/controllers/CardsController.java); [`CustomerService.java:38`](src/main/java/com/app/carimbai/services/CustomerService.java).
- **Evidência segura:**
  - `POST /api/customers/login-or-register` aceita só `{name,email,phone,providerId}` — **sem senha/token** — e responde com `customerId` + dados do cliente. Em `loginOrRegister`, se o e-mail (ou telefone) já existe, o registro é **sobrescrito** com os campos enviados (tampering) e o cliente existente é retornado (disclosure por e-mail).
  - `GET /api/cards/customer/{customerId}` é público e retorna a lista de cartões (lojista, programa, recompensa, progresso) de **qualquer** cliente, com `customerId` sequencial/enumerável.
- **Impacto:** sem autenticar, um atacante: (a) descobre se um e-mail é cliente e lê seus dados; (b) **sobrescreve** telefone/e-mail/nome de um cliente existente; (c) enumera `customerId` e mapeia hábitos de consumo (quais lojas a pessoa frequenta) — exposição de dados pessoais em escala (risco LGPD relevante).
- **Recomendação:** introduzir autenticação de cliente (sessão/JWT próprio após `social-login` ou OTP), escopar `/api/cards/**` e `/api/qr/**` ao dono autenticado, e tratar `login-or-register` como operação autenticada/idempotente que não sobrescreve dados de terceiros por e-mail. Validar na Fase 5/6.
- **Status:** Aberto.

### SEC-002 — Emissão pública de tokens de selo (CUSTOMER_QR) para qualquer cartão
- **Severidade:** Alta
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N` (~5.4, eleva com insider).
- **Categoria:** API1:2023 (BOLA) / abuso de fluxo de negócio.
- **Local:** [`QrCodeController.java:23`](src/main/java/com/app/carimbai/controllers/QrCodeController.java), [`SecurityConfig.java:57`](src/main/java/com/app/carimbai/config/SecurityConfig.java).
- **Evidência segura:** `GET /api/qr/{cardId}` (público) gera um token HMAC válido (TTL ~90s) para **qualquer** `cardId`. O token é o que o caixa escaneia para carimbar.
- **Impacto:** quebra a premissa de "presença do cliente". Um caixa/ADMIN mal-intencionado (ou com acesso à API) pode obter o QR de cartões arbitrários e carimbar sem o cliente presente (fraude de fidelidade). Também permite enumeração de cartões válidos.
- **Recomendação:** exigir que o QR só seja emitido para o cliente autenticado dono do cartão. Reavaliar junto de SEC-001. Validar na Fase 12 (lógica de negócio).
- **Status:** Aberto.

### SEC-003 — Segredos (JWT/HMAC/DB) versionados no repositório
- **Severidade:** Alta (Crítica se a produção usar o fallback sem `env`).
- **CVSS:** `AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:N` (~7.4).
- **Categoria:** OWASP A05/A07; gestão de segredos.
- **Local:** [`application-dev.yaml:30,36`](src/main/resources/application-dev.yaml), [`application-local.yaml:31,37`](src/main/resources/application-local.yaml) (ambos versionados — `git ls-files` confirma).
- **Evidência segura (valores mascarados):**
  - `CARIMBAI_JWT_SECRET` com **fallback embutido** `h9V$…nLjC` (32 chars, aparência de chave real).
  - `CARIMBAI_HMAC_SECRET` fallback `dev-secret-change-me`.
  - Credenciais de banco em claro: `application.yaml` (`appuser` / `c…`), `application-local.yaml` (`postgres`/`postgres`), `docker-compose.yaml` (`postgres`/`postgres`, pgAdmin `admin@admin.com`/`a…`).
- **Impacto:** se qualquer ambiente subir sem `CARIMBAI_JWT_SECRET` exportado, o fallback assina os JWTs — quem conhece o valor (está no git) **forja tokens de ADMIN** → tomada de conta total. Mesma lógica para o HMAC (forja de QR de selo).
- **Recomendação:** (1) remover os fallbacks — falhar a inicialização se a env não existir; (2) **rotacionar** JWT/HMAC/DB caso esses valores já tenham ido a algum ambiente real; (3) limpar o histórico do git (BFG/`git filter-repo`) — ver playbook no Anexo Fase 3; (4) confirmar que produção injeta tudo por env. **Não** colar os valores reais em relatórios.
- **Confirmação na Fase 3 (histórico do git):** o literal do `CARIMBAI_JWT_SECRET`
  (valor mascarado `h9V$3u…[REDACTED]`, 32 chars) foi **introduzido em
  2025-12-02**, commit `c6f0075` ("add security and login"), e **permanece no
  HEAD** em `application-dev.yaml` e `application-local.yaml` — ou seja, está vivo
  na árvore atual **e** em todo o histórico. Remover do HEAD **não** basta:
  qualquer clone antigo ainda contém o valor → **rotação é obrigatória**.
  `CARIMBAI_HMAC_SECRET` aparece apenas como placeholder (`dev-secret-change-me`),
  sem valor real vazado; ainda assim, usá-lo como fallback é inseguro. Detalhes e
  resultados negativos no **Anexo — Fase 3**.
- **Status:** Aberto. **Ação imediata recomendada: rotacionar o JWT secret.**

### SEC-004 — Emissão pública de REDEEM_QR + criação pública de cartões
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N` (~5.4).
- **Categoria:** API1/API6:2023.
- **Local:** [`CardsController.java:41,49`](src/main/java/com/app/carimbai/controllers/CardsController.java).
- **Evidência segura:** `GET /api/cards/{cardId}/redeem-qr` e `POST /api/cards` são públicos. O resgate em si exige CASHIER/ADMIN + PIN, mas a emissão do token de resgate e a criação de cartões (vínculo customer↔program arbitrário) não têm dono.
- **Impacto:** mint de tokens de resgate para cartões arbitrários (fraude com insider), e criação não autenticada de cartões (poluição de dados / consumo de recursos).
- **Recomendação:** escopar ao cliente/lojista autenticado. Reavaliar com SEC-001.
- **Status:** Aberto.

### SEC-005 — Swagger/OpenAPI habilitados e públicos (inclusive provável produção)
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N` (~5.3).
- **Local:** [`application.yaml`](src/main/resources/application.yaml) / `application-dev.yaml` (`springdoc.swagger-ui.enabled: true`, path `/`), [`SecurityConfig.java:49-51`](src/main/java/com/app/carimbai/config/SecurityConfig.java).
- **Evidência segura:** `/`, `/api-docs`, `/swagger-ui/**`, `/v3/api-docs/**` em `permitAll`, sem desabilitar por profile de produção.
- **Impacto:** exposição completa da superfície da API a anônimos, facilitando enumeração e exploração das falhas acima.
- **Recomendação:** desabilitar Swagger/api-docs em produção (ou proteger por auth). Confirmar na Fase 10.
- **Confirmação Fase 10:** como não há profile de produção (SEC-028), Swagger e
  `/api-docs` ficam **públicos em produção**. **Actuator**, por outro lado, está
  **OK por padrão**: sem `management.*` configurado, só `/actuator/health` é
  exposto (e `permitAll`); demais endpoints actuator caem em `denyAll`. Recomenda-se
  fixar explicitamente `management.endpoints.web.exposure.include=health` e
  `management.endpoint.health.show-details=never`.
- **Status:** Aberto.

### SEC-006 — Ausência de rate limiting em login, cadastro e login social
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:L` (~5.4).
- **Categoria:** API4:2023 (Unrestricted Resource Consumption).
- **Local:** [`AuthController.java`](src/main/java/com/app/carimbai/controllers/AuthController.java), [`CustomerController.java`](src/main/java/com/app/carimbai/controllers/CustomerController.java). Rate limit existe **apenas** por cartão no selo ([`StampsService.checkRateLimit`](src/main/java/com/app/carimbai/services/StampsService.java)).
- **Impacto:** brute force de senha de staff, enumeração de e-mails de clientes, abuso de cadastro/login social.
- **Recomendação:** rate limit + lockout/atraso em `/api/auth/login` e nos endpoints de cliente. Detalhar na Fase 5/11.
- **Status:** Aberto.

### SEC-007 — Mensagens de erro expõem detalhes internos / oráculo de existência
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N` (~5.3).
- **Local:** [`GlobalExceptionHandler.java:22-26,43-48`](src/main/java/com/app/carimbai/handler/GlobalExceptionHandler.java).
- **Evidência segura:** `IllegalArgumentException` retorna `ex.getMessage()` cru (ex.: `"Card not found: 123"`, `"Card does not belong to staff merchant"`, `"No active link to requested merchant"`); validação retorna `bindingResult.toString()` (verboso). Não há handler genérico de `Exception` → exceções não previstas caem no `/error` padrão.
- **Complemento Fase 7:** **`DataIntegrityViolationException` não é tratada** no
  `GlobalExceptionHandler`. Como os DTOs não limitam tamanho (SEC-022), um valor
  acima do limite da coluna (ex.: `name` > 120, `email` > 160) ou a violação de
  `UNIQUE` (ex.: e-mail de cliente duplicado) gera **HTTP 500** genérico em vez
  de 400/409 — comportamento ruim e potencial vazamento de detalhe via `/error`.
  JSON malformado → `HttpMessageNotReadableException` (400 padrão do Spring, ok).
- **Impacto:** oráculos de enumeração (cartão/merchant existe?), vazamento de estrutura interna e respostas 500 para entradas inválidas; risco de stack trace se `server.error.include-*` não estiver restrito.
- **Recomendação:** mensagens genéricas para o cliente, detalhe só em log; adicionar handler `Exception` → 500 genérico **e** `DataIntegrityViolationException` → 409/400; garantir `include-stacktrace=never`/`include-message=never`. Fase 7/9.
- **Status:** Aberto.

### SEC-008 — Login de staff permite enumeração parcial de e-mails válidos
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N` (~5.3).
- **Local:** [`AuthService.login`](src/main/java/com/app/carimbai/services/AuthService.java:25).
- **Evidência segura:** credenciais inválidas retornam genérico `"Invalid credentials"` (bom), **porém** e-mail válido com conta inativa → `"Staff user is inactive"` (409) e sem vínculo → `"Staff user has no active merchant links"` (409) — respostas distintas revelam que o e-mail existe.
- **Recomendação:** uniformizar resposta para qualquer falha de login. Fase 5.
- **Status:** Aberto.

### SEC-009 — CSRF desabilitado (avaliação Fase 7)
- **Severidade:** Informativa
- **Local:** [`SecurityConfig.java:40`](src/main/java/com/app/carimbai/config/SecurityConfig.java).
- **Determinação Fase 7 — a autenticação é por HEADER, não por cookie:**
  - Auth via `Authorization: Bearer <JWT>` ([`JwtAuthenticationFilter`](src/main/java/com/app/carimbai/security/JwtAuthenticationFilter.java)); sessão `STATELESS`; **nenhum cookie** é emitido/lido (confirmado: `grep` por `Cookie`/`SameSite`/`HttpOnly` retornou nada além de `mac.reset()`); CORS com `setAllowCredentials(false)`.
  - Um site malicioso **não consegue** forjar a requisição: o navegador não anexa
    o header `Authorization` automaticamente e não há cookie de sessão para
    "pegar carona". Logo, **`csrf(disable)` está correto** e **não é necessário**
    token anti-CSRF nem atributo `SameSite` (não há cookie a configurar).
- **Condição para revisão:** se algum dia a auth passar a usar **cookie de
  sessão** (ou `allowCredentials=true`), torna-se obrigatório: token anti-CSRF,
  `SameSite=Lax/Strict`, `HttpOnly`, `Secure`, e verificação de `Origin/Referer`.
- **Status:** Aberto (informativo — sem ação no momento).

### SEC-010 — Configuração de infraestrutura/container melhorável
- **Severidade:** Baixa
- **Local:** [`Dockerfile`](Dockerfile), [`docker-compose.yaml`](docker-compose.yaml).
- **Evidência segura:** container roda como **root** (sem `USER`); `EXPOSE 8080` mas app sobe na **1234** (inconsistência); `docker-compose` publica Postgres (5434) e pgAdmin (8080, `admin/admin`) — uso dev, mas versionado. Imagens base sem digest fixo.
- **Atenuante Fase 10:** o **deploy de produção NÃO usa Docker** — é jar via `scp`
  + `systemctl` na VPS (`deploy.yml`); Dockerfile/compose são artefatos de **dev**.
  Por isso o impacto em produção é baixo. Ainda assim, se algum dia conteinerizar,
  aplicar usuário não-root, alinhar porta e fixar imagens. A exposição do
  Postgres/pgAdmin em produção (fora do compose dev) é `PENDENTE` — confirmar que
  o banco **não** está público na VPS.
- **Recomendação:** usuário não-root no runtime, alinhar porta, fixar imagens, garantir que compose não vá para produção; confirmar que o banco de produção não tem porta pública.
- **Status:** Aberto.

### SEC-011 — `show-sql: true` em todos os profiles
- **Severidade:** Baixa
- **Local:** `application.yaml` / `-dev` / `-local`.
- **Evidência segura:** `spring.jpa.show-sql: true` e `format_sql: true` ativos — logging verboso de SQL; em produção pode registrar consultas com dados pessoais e degradar performance.
- **Recomendação:** desabilitar em produção. Fase 9/10.
- **Status:** Aberto.

### SEC-012 — JWT sem revogação / logout apenas client-side / sem refresh / sem timeout de inatividade
- **Severidade:** Baixa
- **CVSS:** `AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:L/A:N` (~3.7).
- **Local:** [`JwtService.java`](src/main/java/com/app/carimbai/services/JwtService.java), [`JwtAuthenticationFilter.java`](src/main/java/com/app/carimbai/security/JwtAuthenticationFilter.java), [`SecurityConfig.java:42`](src/main/java/com/app/carimbai/config/SecurityConfig.java).
- **Evidência segura (revisão Fase 5):**
  - **Sessão:** `SessionCreationPolicy.STATELESS` — não há `JSESSIONID`/sessão de servidor para autenticação ⇒ **sem risco de session fixation** (controle positivo).
  - **Claims:** `sub`, `role`, `merchantId`, `email`, `iat`, `exp`. **Faltam `iss`, `aud` e `jti`.** Sem `jti`/blacklist não há revogação por token.
  - **Expiração:** 8h **absoluta**; **não há timeout de inatividade (idle)** — token roubado vale até 8h independentemente de uso.
  - **Logout:** **não existe endpoint de logout** e o token não é invalidado no servidor — logout é só o cliente descartar o token.
  - **Kill switch parcial:** o filtro recarrega o `StaffUser` e exige `active=true` a cada request ⇒ **desativar o usuário (`active=false`) revoga imediatamente** todos os tokens dele (mitigação real, porém é tudo-ou-nada, não por token/sessão).
  - **Concorrência:** múltiplos tokens válidos coexistem sem limite (sem política de sessões concorrentes).
  - **Algoritmo:** assinatura HS256 via `verifyWith(SecretKey)` (jjwt) — sem brecha de *algorithm confusion*/`alg=none` (controle positivo).
  - **Nota (confirmada por teste na Fase 13):** `JwtService.isExpired()` **lança**
    `ExpiredJwtException` para token expirado em vez de retornar `true` — é
    *fail-closed* (o `JwtAuthenticationFilter` captura `JwtException` e trata como
    não-autenticado), mas o nome do método é enganoso. Melhoria de clareza, sem
    impacto de segurança.
- **Impacto:** um JWT vazado é utilizável por até 8h sem possibilidade de revogação granular; ausência de idle timeout amplia a janela.
- **Recomendação:** adicionar `iss`/`aud` e validá-los; considerar `jti` + lista de revogação (ou refresh token curto + access token de poucos minutos); reduzir TTL e introduzir idle timeout; criar logout que revogue o refresh. Avaliar impacto no PWA antes (muda contrato). (Migrations `refresh_tokens`/`password_reset_tokens` existem em outras branches — feature planejada.)
- **Status:** Aberto.

### SEC-016 — Ausência de política de senha para usuários de staff
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N` (~5.4, depende de SEC-006).
- **Categoria:** OWASP A07 (Identification and Authentication Failures).
- **Local:** [`CreateStaffUserRequest.java`](src/main/java/com/app/carimbai/dtos/admin/CreateStaffUserRequest.java) (`@NotBlank String password`), [`StaffService.createStaffUser`](src/main/java/com/app/carimbai/services/StaffService.java:53).
- **Evidência segura:** a senha do staff é validada apenas com `@NotBlank` — **sem comprimento mínimo, complexidade ou verificação contra senhas vazadas**. Aceita `"1"`. Hash é BCrypt **força padrão (10)** ([`BcryptConfig`](src/main/java/com/app/carimbai/config/BcryptConfig.java)).
- **Impacto:** contas privilegiadas (CASHIER/ADMIN) podem ter senhas triviais; combinado com a ausência de rate limit no login (SEC-006), facilita brute force/credential stuffing.
- **Recomendação:** exigir mínimo de 10–12 caracteres (ou passphrase), bloquear senhas comuns, e avaliar `BCryptPasswordEncoder(12)`. Mudança de baixo impacto funcional (só políticas de validação). Impacto: cadastros novos passam a exigir senha forte.
- **Status:** Aberto.

### SEC-017 — PIN de caixa sem proteção contra brute force e sem validação de formato
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:N` (~4.2).
- **Categoria:** OWASP A07 / API4 (Unrestricted Resource Consumption).
- **Local:** [`StaffService.setPin`/`validateCashierPin`](src/main/java/com/app/carimbai/services/StaffService.java:25), [`SetPinRequest.java`](src/main/java/com/app/carimbai/dtos/admin/SetPinRequest.java), [`AdminController.setStaffPin`](src/main/java/com/app/carimbai/controllers/AdminController.java:27), [`RedeemController`](src/main/java/com/app/carimbai/controllers/RedeemController.java).
- **Evidência segura:** o PIN é hash BCrypt (bom), porém: (a) **sem rate limit/lockout** nas tentativas em `POST /api/redeem` — um PIN de 4 dígitos tem só 10⁴ combinações; (b) `setPin` diz "4..10 digits" mas **não valida que são dígitos** (aceita qualquer caractere); (c) `SetPinRequest` não tem Bean Validation e `setStaffPin` não usa `@Valid`.
- **Impacto:** quem possua um JWT de caixa pode forçar o PIN para autorizar resgates (fraude de recompensa). Validação de formato fraca.
- **Recomendação:** rate limit/lockout por staff nas tentativas de PIN; validar formato no DTO (`@Pattern`, `@NotBlank`) e adicionar `@Valid`; considerar contador de tentativas com bloqueio temporário. Impacto funcional baixo.
- **Status:** Aberto.

### SEC-018 — Sem MFA para contas administrativas
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N` (~5.4).
- **Categoria:** OWASP A07; ANPD (boas práticas para contas privilegiadas).
- **Local:** fluxo de login ([`AuthService.login`](src/main/java/com/app/carimbai/services/AuthService.java:25)) — apenas e-mail+senha.
- **Evidência segura:** não há segundo fator para nenhuma conta, inclusive `ADMIN` (que cria merchants, staff, programas e define PINs).
- **Avaliação de viabilidade:** **viável** com baixo custo via **TOTP** (RFC 6238): adicionar `totp_secret` (cifrado) a `staff_users`, exigir código no login quando `role=ADMIN` (ou opt-in por usuário). Bibliotecas: `dev.samstevens.totp` ou `com.warrenstrange:googleauth`. Alternativa: *magic link*/OTP por e-mail (depende de provedor de e-mail, hoje inexistente).
- **Recomendação:** implementar TOTP ao menos para `ADMIN` antes da comercialização. Mudança **grande** (novo fluxo de login + tela no PWA) → **propor antes de implementar** (não alterar agora).
- **Status:** Aberto (proposta).

### SEC-020 — Escalação horizontal entre lojistas: endpoints ADMIN não escopam ao merchant do token (BOLA / Broken Function Level Authorization)
- **Severidade:** Alta
- **CVSS:** `AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:H/A:L` (~7.3).
- **Categoria:** OWASP API1:2023 (BOLA) + API5:2023 (Broken Function Level Authorization) + API3 (BOPLA).
- **Local:** [`MerchantController`](src/main/java/com/app/carimbai/controllers/MerchantController.java), [`AdminController`](src/main/java/com/app/carimbai/controllers/AdminController.java), [`StaffUserController`](src/main/java/com/app/carimbai/controllers/StaffUserController.java), [`ProgramService`](src/main/java/com/app/carimbai/services/ProgramService.java), [`LocationService`](src/main/java/com/app/carimbai/services/LocationService.java), [`StaffService`](src/main/java/com/app/carimbai/services/StaffService.java).
- **Causa-raiz:** o papel `ADMIN` é uma **string global** no JWT (vinda do
  `StaffUserMerchant` ativo). `@PreAuthorize("hasAuthority('ADMIN')")` confere
  **apenas o papel**, não o merchant. Diferente de `applyStamp`/`redeem` (que
  comparam o recurso com `SecurityUtils.getActiveMerchantId()`), os endpoints de
  gestão **usam o `merchantId` do path/body sem compará-lo ao do token**.
- **Evidência segura (todos exigem só `role=ADMIN` de *qualquer* lojista):**
  - `POST /api/merchants/{merchantId}/locations` → `LocationService.saveLocationByMerchantId(merchantId, …)` usa o `merchantId` do path sem checar escopo → ADMIN de A cria localização sob o lojista B.
  - `POST /api/merchants/{merchantId}/programs` → `ProgramService.createProgram(merchantId, …)` idem → cria promoção sob B.
  - `PUT /api/merchants/{merchantId}/programs/{programId}` → `updateProgram` valida que o programa pertence ao `merchantId` (bom, evita mismatch path), **mas não valida que o token pertence a esse merchant** → ADMIN de A edita/desativa promoção de B.
  - `POST /api/staff-users` → `StaffService.createStaffUser(request)` usa `request.merchantId()` → ADMIN de A cria staff (inclusive `role=ADMIN`) vinculado a B.
  - `POST /api/admin/staff-users/{id}/pin` → `StaffService.setPin(id, …)` aceita **qualquer** `staffId`, sem escopo de merchant → ADMIN de A sobrescreve o PIN do caixa de B (ver também SEC-017).
  - `POST /api/merchants` → qualquer `ADMIN` cria novos lojistas (não há separação entre *platform admin* e *merchant admin*; o criador nem fica vinculado ao merchant criado).
- **Impacto:** um lojista cliente (ADMIN do próprio merchant) pode **sabotar/alterar dados de concorrentes** na mesma plataforma: editar/desativar promoções alheias, criar staff sob outro lojista, redefinir PIN de caixas de terceiros, criar merchants. Quebra de isolamento multi-tenant — crítico para um SaaS comercializado.
- **Recomendação:** em cada operação de gestão, validar `merchantId (path/body) == SecurityUtils.getActiveMerchantId()` (ou derivar o merchant **somente** do token e ignorar o do path/body); para `setPin`, exigir que o staff alvo pertença ao merchant do token. Introduzir papel **separado** de *platform admin* para `createMerchant`/onboarding. Mudança de autorização com impacto funcional → **propor plano antes de implementar** (não alterar agora). Validar com os testes `TC-AUTHZ-*`.
- **Status:** Aberto.

### SEC-022 — Validação de entrada insuficiente e inconsistente
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:L/A:L` (~4.8).
- **Categoria:** OWASP API8:2023 (Security Misconfiguration) / robustez de entrada.
- **Local:** [`CreateCustomerRequest`](src/main/java/com/app/carimbai/dtos/admin/CreateCustomerRequest.java), [`CustomerLoginRequest`](src/main/java/com/app/carimbai/dtos/customer/CustomerLoginRequest.java), [`StampRequest`](src/main/java/com/app/carimbai/dtos/StampRequest.java), [`RedeemRequest`](src/main/java/com/app/carimbai/dtos/RedeemRequest.java), [`CreateProgramRequest`](src/main/java/com/app/carimbai/dtos/admin/CreateProgramRequest.java).
- **Evidência segura:**
  - **E-mail sem formato:** `@Email` só existe no `LoginRequest` de staff. Os
    DTOs de cliente (`CustomerLoginRequest`, `CreateCustomerRequest`) aceitam
    e-mail/telefone em qualquer formato; `loginOrRegister` nem usa `@Valid`.
  - **Sem limite de tamanho:** nenhum DTO limita comprimento. Valor acima do
    tamanho da coluna (`name`≤120, `email`≤160, `phone`≤30…) gera
    `DataIntegrityViolationException` → **HTTP 500** não tratado (ver SEC-007).
  - **`@Valid` não cascateia:** `StampRequest.payload` é `Object` (convertido
    manualmente por `ObjectMapper`), e `RedeemRequest.redeemQr` não é `@Valid` —
    as anotações `@NotNull` de `CustomerQrPayload`/`RedeemQrPayload` **não são
    aplicadas**. A validação acaba acontecendo só em runtime (HMAC/`UUID.fromString`),
    funcionando, porém sem mensagens previsíveis.
- **Impacto:** entradas inválidas viram 500 (qualidade/robustez, possível abuso),
  dados inconsistentes persistidos, e validação dependente de efeitos colaterais.
- **Recomendação:** adicionar `@Email`, `@Size`, `@Pattern` (telefone), `@Valid`
  em controllers de cliente; cascatear validação para os payloads (tipar
  `StampRequest.payload` como `CustomerQrPayload` + `@Valid`); tratar
  `DataIntegrityViolationException` (SEC-007). Mudança de baixo impacto funcional
  (mais rejeições 400 onde hoje passa/500).
- **Status:** Aberto.

### SEC-023 — Conteúdo de texto/URL armazenado sem sanitização (feed de XSS/open-redirect para o frontend)
- **Severidade:** Baixa
- **CVSS:** `AV:N/AC:L/PR:L/UI:R/S:C/C:L/I:L/A:N` (~4.7).
- **Categoria:** OWASP A03 (Injection — Stored XSS, via frontend consumidor).
- **Local:** [`CreateProgramRequest`](src/main/java/com/app/carimbai/dtos/admin/CreateProgramRequest.java) (`name`, `description`, `terms`, `category`, `imageUrl`), nomes de merchant/cliente.
- **Evidência segura:** o backend (API JSON) **armazena e devolve** strings livres
  sem sanitização. `imageUrl` não tem validação de esquema — aceita
  `javascript:...`/`data:...`. Se o PWA (`../carimbai-app`) renderizar esses
  campos sem *escaping* (ou usar `imageUrl` em `src`/`href` sem checar o esquema),
  há **XSS armazenado / open redirect** no cliente.
- **Impacto:** execução de script no contexto de outros usuários do PWA (limitado
  a quem cria programas — ADMIN — mas com SEC-020 um ADMIN pode injetar em
  programas de outro lojista). Backend não é vulnerável diretamente (não renderiza HTML).
- **Recomendação:** validar `imageUrl` para `http(s)` apenas; limitar tamanho dos
  textos; garantir *output encoding* no frontend (responsabilidade primária do
  PWA — registrar em revisão do `carimbai-app`). Considerar sanitização
  server-side (ex.: OWASP Java HTML Sanitizer) se algum campo for renderizado como HTML.
- **Status:** Aberto.

### SEC-024 — JWT de staff armazenado em `localStorage` (roubo via XSS)
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:R/S:U/C:H/I:L/A:N` (~6.1).
- **Categoria:** OWASP A05/A07; armazenamento inseguro de credencial no cliente.
- **Local:** `../carimbai-app/src/components/StaffLogin.tsx:39`, `StaffScreen.tsx`, `services/api.ts` (lê o token do `localStorage`).
- **Evidência segura:** o login de staff grava a sessão **incluindo o `token` JWT**
  em `localStorage` (`STAFF_STORAGE_KEY = {token, staffId, role, merchantId, merchants}`).
  `localStorage` é acessível a qualquer JavaScript da origem.
- **Impacto:** qualquer XSS no PWA (alimentado por SEC-023) permite **exfiltrar o
  JWT**; como o token vale 8h e **não há revogação por token** (SEC-012), o
  atacante opera como o caixa/ADMIN por horas. Combinação SEC-023 + SEC-024 +
  SEC-012 é a cadeia de maior risco do frontend.
- **Recomendação:** preferir **cookie `HttpOnly`+`Secure`+`SameSite`** para o token
  (exige então CSRF token — reavaliar SEC-009) **ou**, mantendo header auth,
  reduzir TTL + adicionar revogação (SEC-012) e endurecer contra XSS (SEC-023,
  CSP — Fase 10). Mudança no frontend/contrato → **propor antes**. Registrar na
  revisão do `carimbai-app`.
- **Status:** Aberto.

### SEC-025 — Dados pessoais do cliente persistidos em `localStorage` (minimização)
- **Severidade:** Baixa (risco LGPD)
- **CVSS:** `AV:L/AC:L/PR:N/UI:R/S:U/C:L/I:N/A:N` (~2.8).
- **Categoria:** LGPD (minimização) / A05.
- **Local:** `../carimbai-app/src/hooks/useCustomer.ts:38,49`.
- **Evidência segura:** a "sessão" do cliente grava em `localStorage` o
  `CustomerLoginResponse` completo (`customerId, name, email, phone, providerId`),
  persistindo dados de contato no dispositivo por tempo indeterminado (sem TTL,
  sobrevive ao fechamento do navegador).
- **Impacto:** PII de contato exposta no dispositivo (acessível a XSS e a quem
  tenha acesso físico/local ao navegador); contraria minimização.
- **Recomendação:** persistir o mínimo (idealmente só um identificador opaco da
  sessão, após autenticar o cliente — SEC-001); evitar guardar e-mail/telefone;
  limpar no logout (já há `removeItem`). Registrar na revisão do `carimbai-app`.
- **Status:** Aberto.

### SEC-026 — Ausência de logging de auditoria e de eventos de segurança
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N` (~0.0 técnico; risco operacional/compliance — tratado como Média pelo impacto em detecção e LGPD).
- **Categoria:** OWASP A09:2021 (Security Logging and Monitoring Failures).
- **Local:** ausência geral; trilha parcial em `fidelity.stamps`/`rewards` (cashier/location/user-agent/token).
- **Evidência segura:** o código atual **não registra** nenhum dos eventos mínimos
  de auditoria: login bem-sucedido/falho, alteração/criação de staff, `setPin`,
  criação/edição de merchant/programa/localização, falhas de autorização
  (tentativas cross-tenant — SEC-020), nem acesso/exclusão/exportação de dados.
  A única trilha forense é o registro de `stamps`/`rewards` (quem carimbou/resgatou,
  onde, com qual token). Não há tabela `audit_log` na árvore atual (existe migration
  em outra branch — planejado).
- **Impacto:** brute force (SEC-006), abuso de PIN (SEC-017) e tentativas de
  escalação (SEC-020) passam **despercebidos**; sem trilha, a investigação e a
  **notificação de incidente à ANPD (3 dias úteis)** ficam comprometidas.
- **Recomendação:** registrar os eventos mínimos (ver Anexo Fase 9) em um log de
  auditoria estruturado (ou tabela `audit_log`), **sem dados sensíveis** (mascarar).
  Priorizar: falhas de login, falhas de autorização, ações administrativas,
  ciclo de vida de dados pessoais.
- **Status:** Aberto.

### SEC-027 — Sem gestão de logs (retenção, rotação, controle de acesso, mascaramento)
- **Severidade:** Baixa
- **CVSS:** `AV:L/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N` (~3.1).
- **Categoria:** OWASP A09; LGPD (retenção/acesso a dados pessoais em log).
- **Local:** ausência de `logback-spring.xml`/config de logging; `application*.yaml` (sem `logging.*`).
- **Evidência segura:** não há configuração de logging — usa-se o **console padrão**
  do Spring Boot (sem *file appender*, sem rotação, sem política de retenção, sem
  mascaramento). Em produção (serviço `systemd`), os logs vão para o journal/stdout
  e ficam acessíveis a quem tem acesso à VPS, sem retenção definida. `show-sql:true`
  (SEC-011) aumenta o volume/ruído.
- **Impacto:** dados pessoais que entrem em log (ex.: SEC-015) ficam sem retenção/
  expurgo e sem controle de acesso granular (LGPD); ausência de rotação pode
  encher disco.
- **Recomendação:** definir política de retenção/rotação, restringir acesso aos
  logs, adicionar mascaramento de PII e desabilitar `show-sql` em produção. Ver
  Anexo Fase 9 e Fase 10 (infra).
- **Status:** Aberto.

### SEC-028 — Sem profile de produção dedicado: produção herda configuração de dev/local
- **Severidade:** Média
- **CVSS:** `AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:L/A:N` (~6.4).
- **Categoria:** OWASP A05:2021 (Security Misconfiguration).
- **Local:** `src/main/resources/` (só `application.yaml`, `-dev`, `-local`; **não há `application-prod.yaml`**); [`application.yaml:3`](src/main/resources/application.yaml) (`active: ${SPRING_PROFILES_ACTIVE:local}`); [`DevDataSeeder.java:25`](src/main/java/com/app/carimbai/config/DevDataSeeder.java) (`@Profile("stg")` — perfil sem yaml correspondente).
- **Evidência segura:** o deploy é **jar via systemd na VPS** (`.github/workflows/deploy.yml`); não há perfil de produção. Se `SPRING_PROFILES_ACTIVE` não for definido, cai no default **`local`** (Flyway on, DB `localhost`, **fallback de JWT hardcoded** SEC-003, **`show-sql:true`** SEC-011, **Swagger público** SEC-005). Mesmo com `dev`, herdam-se Swagger/show-sql/fallback. A segurança em produção passa a depender de **cada env var** estar corretamente sobrescrita.
- **Impacto:** alta probabilidade de produção rodar com Swagger exposto, SQL em log e — pior — **assinando JWT com o segredo hardcoded** se a env faltar (token for|forja de ADMIN, SEC-003).
- **Recomendação:** criar `application-prod.yaml` com Swagger/`show-sql` desativados, **sem** defaults de segredo (falha se ausente), e fixar `SPRING_PROFILES_ACTIVE=prod` no serviço; remover o `@Profile("stg")` órfão ou criar o yaml. Tratar junto com SEC-003/005/011 no `SECURITY_FIX_PLAN.md`.
- **Status:** Aberto.

### SEC-029 — Cabeçalhos de segurança incompletos e HTTPS/forward-headers não configurados
- **Severidade:** Baixa
- **CVSS:** `AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:L/A:N` (~3.1).
- **Categoria:** OWASP A05; hardening de resposta HTTP.
- **Local:** [`SecurityConfig.java`](src/main/java/com/app/carimbai/config/SecurityConfig.java) (sem `http.headers(...)`); `application*.yaml` (sem `server.ssl`/`forward-headers-strategy`).
- **Evidência segura:** o Spring Security aplica por padrão `X-Content-Type-Options:
  nosniff` e `X-Frame-Options: DENY` (controle positivo). **Faltam**:
  `Strict-Transport-Security` (HSTS), `Referrer-Policy`, `Permissions-Policy` e
  `Content-Security-Policy`. `server.forward-headers-strategy` não está definido —
  atrás de proxy reverso o app pode não reconhecer o esquema HTTPS (afeta HSTS/redirect).
  **HTTPS obrigatório** depende do proxy na VPS (não confirmado — `PENDENTE`).
- **Impacto:** ausência de HSTS facilita *downgrade*/MITM em primeira conexão; sem
  forward-headers o app trata tudo como HTTP. (CSP tem pouco efeito na API JSON —
  o CSP que importa é o do **PWA** servido pela Vercel: registrar na revisão do `carimbai-app`.)
- **Recomendação:** definir `server.forward-headers-strategy: framework`; configurar
  HSTS no app (`http.headers().httpStrictTransportSecurity(...)`) ou no proxy;
  garantir redirecionamento HTTP→HTTPS no proxy; adicionar `Referrer-Policy: no-referrer`.
- **Status:** Aberto.

### SEC-030 — CORS permite origens `localhost` em produção
- **Severidade:** Baixa
- **CVSS:** `AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N` (~2.6).
- **Local:** [`SecurityConfig.java:80-85`](src/main/java/com/app/carimbai/config/SecurityConfig.java).
- **Evidência segura:** `allowedOriginPatterns` inclui `http://localhost:5173/3000/1234`
  além de `https://carimbai-app.vercel.app`. Como não há profile de produção (SEC-028),
  os origins de dev valem também em produção. Mitigado por `allowCredentials=false`.
- **Impacto:** baixo (sem credenciais via cookie), mas amplia origens aceitas em prod.
- **Recomendação:** restringir CORS por profile — só a origem do PWA em produção.
- **Status:** Aberto.

### SEC-031 — Backups não documentados / restauração não testada
- **Severidade:** Baixa (operacional / LGPD)
- **Local:** repositório (ausência de scripts/política de backup).
- **Evidência segura:** não há scripts, documentação ou configuração de backup/restore
  do PostgreSQL no repositório. Sem evidência de **criptografia de backup** nem de
  **teste de restauração**.
- **Impacto:** risco de perda de dados e de não atender requisitos de continuidade/LGPD.
- **Recomendação:** documentar e automatizar backup (criptografado), testar restauração
  periodicamente, restringir acesso aos backups. Cross-ref `PRIVACY_ACTION_ITEMS.md` PRV-O05.
- **Status:** Aberto.

### SEC-032 — Fluxos públicos sem anti-automação / rate limit (criação em massa e amplificação de saída)
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:L` (~6.3).
- **Categoria:** OWASP API4:2023 (Unrestricted Resource Consumption) + API6:2023 (Unrestricted Access to Sensitive Business Flows).
- **Local:** [`CustomerController`](src/main/java/com/app/carimbai/controllers/CustomerController.java), [`CardsController`](src/main/java/com/app/carimbai/controllers/CardsController.java), [`QrCodeController`](src/main/java/com/app/carimbai/controllers/QrCodeController.java).
- **Evidência segura:** o **único** rate limit do sistema é por cartão no selo
  (`StampsService.checkRateLimit`, janela de 120s — controle positivo). **Sem**
  limite/anti-automação em vários fluxos **públicos**:
  - `POST /api/customers/login-or-register` — **criação/atualização em massa de
    clientes** (INSERT em `customers`) sem captcha/limite → poluição de dados e
    consumo (e tampering por e-mail, SEC-001).
  - `POST /api/customers/social-login` — cada chamada dispara uma **requisição
    HTTP de saída** ao provedor (Google/Apple/Facebook). Sem limite ⇒ um atacante
    força o servidor a fazer chamadas externas ilimitadas (**amplificação**,
    custo, e estouro do rate limit do provedor).
  - `POST /api/cards` — **criação em massa de cartões** (vínculo arbitrário), sem auth/limite.
  - `GET /api/qr/{id}` e `GET /api/cards/{cardId}/redeem-qr` — **emissão ilimitada
    de tokens** HMAC (custo de CPU; sem persistência até consumo), sem auth/limite.
  - **Tamanho de corpo:** não há limite explícito de tamanho de payload JSON;
    nos endpoints públicos (que aceitam JSON), corpos grandes podem consumir memória.
- **Impacto:** DoS de baixo custo, poluição de base de dados, amplificação de
  chamadas externas e abuso dos fluxos de cadastro/QR. Complementa SEC-006 (login)
  e SEC-017 (PIN), também sem rate limit.
- **Recomendação:** rate limit por IP/identidade nos endpoints públicos
  (ex.: Bucket4j / filtro), captcha/anti-automação no cadastro, *throttle* no
  social-login, limite de tamanho de requisição (`spring.servlet.multipart` /
  `server.tomcat.max-swallow-size` / validação), e **autenticar** cards/qr (SEC-001/002/004).
- **Status:** Aberto.

### SEC-033 — Listagens sem paginação nem limite máximo
- **Severidade:** Baixa
- **CVSS:** `AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:L` (~3.1).
- **Categoria:** OWASP API4:2023.
- **Local:** [`CardService.getCustomerCards`](src/main/java/com/app/carimbai/services/CardService.java:31) (`findByCustomerIdWithProgram`), [`ProgramService.listPrograms`](src/main/java/com/app/carimbai/services/ProgramService.java:82).
- **Evidência segura:** nenhum endpoint usa `Pageable`/`limit`/`offset` (confirmado
  por busca) — as listagens retornam **todos** os registros. `GET /api/cards/customer/{id}`
  e `GET /api/merchants/{id}/programs` devolvem o conjunto completo. Hoje o tamanho
  é **limitado pelos dados do tenant** (poucos cartões/programas por entidade), então
  o risco é baixo; porém **não há teto** nem infraestrutura de paginação para crescer com segurança.
- **Impacto:** baixo no momento; cresce conforme a base. Não há `limit` controlado
  pelo atacante (não existe o parâmetro), então não há o vetor clássico de
  `limit` gigante — apenas ausência de teto.
- **Recomendação:** adotar `Pageable` com **tamanho máximo de página** nas
  listagens (e em qualquer endpoint de listagem futuro).
- **Status:** Aberto.

### SEC-034 — Resgate de recompensa sem PIN e sem QR do cliente (controles anti-fraude opcionais/contornáveis)
- **Severidade:** Média
- **CVSS:** `AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N` (~6.5).
- **Categoria:** OWASP API6:2023 (Unrestricted Access to Sensitive Business Flows) / lógica de negócio.
- **Local:** [`RedeemService.redeem`](src/main/java/com/app/carimbai/services/RedeemService.java:41), [`RedeemRequest`](src/main/java/com/app/carimbai/dtos/RedeemRequest.java).
- **Evidência segura:** o resgate (ação de **maior impacto financeiro** — emite a
  recompensa) tem dois controles anti-fraude, mas **ambos são opcionais**:
  - **PIN contornável:** toda a verificação de PIN está dentro de
    `if (redeemRequest.locationId() != null) { … requirePin … validateCashierPin … }`.
    **Omitindo `locationId`, o bloco inteiro é pulado** e o resgate prossegue
    **sem PIN** (mesmo com `requirePinOnRedeem=true`, que é o default fail-safe — só
    aplicado quando há location). O reward é criado com `location=null`.
  - **QR do cliente opcional:** `redeemRequest.redeemQr` é opcional; o resgate
    aceita apenas `cardId`. Logo, a "presença do cliente" (token REDEEM_QR com
    anti-replay) **não é exigida**.
  - Controles **mandatórios restantes:** apenas staff autenticado `CASHIER/ADMIN` +
    escopo de merchant + `status == READY_TO_REDEEM`.
- **Impacto:** um caixa autenticado pode **resgatar recompensas de qualquer cartão
  pronto do seu merchant** sem PIN e sem o cliente presente (ex.: emitir prêmios
  para si/cúmplices) — fraude de fidelidade. Perde-se também a auditoria de
  `location` quando omitida.
- **Recomendação:** aplicar a política de PIN com base no **merchant/programa**
  (não condicionada a `locationId`); exigir `locationId` válido no resgate; e/ou
  **exigir o token REDEEM_QR** (presença do cliente, com anti-replay) para resgatar.
  Derivar `location` do contexto/token. Mudança de fluxo de negócio → **propor antes**.
  Testes: `TC-BIZ-05/06`.
- **Status:** Aberto.

### SEC-035 — Parâmetros de programa sem validação de regra de negócio
- **Severidade:** Baixa
- **CVSS:** `AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:L` (~3.5).
- **Categoria:** lógica de negócio / validação.
- **Local:** [`CreateProgramRequest`](src/main/java/com/app/carimbai/dtos/admin/CreateProgramRequest.java), [`ProgramService`](src/main/java/com/app/carimbai/services/ProgramService.java).
- **Evidência segura:** `ruleTotalStamps`, `expirationDays`, `sortOrder` não têm
  `@Min`/`@Positive`. Um ADMIN pode criar programa com `ruleTotalStamps = 0`/negativo,
  gerando estado inconsistente: com `needed=0`, o primeiro selo lança
  `CardReadyToRedeemException` e o status nunca vira `READY_TO_REDEEM` → cartão
  **travado** (não carimba nem resgata). Sem validação de `startAt <= endAt`.
- **Impacto:** baixo (somente ADMIN do próprio merchant), mas gera dados/estado
  inconsistentes e potencial "DoS" do próprio programa.
- **Recomendação:** `@Min(1)` em `ruleTotalStamps`, `@PositiveOrZero` em `sortOrder`,
  `@Positive` em `expirationDays`, e validar `startAt <= endAt`. Pequena e segura
  (complementa SEC-022 / FIX-07).
- **Status:** Aberto.

### SEC-021 — Confirmação de BOLA no lado do cliente (cards/qr) — referência cruzada
- **Severidade:** ver SEC-001 (Crítica) / SEC-002 / SEC-004.
- **Categoria:** OWASP API1:2023 (BOLA).
- **Local:** [`CardService.getCustomerCards`](src/main/java/com/app/carimbai/services/CardService.java:31), `getOrCreateCard`, `generateRedeemQr`.
- **Evidência segura (Fase 6):** confirmado que nenhuma das operações de cartão
  verifica dono/escopo — `getCustomerCards` retorna via
  `cardRepository.findByCustomerIdWithProgram(customerId)` para **qualquer**
  `customerId`; `getOrCreateCard` vincula `programId`/`customerId` arbitrários;
  `generateRedeemQr` emite token para qualquer `cardId`. Como as rotas
  `/api/cards/**` e `/api/qr/**` são `permitAll` (SEC-001/002/004), isso é BOLA
  **sem autenticação**.
- **Recomendação:** tratado em SEC-001/002/004 (autenticar cliente + escopar ao dono). Esta entrada apenas registra a confirmação na Fase 6 e os testes `TC-AUTHZ-05/06`.
- **Status:** Aberto (consolidado em SEC-001/002/004).

### SEC-019 — Sem fluxo de troca/recuperação de senha (e requisitos para quando existir)
- **Severidade:** Informativa
- **Local:** ausência de endpoint; migration `V2026.20…__create_password_reset_tokens.sql` existe em outra branch (feature planejada).
- **Evidência segura:** não há `change-password` nem `forgot/reset password` na árvore atual. Hoje a senha só é definida na criação do staff (por ADMIN). Não há, portanto, vulnerabilidade de reset — mas também não há recuperação segura, e o recurso está planejado.
- **Requisitos para a implementação futura (checklist Fase 5 do plano):** token de reset **aleatório (≥128 bits), de uso único, com expiração curta**, nunca logado; resposta **genérica** independentemente de o e-mail existir (anti-enumeração); invalidar sessões/tokens após troca; rate limit no pedido de reset (SEC-006/API4); exigir senha forte (SEC-016).
- **Recomendação:** ao implementar, seguir o checklist acima e revalidar na Fase 5/13. Pré-requisito: provedor de e-mail.
- **Status:** Aberto (informativo / gap funcional).

### SEC-013 — Dados pessoais sem criptografia em repouso, sem TLS garantido ao banco e sem retenção definida
- **Severidade:** Baixa (risco LGPD)
- **Local:** migrations / entidades (`merchants.document`, `stamps.ip`, `stamps.user_agent`); `application*.yaml` (datasource).
- **Evidência segura:** documento (CNPJ/CPF) e metadados de selo (IP/User-Agent)
  armazenados **em claro**, sem política de retenção/expurgo aparente. **Nenhuma
  criptografia/pseudonimização em nível de coluna.**
- **Complemento Fase 8 — TLS para o banco:** os datasources versionados
  (`application-dev.yaml`, `application-local.yaml`) apontam para `localhost`
  **sem `sslmode`**. Em produção a URL vem de env (`CARIMBAI_DB_URL`) e **não foi
  possível confirmar** que usa `sslmode=require`/TLS — `PENDENTE`. Se o banco for
  remoto sem TLS, dados pessoais trafegam em claro entre app e banco.
- **Recomendação:** (1) avaliar cifrar/pseudonimizar `document` (e dados de
  contato) em repouso, ou no mínimo **criptografia de disco/volume** na VPS;
  (2) **exigir `sslmode=require`** (ou superior) na conexão de produção quando o
  banco for remoto; (3) definir retenção/expurgo (ver `PRIVACY_ACTION_ITEMS.md`).
  Fases 8, 10 e 14.
- **Status:** Aberto.

### SEC-014 — Verificador Facebook cria `HttpClient` por instância e degrada falha de proof
- **Severidade:** Informativa
- **Local:** [`FacebookTokenVerifier.java`](src/main/java/com/app/carimbai/services/social/FacebookTokenVerifier.java).
- **Evidência segura:** se `appSecret` ausente, `computeAppSecretProof` retorna `""` e segue a chamada à Graph API (sem o proof). Em produção, `appsecret_proof` deve ser obrigatório.
- **Recomendação:** tornar o `appsecret_proof` obrigatório quando o app exige; falhar caso o segredo não esteja configurado. Fase 5/7.
- **Status:** **Fechado (Fase 15, FIX-18)** — falha de cálculo do proof agora lança em vez de seguir sem proof. Ver `RETEST_REPORT.md`.

### SEC-015 — Dados pessoais (id/nome/e-mail) registrados em log no erro do login Facebook
- **Severidade:** Baixa (risco LGPD)
- **CVSS:** `AV:L/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N` (~3.3).
- **Categoria:** OWASP A09 (Logging Failures) / exposição de dados pessoais em logs.
- **Local:** [`FacebookTokenVerifier.java:57`](src/main/java/com/app/carimbai/services/social/FacebookTokenVerifier.java).
- **Evidência segura:** `log.warn("Facebook Graph API retornou {}: {}", response.statusCode(), response.body())` — o corpo da Graph API (`fields=id,name,email`) é gravado no log no caminho de não-200, expondo **id, nome e e-mail** do titular em arquivos de log.
- **Impacto:** dados pessoais persistidos em logs (retenção/acesso indefinidos), ampliando a superfície de vazamento.
- **Recomendação:** logar apenas `statusCode` (e, se necessário, um campo `error.code` específico), nunca o body bruto. **Correção pequena e segura** (trocar `response.body()` por `response.statusCode()`).
- **Status:** **Fechado (Fase 15, FIX-09)** — o log passou a registrar só `statusCode`; sem PII. Ver `RETEST_REPORT.md`.

---

## 4-bis. Controles positivos observados (não são achados)

- Tokens de QR: HMAC-SHA256 + TTL curto + **anti-replay por nonce** + comparação
  em **tempo constante** ([`StampTokenService`](src/main/java/com/app/carimbai/services/StampTokenService.java)).
- **Isolamento multi-tenant** validado no backend em selo e resgate
  (cartão/localização checados contra o merchant do token). **Atenção:** esse
  mesmo controle **falta** nos endpoints de gestão (SEC-020).
- **Sem escalação vertical** (Fase 6): `@PreAuthorize` por papel funciona; o
  papel vem do token (não do payload do usuário) e **não há endpoint de
  auto-atualização** que permita *mass assignment* de `role`/`isAdmin`. Um
  CASHIER não alcança endpoints ADMIN.
- `updateProgram` valida que o `programId` pertence ao `merchantId` do path
  (evita mismatch de IDs dentro da própria requisição) — porém não escopa ao
  token (SEC-020).
- `switch-merchant` só permite alternar para um merchant ao qual o staff tem
  **vínculo ativo** (`AuthService.switchMerchant`) — não dá para assumir um
  merchant arbitrário.
- **Superfície de injeção limpa (Fase 7):**
  - **Sem SQL Injection** — todos os repositórios usam *derived queries* do Spring
    Data ou **JPQL parametrizado** (`:customerId`, `:cardId`, `:since`); nenhuma
    query nativa nem concatenação de valores; o `+` em `CardRepository` apenas
    monta o template JPQL, não interpola valores.
  - **Sem Command Injection** — nenhum `Runtime.exec`/`ProcessBuilder`/shell.
  - **Sem upload de arquivos** — nenhum `MultipartFile` (o `imageUrl` é string, não upload).
  - **Sem Path Traversal** — nenhum acesso a `File`/`Paths`/serving de arquivos.
  - **Sem SSRF relevante** — HTTP de saída só para hosts fixos (Apple JWKS, Google,
    Graph do Facebook); o host não é controlado pelo usuário (ressalva menor: o
    token do Facebook entra como query param sem URL-encode — não altera o host).
  - **CSRF**: auth por header `Bearer` + `STATELESS` + sem cookie ⇒ `csrf(disable)`
    correto (SEC-009).
- **Sem vazamento de credenciais nas respostas (Fase 8):** nenhuma resposta/mapper
  serializa `passwordHash`/`pinHash`/`token`/`secret` (confirmado por busca em
  `mappers/` e `dtos/`). **Entidades JPA nunca são expostas diretamente** — todos
  os endpoints retornam DTOs/records. `StampResponse`/`RedeemResponse`/`AdminCardResponse`
  não carregam PII direta (apenas IDs/contadores).
- **Logs sem senhas/tokens (Fase 9):** nenhum log registra senha, hash, JWT, HMAC
  ou token de reset (logging é esparso). `stamps`/`rewards` oferecem trilha forense
  parcial do fluxo de selo/resgate (cashier/location/user-agent/token). Único
  vazamento de PII em log é o corpo da Graph API do Facebook (SEC-015).
- **Infra (Fase 10):** **Actuator seguro por padrão** (só `/actuator/health`
  exposto; demais em `denyAll`). **Headers padrão do Spring Security presentes**
  (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`). Deploy de produção
  **não usa Docker** (jar via systemd), reduzindo o impacto do hardening de container.
  Segredos do deploy em **GitHub Secrets** (não no código). CORS com `allowCredentials=false`.
- **Consumo de recursos (Fase 11):** o fluxo de negócio mais sensível — acúmulo
  de selos — **tem rate limit por cartão** (120s) + **idempotência** + lock
  otimista. **Não há endpoint de exportação em massa** (sem vetor de dump hoje) e
  **não há envio de e-mail/SMS** no backend atual (sem vetor de abuso de
  mensageria). Demais lacunas de rate limit em SEC-032/033/006/017.
- **Lógica de negócio (Fase 12) — defesas do acúmulo de selos são robustas:**
  - **Payload imutável:** o token de selo é assinado por HMAC sobre
    `type|idRef|nonce|exp` ⇒ **não dá para manipular `cardId`/`exp`** nem trocar o
    tipo (CUSTOMER_QR ≠ REDEEM_QR, ambos assinados — sem *type confusion*).
  - **Limiar vem do servidor:** `needed` vem de `program.ruleTotalStamps`, não do payload.
  - **Anti-race:** `Card` tem `@Version` (lock otimista) ⇒ selos/resgates
    concorrentes no mesmo cartão → 2º commit falha com 409; `Math.min(.., needed)`
    impede ultrapassar o limiar; status `READY_TO_REDEEM` bloqueia novos selos.
  - **Anti-replay/idempotência:** nonce único por token + `Idempotency-Key` único.
  - **Sem fluxos de pagamento/cupom/transferência/convite** no domínio ⇒ essas
    classes de abuso não se aplicam. Ressalva: controles do **resgate** são
    contornáveis (SEC-034).
- Senhas e PIN com **BCrypt**; resposta de login genérica para credencial inválida.
- **Idempotência** no selo (`Idempotency-Key` + `IdempotencyKey`) e lock otimista
  no `Card` (tratado no handler).
- Filtro JWT recarrega o usuário e exige `active=true` a cada request (kill switch real).
- CORS com origens restritas e `allowCredentials=false`.
- **Sessão `STATELESS`** (sem `JSESSIONID` para auth) ⇒ sem session fixation.
- JWT assinado **HS256** via `verifyWith(SecretKey)` ⇒ sem *algorithm confusion*/`alg=none`.
- Login com resposta **genérica** (`Invalid credentials`) para usuário/senha inválidos.
- **Verificação de token social robusta** (Fase 5): Apple e Google validam
  **assinatura, issuer, audience e expiração** ([`AppleTokenVerifier`](src/main/java/com/app/carimbai/services/social/AppleTokenVerifier.java) via JWKS com cache; [`GoogleTokenVerifier`](src/main/java/com/app/carimbai/services/social/GoogleTokenVerifier.java) com `audience`). Ressalva: Facebook depende do `appsecret_proof` opcional (SEC-014).

---

## 7. Riscos LGPD (visão inicial)

| ID | Risco | Dados afetados | Impacto | Recomendação | Status |
|---|---|---|---|---|---|
| LGPD-01 | Acesso/alteração de dados de cliente sem autenticação | nome, e-mail, telefone | Alto | SEC-001/004 | Aberto |
| LGPD-02 | Enumeração de clientes e hábitos de consumo | e-mail, vínculos de loja | Médio | Autenticar e escopar | Aberto |
| LGPD-03 | Sem criptografia em repouso / retenção indefinida | CNPJ/CPF, IP, User-Agent | Médio | SEC-013 | Aberto |
| LGPD-04 | Possível tratamento de dados de menores sem verificação | dados de cliente | A avaliar | Fase 14 | Aberto |
| LGPD-05 | Segredos versionados (risco de incidente) | credenciais/tokens | Alto | SEC-003 | Aberto |

> Mapeamento completo → **Fase 2** (`LGPD_DATA_MAPPING.md`). Pendências
> jurídicas/operacionais → **Fase 14** (`PRIVACY_ACTION_ITEMS.md`).

---

## 8. Correções propostas

> Plano detalhado e priorizado é a **Fase pós-análise** (`SECURITY_FIX_PLAN.md`).
> Nada deve ser alterado sem autorização explícita, fase por fase. Resumo de
> direção:

| Prioridade | Correção | Arquivos prováveis | Risco de quebra | Status |
|---|---|---|---|---|
| 1 | Autenticação de cliente + escopo em `/api/cards/**` e `/api/qr/**` | `SecurityConfig`, controllers de card/qr, novo fluxo de sessão | Alto (muda contrato/PWA) | Proposto |
| 2 | Remover fallbacks de segredo e rotacionar | `application-*.yaml`, deploy/env | Médio | Proposto |
| 3 | Desabilitar Swagger em produção | `application` por profile, `SecurityConfig` | Baixo | Proposto |
| 4 | Rate limiting em login/cadastro/social | filtro/bucket | Médio | Proposto |
| 5 | Higienizar mensagens de erro + handler genérico | `GlobalExceptionHandler` | Baixo | Proposto |
| 6 | Hardening de container/infra | `Dockerfile`, deploy | Baixo | Proposto |

---

## 9. Próximos passos recomendados

1. **Preencher as Regras de Engajamento (seção 2.1)** antes de qualquer teste
   ativo (ambiente autorizado = staging/local, janela, contato, responsável).
2. **Fase 2** — mapeamento LGPD detalhado (`LGPD_DATA_MAPPING.md`).
3. **Fase 3** — secrets na árvore **e no histórico do git** (gitleaks); avaliar
   rotação e limpeza de histórico para SEC-003.
4. **Fase 5/6** — validar dinamicamente SEC-001/002/004 (BOLA/auth) em ambiente
   autorizado; testes A-vs-B e endpoints sem token.
5. **Fase 4** — dependências (`mvn org.owasp:dependency-check`, `trivy`).
6. **Fases 7–11** — validação de entrada/CSRF, exposição de dados, lógica de
   negócio, rate limiting, logs.
7. **Fase 10** — confirmar postura de produção: HTTPS, headers, Swagger, banco
   não exposto, container não-root.
8. **Fase 14** — checklist LGPD comercial (Encarregado/DPO, política de
   privacidade, RIPD, retenção, notificação de incidente ANPD em 3 dias úteis).

---

## 10. Pendências antes de comercializar (preliminar)

- [ ] Implementar autenticação do cliente e escopar dados/QR ao dono (SEC-001/002/004).
- [ ] Remover segredos do código e rotacionar (SEC-003); confirmar injeção por env em produção.
- [ ] Proteger/desabilitar Swagger e endpoints de doc em produção (SEC-005).
- [ ] Rate limiting em autenticação e cadastro (SEC-006).
- [ ] Definir retenção e minimização de dados pessoais (IP/User-Agent/documento) (SEC-013).
- [ ] Avaliar tratamento de dados de menores (Fase 14).
- [ ] Política de Privacidade, Termos, Encarregado (DPO) e plano de resposta a incidente (Fase 14).

---

> **Status do plano:** Fases 1–14 concluídas + **Fase 15 (primeira rodada de
> reteste)**. Plano de correção em `SECURITY_FIX_PLAN.md`; checklist LGPD em
> `PRIVACY_ACTION_ITEMS.md`; reteste em `RETEST_REPORT.md`. Na **Fase 15** foram
> aplicados os *quick wins* seguros (FIX-06/07/09/13/16/17/18/24) — `./mvnw test`
> → BUILD SUCCESS (9/9); fluxos de auth/autz e contrato de API **não** alterados.
> Fechados: SEC-014, SEC-015, SEC-016, SEC-035. Parciais: SEC-007, SEC-022, SEC-029.
> Achados Crítico/Alto (SEC-001/002/003/004/020) seguem **abertos** e exigem
> proposta antes (FIX-01/02/03).

---

## Anexo — Fase 9: Logs, auditoria e monitoramento

### A. Vazamento de dados em logs

| Evento de log | Local | Conteúdo | PII/segredo? | Ação |
|---|---|---|---|---|
| Erro Graph API Facebook | `FacebookTokenVerifier.java:57` | `response.body()` (id, nome, e-mail) | **Sim (PII)** | Logar só `statusCode` — SEC-015 |
| Falha verificação social | Google:48 / Apple:87 / FB:81,94 | `e.getMessage()` | Improvável | Revisar; não logar token |
| `Redeem type REDEEM` | `RedeemService.java:77` | fixo | Não | OK (remover ruído) |
| SQL | `show-sql:true` | SQL (params como `?`) | Estrutura, não valores | Desabilitar em prod — SEC-011 |
| Senha/JWT/HMAC/reset | — | — | **Nenhum** (não há) | OK (positivo) |

### B. Mascaramento sugerido (quando o dado precisar aparecer)

- **E-mail:** `j***@dominio.com`. **Telefone:** só os 4 últimos. **CPF/CNPJ:**
  `***.***.***-12`. **Tokens/JWT/HMAC/PIN/senha:** **nunca logar** (nem mascarados).
- Implementar via *converter*/*masking layout* do Logback ou um utilitário central
  de mascaramento aplicado antes de logar; evitar `log("...", objetoComPII)`.

### C. Eventos mínimos de auditoria (SEC-026)

Registrar (sem PII sensível; com `actorId`, `merchantId`, `ip`, `timestamp`,
`resultado`):
- Login **bem-sucedido** e **falho** (sem senha) — detecta brute force (SEC-006).
- Alteração de senha / `setPin` / criação de staff.
- Solicitação/uso de reset de senha (quando existir — SEC-019).
- Ações administrativas: criar/editar merchant, programa, localização.
- **Falhas de autorização** (tentativas cross-tenant — SEC-020).
- Exclusão e **exportação** de dados pessoais (direitos do titular — quando existir).
- Resgate de recompensa (já há trilha parcial via `rewards`).

### D. Retenção e controle de acesso (SEC-027)

- **Retenção:** definir prazo (ex.: logs de aplicação 30–90 dias; auditoria de
  segurança 6–12 meses), com **expurgo automático**; não reter PII além do necessário.
- **Rotação:** *file appender* com rotação por tamanho/data (Logback `RollingFile`)
  ou centralização (ex.: stack de logs) com retenção configurada.
- **Acesso restrito:** apenas administradores; logs fora do alcance público;
  em VPS, restringir leitura do journal/arquivos a usuários autorizados.
- **Integridade:** considerar log de auditoria *append-only* para eventos sensíveis.

### E. Critério de aceite da Fase 9

- [x] Logs sensíveis identificados (SEC-015; nenhum segredo/credencial em log).
- [x] Estratégia de mascaramento sugerida (§B).
- [x] Eventos mínimos de auditoria definidos (§C — SEC-026).
- [x] Retenção e acesso a logs avaliados (§D — SEC-027).

> Nenhuma alteração de código foi feita na Fase 9.

---

## Anexo — Fase 3: Varredura de secrets (árvore atual + histórico do git)

### A. Metodologia e escopo

- **Ferramentas:** `gitleaks`/`trufflehog` **não estão instalados** no ambiente;
  a varredura foi feita manualmente com `git` (pickaxe `-S`, `git grep` por blob,
  `git log --diff-filter=A`) + `grep` textual na árvore. **Recomenda-se** rodar
  `gitleaks detect --source .` (histórico) e `gitleaks detect --source . --no-git`
  (árvore) no CI quando disponível, para cobertura por regras/entropia.
- **Escopo do histórico:** **todas as refs**, **135 commits** (`git rev-list --all`).
- **Valores sempre mascarados** neste relatório (regra 7 do plano).

### B. Resultados — secrets encontrados (todos mascarados)

| Item | Onde | Árvore atual? | Histórico? | Tipo | Severidade |
|---|---|---|---|---|---|
| `CARIMBAI_JWT_SECRET` fallback `h9V$3u…[REDACTED]` | `application-dev.yaml`, `application-local.yaml` | **Sim (HEAD)** | **Sim** (desde `c6f0075`, 2025-12-02; 4 commits) | Chave de assinatura JWT (real) | **Alta** → SEC-003 |
| `CARIMBAI_HMAC_SECRET` fallback `dev-…[REDACTED]` | `application-dev.yaml`, `application-local.yaml`, `docs/carimbai-api-esqueleto.md` | Sim | Sim | Placeholder (não é segredo real) | Baixa → SEC-003 |
| Senha do datasource `c…[REDACTED]` / `p…[REDACTED]` | `application.yaml`, `application-local.yaml` | Sim | Sim (poucos valores distintos, todos dev/local) | Credencial de banco (dev) | Baixa → SEC-003 |
| Senha Postgres/pgAdmin `p…` / `a…[REDACTED]` | `docker-compose.yaml` | Sim | Sim | Credencial de container (dev) | Baixa → SEC-010 |

### C. Resultados — verificações negativas (nada encontrado, bom)

- ❌ Nenhum arquivo **`.env`** jamais commitado (e `.gitignore` já ignora `.env`).
- ❌ Nenhuma **chave privada** (`-----BEGIN ... PRIVATE KEY-----`, `.pem`, `.key`, `.jks`, `.p12`, `.pfx`).
- ❌ Nenhum **dump `.sql` com dados reais** — apenas DDL de migrations Flyway.
- ❌ Nenhuma **connection string com credencial embutida** (`postgres://user:pass@host`) — incluindo os commits "railway"/"test envs".
- ✅ **Segredos OAuth** (Google/Apple/Facebook) sempre como placeholder de env
  (`${CARIMBAI_FACEBOOK_APP_SECRET:}` etc.) — nunca valor real.
- ✅ **CI/CD** (`deploy.yml`) usa **GitHub Secrets** (`SSH_HOST`, `SSH_USER`,
  `SSH_PRIVATE_KEY`) — sem credencial hardcoded.

### D. Playbook de remediação (SEC-003)

**1. Rotacionar o segredo JWT (prioridade máxima):**
- Gerar novo valor forte (≥256 bits) e injetar **apenas via env** (`CARIMBAI_JWT_SECRET`)
  em todos os ambientes; remover o fallback do código.
- A rotação **invalida todos os JWTs atuais** (staff precisa relogar) — efeito
  aceitável e desejado.
- Rotacionar também `CARIMBAI_HMAC_SECRET` (tokens de QR expiram em ~90s, impacto mínimo)
  e as senhas de banco que tenham ido a qualquer ambiente real.

**2. Remover os fallbacks do código:**
- Em `application-dev.yaml` / `application-local.yaml`, trocar
  `${CARIMBAI_JWT_SECRET:h9V$…}` por `${CARIMBAI_JWT_SECRET}` (falha se ausente),
  ou mover defaults de dev para `.claude/settings.local.json`/`.env` **não versionado**.
- *(Mudança pequena, mas altera comportamento de inicialização em dev — proposta
  aqui para a fase de correção; não aplicada nesta fase de análise.)*

**3. Limpar o histórico do git (após rotacionar):**
- A rotação é o que realmente neutraliza o vazamento. A limpeza de histórico é
  complementar (evita confusão/reuso). Como o repositório tem remoto e PRs,
  reescrever histórico exige coordenação (force-push + reclonagem por todos).
- Opção recomendada — **BFG**:
  ```bash
  # arquivo replacements.txt contendo: h9V$3uP...==>***REMOVED***
  bfg --replace-text replacements.txt
  git reflog expire --expire=now --all && git gc --prune=now --aggressive
  git push --force
  ```
- Alternativa — **git filter-repo**:
  ```bash
  git filter-repo --replace-text replacements.txt
  ```
- Após reescrever: invalidar tokens/PRs em aberto e avisar a equipe para reclonar.

**4. Prevenção:**
- Adicionar `gitleaks` como hook de pre-commit e/ou step no GitHub Actions.
- Garantir que nenhum `application-*.yaml` versionado contenha valor real —
  só placeholders ou referências `${ENV}` sem default sensível.

### E. Critério de aceite da Fase 3

- [x] Nenhum secret real exposto no relatório (todos mascarados).
- [x] Histórico do git verificado (135 commits, todas as refs).
- [x] Arquivos sensíveis identificados (e confirmado que `.env`/chaves/dumps **não** existem).
- [x] Recomendações de `.gitignore` feitas (já cobre `.env`; manter yaml sem segredo real).
- [x] Recomendação de rotação para o segredo vazado (JWT) — prioridade máxima.
