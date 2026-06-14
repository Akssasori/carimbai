# Mapeamento LGPD de Dados Pessoais — carimbai (backend)

> Gerado pela **Fase 2** do `SECURITY_LGPD_PLAN.md`. Análise **estática**, sem
> alteração de código. Base: entidades JPA (`models/`), migrations
> (`db.migration/`), DTOs (`dtos/`), controllers/services e pontos de log.
> Itens sem clareza estão marcados como `PENDENTE`.
>
> **Bases legais e retenções são SUGESTÕES preliminares** e devem ser
> confirmadas por revisão jurídica (LGPD) antes da comercialização (ver Fase 14
> / `PRIVACY_ACTION_ITEMS.md`). Riscos detalhados em `SECURITY_REPORT.md`.

## 1. Resumo

- **Titulares envolvidos:** clientes finais (consumidores), usuários de staff
  (lojistas/caixas) e o próprio estabelecimento (merchant — pode ser MEI/pessoa
  física via `document`).
- **Dados sensíveis (art. 11 LGPD):** **nenhum identificado** (sem saúde,
  biometria, origem racial, convicção, etc.).
- **Atenção especial:** possível tratamento de **dados de menores** — não há
  verificação de idade no cadastro/login de cliente (`PENDENTE` — Fase 14).
- **Dado pessoal mais exposto:** dados de contato do cliente (nome, e-mail,
  telefone), acessíveis/alteráveis **sem autenticação** (ver SEC-001).
- **Dado potencialmente fiscal:** `merchants.document` (CNPJ, ou **CPF** se o
  lojista for pessoa física) — armazenado em claro.

## 2. Tabela de dados pessoais

