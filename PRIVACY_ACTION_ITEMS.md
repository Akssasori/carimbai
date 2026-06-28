# Pendências de Privacidade e LGPD — carimbai

> **Fase 14** do `SECURITY_LGPD_PLAN.md` — checklist de **comercialização**.
> Consolida e completa o que foi iniciado na Fase 8. Baseado no **código e nas
> funcionalidades efetivamente encontradas** (não em suposições). Cada item indica
> se é resolvível **no código** ou se exige **revisão jurídica/negocial**.
>
> ⚠️ Este documento **organiza pendências**; **não substitui** assessoria jurídica
> de LGPD nem pentest independente. Prazos/obrigações refletem a regulamentação
> vigente e devem ser confirmados juridicamente antes de comercializar.
>
> Referências: `SECURITY_REPORT.md` (SEC-xxx), `LGPD_DATA_MAPPING.md`, `SECURITY_FIX_PLAN.md` (FIX-xx).

## Legenda
- 🛠️ **Técnico (código/infra)** · ⚖️ **Jurídico** · 🧭 **Operacional**.
- Resolução: **[CÓDIGO]** resolvível em código/infra · **[JURÍDICO]** exige advogado/DPO · **[NEGÓCIO]** decisão/processo.
- Prioridade: 🔴 Alta · 🟠 Média · 🟡 Baixa.

## 0. Contexto do tratamento (resumo factual)

- **Titulares:** clientes finais (consumidores), usuários de staff (lojistas/caixas), e o estabelecimento (merchant — pode ser **MEI/pessoa física** via `document`).
- **Dados pessoais:** nome, e-mail, telefone, `providerId` social (cliente); e-mail + hash de senha/PIN (staff); CNPJ/**CPF** (merchant); IP/User-Agent (selo); JWT (claim de e-mail). Detalhe em `LGPD_DATA_MAPPING.md`.
- **Dados sensíveis (art. 11):** **nenhum** identificado.
- **Atenção:** **possível tratamento de dados de menores** — sem verificação de idade.
- **Operadores/terceiros:** Google/Apple/Facebook (verificação de login social), provedor da **VPS** (banco/app), **Vercel** (hospedagem do PWA).
- **Sem** dados financeiros/pagamento, **sem** e-mail/SMS transacional, **sem** exportação em massa (hoje).

---

## 1. Itens técnicos (🛠️ resolvíveis no código/infra)

| # | Item | Resolução | Prioridade | Achado/FIX |
|---|---|---|---|---|
| PRV-T01 | Autenticar o cliente e **escopar dados/QR ao titular** (hoje qualquer um lê/altera PII por ID/e-mail) | [CÓDIGO] | 🔴 | SEC-001/002/004 · FIX-02 |
| PRV-T02 | **Endpoints de direitos do titular**: exclusão, exportação/acesso e correção dos dados do cliente (inexistentes) | [CÓDIGO]+[NEGÓCIO] | 🔴 | Fase 14 · novo |
| PRV-T03 | **Retenção/expurgo** automatizados (clientes inativos, `stamps.user_agent`/`ip`, `stamp_tokens`, rewards) | [CÓDIGO]+[NEGÓCIO] | 🟠 | SEC-013 |
| PRV-T04 | Não persistir PII de cliente em `localStorage` (guardar identificador opaco; limpar no logout) | [CÓDIGO] (frontend) | 🟠 | SEC-025 · FIX-12 |
| PRV-T05 | Proteger o JWT no cliente (cookie HttpOnly/Secure ou TTL curto + revogação) | [CÓDIGO] | 🟠 | SEC-024/012 · FIX-11/12 |
| PRV-T06 | **Remover PII de logs** (corpo da Graph API do Facebook) + mascaramento | [CÓDIGO] | 🟠 | SEC-015 · FIX-09 |
| PRV-T07 | **Logging de auditoria** (acesso/alteração/exclusão de dados pessoais; falhas de autorização) | [CÓDIGO] | 🟠 | SEC-026 |
| PRV-T08 | Criptografia/pseudonimização de `document` (CNPJ/CPF) em repouso, ou cripto de disco na VPS | [CÓDIGO]+[NEGÓCIO] | 🟠 | SEC-013 · FIX-14 |
| PRV-T09 | **TLS na conexão com o banco** em produção (`sslmode=require`) se remoto | [CÓDIGO] | 🟠 | SEC-013 (`PENDENTE`) |
| PRV-T10 | Minimizar campos nas respostas (ex.: não devolver `providerId` ao cliente) | [CÓDIGO] | 🟡 | Fase 8 |
| PRV-T11 | Coluna `stamps.ip` ociosa — remover ou definir coleta com base legal/retenção | [CÓDIGO] | 🟡 | LGPD_DATA_MAPPING |
| PRV-T12 | Desabilitar Swagger/`show-sql` em produção (reduz exposição) | [CÓDIGO] | 🟠 | SEC-005/011/028 · FIX-01 |
| PRV-T13 | Sanitização/validação de conteúdo renderizado (XSS) e `imageUrl` | [CÓDIGO] | 🟡 | SEC-023 |
| PRV-T14 | **Verificação de idade** no cadastro/login do cliente (se houver restrição a menores) | [CÓDIGO]+[JURÍDICO] | 🔴 | PRV-J04 |
| PRV-T15 | Suporte técnico ao **consentimento de cookies** (se o PWA usar cookies/analytics não essenciais) | [CÓDIGO] (frontend) | 🟡 | PRV-J06 |

## 2. Itens jurídicos (⚖️ exigem assessoria)

| # | Item | Resolução | Prioridade |
|---|---|---|---|
| PRV-J01 | **Política de Privacidade** publicada (dados, finalidade, base legal, retenção, direitos, contato do Encarregado) | [JURÍDICO] | 🔴 |
| PRV-J02 | **Termos de Uso** publicados | [JURÍDICO] | 🔴 |
| PRV-J03 | **Bases legais** confirmadas por dado (as do `LGPD_DATA_MAPPING.md` são sugestões) | [JURÍDICO] | 🔴 |
| PRV-J04 | **Tratamento de dados de menores** — definir política, verificação de idade e base legal/consentimento dos responsáveis | [JURÍDICO]+[CÓDIGO] | 🔴 |
| PRV-J05 | **Transferência internacional** (Google/Apple/Facebook; VPS/Vercel se fora do Brasil) — avaliar salvaguardas (cláusulas-padrão, adequação) | [JURÍDICO] | 🟠 |
| PRV-J06 | **Consentimento de cookies/analytics** no PWA, se houver cookies não essenciais (banner + registro do consentimento) | [JURÍDICO]+[CÓDIGO] | 🟡 |
| PRV-J07 | **Cláusulas de proteção de dados** nos contratos com lojistas (B2B): papéis controlador/operador, responsabilidades, incidentes | [JURÍDICO] | 🟠 |
| PRV-J08 | **Lista de operadores/suboperadores** (Google/Apple/Facebook, provedor da VPS, Vercel) com contratos/DPA | [JURÍDICO]+[NEGÓCIO] | 🟠 |
| PRV-J09 | **RIPD** (Relatório de Impacto à Proteção de Dados), ao menos simplificado — recomendado por tratar dados de contato em escala e **possíveis menores** | [JURÍDICO] | 🟠 |
| PRV-J10 | Definir **papéis controlador/operador** entre a plataforma carimbai e os lojistas (quem é controlador dos dados do cliente?) | [JURÍDICO]+[NEGÓCIO] | 🔴 |

## 3. Itens operacionais (🧭 processo/política/pessoa)

| # | Item | Resolução | Prioridade |
|---|---|---|---|
| PRV-O01 | **Encarregado (DPO) nomeado e publicado**, com **canal de contato** de privacidade (e-mail/formulário) | [NEGÓCIO] | 🔴 |
| PRV-O02 | **Plano de resposta a incidente** alinhado à ANPD (ver §4) | [NEGÓCIO]+[JURÍDICO] | 🔴 |
| PRV-O03 | **Registro de operações de tratamento (ROPA)** | [NEGÓCIO] | 🟠 |
| PRV-O04 | **Processo de atendimento ao titular** (exclusão/acesso/correção) com **prazo de resposta** definido | [NEGÓCIO] | 🟠 |
| PRV-O05 | **Backup e restauração** documentados, **criptografados** e com **restauração testada**; acesso restrito | [NEGÓCIO]+[CÓDIGO] | 🟠 |
| PRV-O06 | **Retenção e controle de acesso a logs** (quem acessa, por quanto tempo) | [NEGÓCIO]+[CÓDIGO] | 🟡 |
| PRV-O07 | Treinamento/processo interno mínimo de privacidade para quem opera a plataforma | [NEGÓCIO] | 🟡 |

---

## 4. Plano de resposta a incidente (Resolução CD/ANPD nº 15/2024)

Itens que o **plano de resposta a incidente** (PRV-O02) deve conter:

- **Prazo:** comunicar à **ANPD** e aos **titulares afetados** em **até 3 (três) dias úteis** a partir do conhecimento de incidente que possa acarretar **risco ou dano relevante**.
- **Comunicação não espera o fim da investigação:** admite-se **comunicação preliminar**, complementada em até **20 dias úteis**.
- **Via:** comunicação feita pelo **Encarregado (DPO)**, através do **formulário eletrônico da ANPD**.
- **Risco relevante é presumido** quando envolver: dados **sensíveis**, dados de **crianças/adolescentes/idosos**, dados **financeiros**, ou **senhas/credenciais**.
  - **Aplicação ao carimbai:** o sistema trata **hashes de senha/PIN de staff** e **possivelmente dados de menores** → enquadra-se na presunção de risco relevante. Logo, o plano deve estar pronto **antes** de comercializar.
- **Pré-requisitos técnicos do plano (cruzam com o código):**
  - **Detecção:** depende de **logging de auditoria** (PRV-T07/SEC-026) — hoje ausente.
  - **Trilha forense:** parcial via `stamps`/`rewards`; insuficiente para login/admin/acesso a dados.
  - **Contenção:** *kill switch* existe (desativar staff revoga JWT); falta revogação granular de token (SEC-012).

> Estes prazos refletem a regulamentação vigente; **confirmar com revisão jurídica**
> antes da comercialização.

---

## 5. Checklist mínimo de comercialização (visão consolidada)

- [ ] Política de Privacidade publicada — PRV-J01
- [ ] Termos de Uso publicados — PRV-J02
- [ ] Registro de operações de tratamento (ROPA) — PRV-O03
- [ ] **Encarregado (DPO)** nomeado e publicado, com canal de contato — PRV-O01
- [ ] Processo de **exclusão** de conta/dados (com prazo) — PRV-T02/PRV-O04
- [ ] Processo de **exportação/acesso** aos dados — PRV-T02/PRV-O04
- [ ] Processo de **correção** de dados — PRV-T02/PRV-O04
- [ ] Processo de revogação de consentimento (se aplicável) — PRV-J06
- [ ] **Consentimento de cookies/banner** (se PWA usar cookies/analytics não essenciais) — PRV-J06/PRV-T15
- [ ] Lista de operadores/suboperadores + DPA — PRV-J08
- [ ] **Transferência internacional** avaliada (Google/Apple/Facebook, VPS/Vercel) — PRV-J05
- [ ] **Retenção de dados** definida — PRV-T03/PRV-O06
- [ ] **RIPD** elaborado (ao menos simplificado) — PRV-J09
- [ ] **Plano de resposta a incidente** (3 dias úteis ANPD) — PRV-O02/§4
- [ ] Procedimento de **backup e restauração** documentado e testado — PRV-O05
- [ ] Cláusulas de proteção de dados nos contratos B2B — PRV-J07
- [ ] Papéis **controlador/operador** definidos (plataforma × lojista) — PRV-J10
- [ ] Tratamento de **dados de menores** definido — PRV-J04/PRV-T14

---

## 6. Riscos de privacidade mais críticos antes de comercializar

1. 🔴 **PII de clientes acessível/alterável sem autenticação** (PRV-T01 / SEC-001) — maior risco LGPD imediato e bloqueante.
2. 🔴 **Sem direitos do titular** (exclusão/exportação/correção) e **sem DPO/Política/Termos** (PRV-T02, PRV-J01/02, PRV-O01).
3. 🔴 **Dados de menores sem tratamento definido** (PRV-J04/PRV-T14).
4. 🔴 **Papéis controlador/operador indefinidos** entre plataforma e lojistas (PRV-J10).
5. 🟠 **Sem plano de resposta a incidente** apesar de tratar senhas/possíveis menores (PRV-O02 — presunção de risco relevante).
6. 🟠 **Token/PII em `localStorage`, PII em logs, cripto/TLS não confirmados** (PRV-T04/05/06/08/09).

## 7. O que é resolvível só no código (resumo para o time de dev)

Independem de jurídico e podem entrar no `SECURITY_FIX_PLAN.md`:
- Direitos do titular (endpoints de exclusão/exportação/correção) — PRV-T02 (precisa de PRV-T01 antes).
- Retenção/expurgo automatizado — PRV-T03.
- Tirar PII do `localStorage` / proteger JWT — PRV-T04/T05.
- Remover PII de logs + auditoria — PRV-T06/T07.
- Cripto em repouso + TLS DB — PRV-T08/T09.
- Swagger/show-sql off em prod — PRV-T12.
- Suporte a banner de cookies / verificação de idade (a política vem do jurídico) — PRV-T14/T15.

## 8. Critério de aceite da Fase 14

- [x] Pendências **técnicas** listadas (§1) e marcadas como [CÓDIGO].
- [x] Pendências **jurídicas** listadas (§2) e marcadas como [JURÍDICO].
- [x] Pendências **operacionais** listadas (§3).
- [x] **Encarregado/DPO** (PRV-O01), **RIPD** (PRV-J09), **transferência internacional** (PRV-J05), **consentimento de cookies** (PRV-J06) e **notificação de incidente — 3 dias úteis** (§4) incluídos.
- [x] Riscos antes da comercialização destacados (§6).
- [x] Marcado o que é resolvível no código vs. revisão jurídica/negocial.