| Dado | Onde é coletado | Onde é armazenado | Finalidade | Base legal provável | Retenção sugerida | Sensível? | Risco | Observações |
|---|---|---|---|---|---|---|---|---|
| Nome do cliente | `POST /api/customers/login-or-register`; `POST /api/customers/social-login` (vem do provedor) | `fidelity.customers.name` (VARCHAR 120) | Identificação/personalização do cartão | Execução de contrato / legítimo interesse | Enquanto conta ativa + prazo legal | Não | **Alto** | Retornado em `CustomerLoginResponse` por e-mail sem auth (SEC-001) |
| E-mail do cliente | `login-or-register`, `social-login`, `POST /api/customers` (admin) | `fidelity.customers.email` (VARCHAR 160, UNIQUE) | Identificação, matching de conta, contato | Execução de contrato | Enquanto conta ativa | Não | **Alto** | Enumerável/alterável sem auth; oráculo de existência (SEC-001) |
| Telefone do cliente | `login-or-register`, `POST /api/customers` (admin) | `fidelity.customers.phone` (VARCHAR 30) | Contato, matching de conta | Execução de contrato / consentimento | Enquanto conta ativa | Não | **Alto** | Sobrescrito por e-mail sem auth (SEC-001); sem validação de formato |
| ID do provedor social | `social-login` (`google:sub` / `apple:sub` / `facebook:id`); `login-or-register` | `fidelity.customers.provider_id` (VARCHAR 80) | Vincular identidade social ao cliente | Execução de contrato | Enquanto conta ativa | Não | Médio | Identificador estável do titular no provedor externo |
| E-mail do staff | `POST /api/staff-users` (admin); usado em `POST /api/auth/login` | `core.staff_users.email` (VARCHAR 160, UNIQUE) | Autenticação do operador | Execução de contrato (relação com lojista) | Enquanto vínculo ativo | Não | Médio | Aparece no claim `email` do JWT e em `LoginResponse` |
| Senha do staff (hash) | `POST /api/staff-users`, `createStaffUser` | `core.staff_users.password_hash` (TEXT, **BCrypt**) | Autenticação | Execução de contrato | Enquanto vínculo ativo | Credencial | Médio | Hash seguro; **não** é exposto em respostas. Manter assim |
| PIN do caixa (hash) | `POST /api/admin/staff-users/{id}/pin` | `core.staff_users.pin_hash` (TEXT, **BCrypt**) | Confirmação de resgate no caixa | Execução de contrato / segurança | Enquanto vínculo ativo | Credencial | Médio | 4–10 chars; enviado via header `X-Cashier-Pin` (TLS obrigatório) |
| Documento do merchant (CNPJ/**CPF**) | `POST /api/merchants` (admin) | `core.merchants.document` (VARCHAR 20) | Identificação fiscal do estabelecimento | Obrigação legal / execução de contrato | Enquanto cadastro ativo + prazo fiscal | **Possível** (CPF de PF/MEI) | Médio | Em claro, sem máscara/validação; avaliar cripto em repouso (SEC-013) `PENDENTE` |
| Endereço da unidade | `POST /api/merchants/{id}/locations` (admin) | `core.locations.address` (VARCHAR 255) | Localização do estabelecimento | Execução de contrato | Enquanto unidade ativa | Não (PJ) | Baixo | Endereço do estabelecimento, não do titular consumidor |
| User-Agent | Header `User-Agent` em `POST /api/stamp` | `fidelity.stamps.user_agent` (TEXT) | Auditoria/anti-fraude do selo | Legítimo interesse | Definir (ex.: 6–12 meses) `PENDENTE` | Não | Médio | Dado pessoal indireto (dispositivo); retenção indefinida hoje |
| IP | (coluna existe) `fidelity.stamps.ip` (INET) | `fidelity.stamps.ip` | Auditoria/anti-fraude | Legítimo interesse | Definir `PENDENTE` | Não | Médio | **Não há gravação de IP no código atual** (`StampsService` só grava `user_agent`/location). Coluna ociosa — `PENDENTE`: remover ou popular com política |
| JWT de staff | Emitido no login; trafega no header `Authorization` | Cliente (frontend) — **armazenamento `PENDENTE`** | Sessão/autorização | Execução de contrato | TTL 8h | Sensível (operacional) | Médio | Contém claim `email`. Onde o frontend guarda o token? `PENDENTE` (Fase 8) |
| Token de QR (HMAC) | Emitido em `/api/qr/**`, `/api/cards/{id}/redeem-qr` | `ops.stamp_tokens` (nonce/sig/exp) — sem PII direta | Selo/resgate efêmero anti-replay | Execução de contrato | Expira ~90s; registro de nonce permanece | Não | Médio | Referencia `card_id`/`location_id`; emissão pública (SEC-002/004) |
| Token social (Google/Apple/Facebook) | Body de `social-login` | **Não armazenado** (só verificado) | Autenticação social | Consentimento (provedor) | Não retido | Sensível (credencial) | Médio | **Vaza em log** no caminho de erro do Facebook (ver §4) |

## 3. Pontos de coleta, processamento e saída

### 3.1. Coleta (entrada de dados pessoais)
- `POST /api/customers/login-or-register` — nome, e-mail, telefone, providerId (**sem autenticação**).
- `POST /api/customers/social-login` — token social → e-mail/nome/subject verificados no provedor.
- `POST /api/customers` (admin) — e-mail, telefone, providerId (rota efetivamente bloqueada por `denyAll`, ver SEC-001/§Fase 1).
- `POST /api/staff-users` (admin) — e-mail, senha, papel.
- `POST /api/admin/staff-users/{id}/pin` (admin) — PIN.
- `POST /api/merchants` / `/locations` (admin) — documento, endereço.
- `POST /api/stamp` — header `User-Agent` (e `X-Location-Id`).

### 3.2. Armazenamento
- PostgreSQL, schemas: `core` (merchants, locations, staff_users), `fidelity`
  (customers, cards, stamps, rewards, programs), `ops` (stamp_tokens).
- Senha e PIN sempre como **hash BCrypt**. Demais dados pessoais em **claro**.
- Sem criptografia/pseudonimização em repouso para documento/contato (`PENDENTE`).

### 3.3. Saída / compartilhamento
- **Respostas de API:** `CustomerLoginResponse` (nome/e-mail/telefone/providerId),
  `LoginResponse` (e-mail do staff + JWT), listagem de cartões (lojista/programa).
- **Terceiros (operadores/transferência internacional):**
  - **Google / Apple / Facebook** — verificação de token social (dados saem para
    fora do Brasil → **transferência internacional `PENDENTE`**, Fase 14).
  - **VPS de deploy** (host `PENDENTE`) — banco de produção reside aí.
- **Sem** analytics/storage/e-mail transacional externos detectados no backend.

## 4. Dados pessoais em logs

| Local | O que loga | PII? | Recomendação |
|---|---|---|---|
| [`FacebookTokenVerifier.java:57`](src/main/java/com/app/carimbai/services/social/FacebookTokenVerifier.java) | `response.body()` da Graph API (contém **id, nome, e-mail**) no caminho de erro | **Sim** | Não logar o body bruto; logar só status/código (ver SEC-015) — **Fase 9** |
| `application*.yaml` `show-sql: true` | SQL formatado (pode incluir valores) | Indireto | Desabilitar em produção (SEC-011) |
| Google/Apple verifiers (`log.warn(e.getMessage())`) | Mensagem de exceção | Improvável | Aceitável; revisar na Fase 9 |
| `RedeemService.java:77` `log.info("Redeem type REDEEM")` | Sem PII | Não | OK |
| `DevDataSeeder` `System.out` | Mensagem fixa (profile `stg`) | Não | OK |

## 5. Pendências (`PENDENTE`) identificadas na Fase 2

- [ ] **Idade do titular:** sem verificação → possível tratamento de dados de menores (Fase 14).
- [ ] **`merchants.document`:** confirmar se é CNPJ (PJ) ou também CPF (PF/MEI); definir máscara/validação e cripto/pseudonimização em repouso.
- [ ] **Retenção:** nenhuma política de expurgo para `customers`, `stamps.user_agent`/`ip`, `rewards`, `stamp_tokens`.
- [ ] **Coluna `stamps.ip`:** ociosa (não preenchida) — remover ou definir política de coleta.
- [ ] **Armazenamento do JWT no frontend:** verificar local seguro (Fase 8 — `../carimbai-app`).
- [ ] **Transferência internacional:** Google/Apple/Facebook (e cloud da VPS) — avaliar salvaguardas (Fase 14).
- [ ] **Processo do titular:** exclusão/exportação/correção de dados de cliente — **não há endpoint** para isso hoje (Fase 14).
- [ ] **Log do Facebook** com PII (SEC-015) — corrigir na Fase 9.

## 6. Critério de aceite da Fase 2

- [x] Dados pessoais listados.
- [x] Dados sensíveis sinalizados (nenhum sensível; alerta sobre menores).
- [x] Local de coleta identificado.
- [x] Local de armazenamento identificado.
- [x] Finalidade descrita.
- [x] Riscos de exposição anotados (cruzados com `SECURITY_REPORT.md`).
- [x] Pontos sem clareza marcados como `PENDENTE`.

> Nenhuma alteração de código foi feita nesta fase.
