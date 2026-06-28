# Plano de Pentest, Segurança e LGPD para executar com Claude Code

> Objetivo: guiar o agente (Claude Code) a revisar minhas aplicações web/API, identificar vulnerabilidades técnicas, riscos de LGPD e pendências antes da comercialização.
>
> Escopo sugerido: backend, frontend, API, banco de dados, autenticação, autorização, lógica de negócio, logs, infraestrutura, variáveis de ambiente, dependências, Docker, CI/CD e documentação legal/operacional.

---

> Regra de ouro: análise e geração de relatório podem ser feitas a qualquer momento. **Correção de código só acontece com sua autorização explícita, fase por fase.**

---

## 1. Referências usadas como base

- **OWASP Web Security Testing Guide (WSTG):** como testar aplicações web.
- **OWASP API Security Top 10 2023:** riscos comuns em APIs (BOLA, Broken Authentication, Broken Object Property Level Authorization, Unrestricted Resource Consumption, Unrestricted Access to Sensitive Business Flows etc.).
- **OWASP ASVS (Application Security Verification Standard):** checklist do que precisa estar verificado, por nível de garantia. Complementa o WSTG.
- **CVSS v3.1 / v4.0:** pontuação numérica de severidade dos achados, para tornar o relatório comparável.
- **Guia Orientativo da ANPD para segurança da informação em agentes de tratamento de pequeno porte:** medidas administrativas e técnicas de proteção de dados pessoais.
- **Checklist e modelo de registro de operações de tratamento da ANPD.**
- **Resolução CD/ANPD nº 15/2024 (Regulamento de Comunicação de Incidente de Segurança):** define o fluxo e o prazo de notificação de incidentes.

---

## 2. Regras para o agente

Antes de alterar qualquer código:

1. Entenda a arquitetura da aplicação.
2. Identifique stack, frameworks e pontos de entrada.
3. Gere evidências no relatório.
4. Classifique cada risco por severidade (qualitativa + CVSS).
5. Priorize falhas que exponham dados pessoais.
6. Não remova funcionalidades sem justificar.
7. Não coloque secrets, tokens ou senhas no relatório (sempre mascarar).
8. Não rode ataques destrutivos contra produção.
9. Não execute varreduras agressivas sem autorização explícita.
10. Para qualquer alteração de segurança com impacto funcional, criar plano antes.
11. Respeite as Regras de Engajamento da seção 2.1 — se o alvo, o ambiente ou a janela não estiverem definidos, **pergunte antes de executar testes ativos**.

---

## 2.1. Autorização e Regras de Engajamento (RoE)

Preencha antes de iniciar testes ativos. O agente deve recusar testes que saiam deste escopo.

| Item | Valor |
|---|---|
| Aplicação / repositório | `PREENCHER` |
| Ambiente autorizado para teste | `staging` / `local` (evitar produção) |
| Ambientes proibidos | `produção` (salvo autorização escrita) |
| Janela de execução | `PREENCHER` |
| Tipo de teste permitido | Revisão de código (estática) + testes dinâmicos não destrutivos em ambiente autorizado |
| Tipos de teste proibidos | DoS, brute force massivo, exclusão/alteração de dados reais, engenharia social |
| Condição de parada (stop condition) | Qualquer indício de impacto em produção, perda de dados ou indisponibilidade |
| Contato de emergência | `PREENCHER` |
| Responsável pela autorização | `PREENCHER` |

> Este bloco é o que separa "revisão de segurança" de "acesso não autorizado". Se a aplicação for vendida para terceiros, tenha esta autorização registrada/assinada.

---

## 3. Entregáveis esperados

Ao final, o agente deve produzir ou atualizar estes arquivos:

```text
SECURITY_REPORT.md
LGPD_DATA_MAPPING.md
SECURITY_FIX_PLAN.md
SECURITY_TEST_CASES.md
PRIVACY_ACTION_ITEMS.md
RETEST_REPORT.md
```

Descrição:

| Arquivo | Finalidade |
|---|---|
| `SECURITY_REPORT.md` | Relatório de vulnerabilidades, evidências, severidade e CVSS |
| `LGPD_DATA_MAPPING.md` | Mapeamento dos dados pessoais tratados pela aplicação |
| `SECURITY_FIX_PLAN.md` | Plano de correção priorizado |
| `SECURITY_TEST_CASES.md` | Casos de teste manuais e automatizados |
| `PRIVACY_ACTION_ITEMS.md` | Pendências LGPD, termos, privacidade e operação |
| `RETEST_REPORT.md` | Verificação de que cada achado corrigido foi realmente fechado |

---

# Fase 1 — Reconhecimento do projeto

## Objetivo

Entender a aplicação antes de testar ou alterar qualquer coisa.

## Tarefas para o agente

Verificar:

- Linguagens e frameworks.
- Estrutura de pastas.
- Endpoints HTTP/API.
- Telas públicas e privadas.
- Mecanismo de autenticação.
- Mecanismo de autorização.
- Conexão com banco de dados.
- Serviços externos usados.
- Uso de e-mail, storage, analytics ou logs externos.
- Arquivos Docker, compose, CI/CD e variáveis de ambiente.
- Onde dados pessoais entram, trafegam e são armazenados.
- Diagrama simples de fluxo de dados (data flow): origem → processamento → armazenamento → saída.

## Prompt sugerido

```text
Execute a Fase 1 do SECURITY_LGPD_PLAN.md.
Analise a arquitetura do projeto sem alterar código.
Crie ou atualize SECURITY_REPORT.md com:
- stack detectada;
- pontos de entrada da aplicação;
- endpoints encontrados;
- mecanismo de autenticação/autorização;
- dados pessoais possíveis;
- fluxo de dados (onde entram, trafegam e são armazenados);
- riscos iniciais;
- próximos passos recomendados.
```

## Critério de aceite

- [ ] Stack identificada.
- [ ] Endpoints principais listados.
- [ ] Dados pessoais prováveis identificados.
- [ ] Integrações externas listadas.
- [ ] Fluxo de dados descrito.
- [ ] Nenhuma alteração de código feita nesta fase.

---

# Fase 2 — Mapeamento LGPD dos dados pessoais

## Objetivo

Identificar quais dados pessoais a aplicação coleta, armazena, processa, exibe, exporta ou compartilha.

## Dados a procurar

- Nome.
- E-mail.
- CPF/CNPJ.
- Telefone.
- Endereço.
- IP.
- User agent.
- Dados financeiros.
- Dados de login.
- Tokens.
- Arquivos enviados pelo usuário.
- Logs com informações pessoais.
- Dados sensíveis, se existirem (saúde, biometria, origem racial, convicção religiosa, dados de menores etc.).

## Tarefas para o agente

Criar `LGPD_DATA_MAPPING.md` com a tabela:

```md
# Mapeamento LGPD de Dados Pessoais

| Dado | Onde é coletado | Onde é armazenado | Finalidade | Base legal provável | Retenção sugerida | Sensível? | Risco | Observações |
|---|---|---|---|---|---|---|---|---|
| E-mail | Cadastro/Login | tabela users | Autenticação | Execução de contrato | Enquanto conta ativa | Não | Médio | Verificar exclusão de conta |
```

## Prompt sugerido

```text
Execute a Fase 2 do SECURITY_LGPD_PLAN.md.
Procure no código entidades, DTOs, migrations, schemas, controllers, formulários, logs e integrações que tratem dados pessoais.
Marque explicitamente dados sensíveis (saúde, biometria, dados de menores etc.).
Crie LGPD_DATA_MAPPING.md.
Não altere código ainda.
```

## Critério de aceite

- [ ] Dados pessoais listados.
- [ ] Dados sensíveis sinalizados.
- [ ] Local de coleta identificado.
- [ ] Local de armazenamento identificado.
- [ ] Finalidade descrita.
- [ ] Riscos de exposição anotados.
- [ ] Pontos sem clareza marcados como `PENDENTE`.

---

# Fase 3 — Secrets, senhas e arquivos sensíveis

## Objetivo

Garantir que não existam senhas, tokens ou chaves no código-fonte — **incluindo no histórico do git**.

## Verificações

Procurar por:

- Senhas hardcoded.
- Tokens JWT hardcoded.
- Chaves de API.
- Credenciais SMTP.
- Credenciais de banco.
- Arquivos `.env` commitados.
- Dumps `.sql` com dados reais.
- Certificados e chaves privadas.
- Logs com secrets.
- **Secrets que foram commitados e depois removidos (ainda vivos no histórico do git).**

## Comandos sugeridos

Varredura da árvore atual:

```bash
gitleaks detect --source . --no-git
```

Varredura de TODO o histórico do git (pega secret commitado e depois removido):

```bash
gitleaks detect --source .
```

Busca textual de apoio:

```bash
grep -RniE "password|passwd|secret|token|apikey|api_key|private_key|smtp|jwt|senha" . \
  --exclude-dir=.git \
  --exclude-dir=node_modules \
  --exclude-dir=target \
  --exclude-dir=dist \
  --exclude-dir=build
```

## Prompt sugerido

```text
Execute a Fase 3 do SECURITY_LGPD_PLAN.md.
Procure secrets, tokens, senhas, arquivos .env, dumps e credenciais hardcoded, na árvore atual E no histórico do git.
Não exponha os valores encontrados no relatório; masque os valores.
Atualize SECURITY_REPORT.md com evidências seguras e recomendações.
Se encontrar secrets reais, adicione recomendação de rotação imediata e de limpeza do histórico (ex.: git filter-repo / BFG).
```

## Critério de aceite

- [ ] Nenhum secret real exposto no relatório.
- [ ] Histórico do git verificado.
- [ ] Arquivos sensíveis identificados.
- [ ] Recomendações de `.gitignore` feitas.
- [ ] Recomendação de rotação para secrets vazados.

---

# Fase 4 — Dependências vulneráveis

## Objetivo

Encontrar bibliotecas, imagens Docker e pacotes com vulnerabilidades conhecidas.

## Comandos por stack

### Node/React/TypeScript

```bash
npm audit
```

```bash
npm outdated
```

### Java/Maven

```bash
mvn dependency:tree
```

```bash
mvn org.owasp:dependency-check-maven:check
```

### Docker/Filesystem

```bash
trivy fs .
```

```bash
trivy image nome-da-imagem:tag
```

## Prompt sugerido

```text
Execute a Fase 4 do SECURITY_LGPD_PLAN.md.
Identifique dependências vulneráveis no projeto.
Atualize SECURITY_REPORT.md com pacote, versão atual, vulnerabilidade (CVE), severidade e sugestão de correção.
Não atualize dependências automaticamente sem avaliar risco de quebra.
```

## Critério de aceite

- [ ] Dependências críticas identificadas.
- [ ] Versões vulneráveis listadas (com CVE quando houver).
- [ ] Correções sugeridas.
- [ ] Atualizações arriscadas marcadas para validação manual.

---

# Fase 5 — Autenticação e gestão de sessão

## Objetivo

Garantir que login, sessão, JWT e recuperação de senha sejam seguros.

## Verificações

- Senhas usam hash seguro, como bcrypt, argon2 ou PBKDF2.
- Senhas nunca são salvas em texto puro.
- Login não informa se e-mail existe ou não.
- Existe rate limit no login.
- Existe bloqueio ou atraso contra brute force.
- JWT tem expiração.
- Refresh token, se existir, tem revogação.
- Reset de senha usa token aleatório, expira e só pode ser usado uma vez.
- Token de reset não aparece em logs.
- Logout invalida sessão ou token quando aplicável.
- Cookies, se usados, têm `HttpOnly`, `Secure` e `SameSite`.
- **Não há session fixation** (o ID/sessão é regenerado após o login).
- **Timeout de sessão** definido (idle e absoluto).
- **Sessões concorrentes** tratadas conforme a política do produto.
- **MFA** disponível ao menos para contas administrativas (recomendado).

## Casos de teste

```md
## Testes de autenticação

- [ ] Tentar login com senha errada várias vezes.
- [ ] Verificar se resposta não revela se o e-mail existe.
- [ ] Solicitar reset de senha para e-mail existente.
- [ ] Solicitar reset de senha para e-mail inexistente.
- [ ] Confirmar que ambas as respostas são genéricas.
- [ ] Usar token de reset expirado.
- [ ] Reutilizar token de reset já usado.
- [ ] Verificar se JWT expirado é rejeitado.
- [ ] Verificar se endpoint privado sem token retorna 401.
- [ ] Verificar se o identificador de sessão muda após o login.
- [ ] Verificar comportamento após timeout de sessão.
```

## Prompt sugerido

```text
Execute a Fase 5 do SECURITY_LGPD_PLAN.md.
Revise autenticação, senha, JWT, cookies, logout, sessão (fixation/timeout/concorrência) e fluxo de esqueci minha senha.
Avalie se MFA é viável para contas administrativas.
Crie SECURITY_TEST_CASES.md com testes manuais e automatizados possíveis.
Atualize SECURITY_REPORT.md com vulnerabilidades encontradas.
Não altere o fluxo sem explicar o impacto.
```

## Critério de aceite

- [ ] Hash de senha validado.
- [ ] Reset de senha validado.
- [ ] JWT/sessão validado (incl. fixation e timeout).
- [ ] Riscos de brute force avaliados.
- [ ] MFA avaliado.
- [ ] Testes documentados.

---

# Fase 6 — Autorização e isolamento de dados

## Objetivo

Evitar que um usuário acesse dados de outro usuário ou funções administrativas.

## Verificações críticas

- Usuário comum não acessa endpoint admin.
- Usuário A não acessa recurso do usuário B trocando ID.
- Usuário A não altera dados do usuário B.
- Usuário A não exclui dados do usuário B.
- Payload não permite mudar `role`, `isAdmin`, `tenantId`, `userId` ou `ownerId` indevidamente (mass assignment).
- Backend não confia apenas em dados enviados pelo frontend.
- Multi-tenant, se existir, filtra tudo por tenant no backend.

## Exemplos de testes

```http
GET /api/users/1
Authorization: Bearer TOKEN_USUARIO_A
```

```http
GET /api/users/2
Authorization: Bearer TOKEN_USUARIO_A
```

```http
PATCH /api/users/2
Authorization: Bearer TOKEN_USUARIO_A
Content-Type: application/json

{
  "name": "alterado indevidamente"
}
```

```http
PATCH /api/users/me
Authorization: Bearer TOKEN_USUARIO_COMUM
Content-Type: application/json

{
  "role": "ADMIN",
  "isAdmin": true
}
```

## Prompt sugerido

```text
Execute a Fase 6 do SECURITY_LGPD_PLAN.md.
Revise controllers, services, repositories, guards, filters, middlewares e policies de autorização.
Procure falhas de IDOR/BOLA, escalação horizontal e escalação vertical.
Atualize SECURITY_REPORT.md com evidências.
Crie testes em SECURITY_TEST_CASES.md para validar que um usuário não acessa dados de outro.
```

## Critério de aceite

- [ ] Endpoints com ID revisados.
- [ ] Regras admin revisadas.
- [ ] Campos sensíveis protegidos contra mass assignment.
- [ ] Testes de usuário A versus usuário B documentados.

---

# Fase 7 — Validação de entrada, ataques comuns e CSRF

## Objetivo

Reduzir risco de SQL Injection, XSS, CSRF, SSRF, Path Traversal, Command Injection e upload inseguro.

## Verificações

- Queries usam parâmetros, não concatenação de string.
- Inputs têm validação de tipo, tamanho e formato.
- HTML vindo do usuário é sanitizado ou bloqueado.
- Upload valida extensão, MIME type e tamanho.
- Upload não permite execução de arquivos.
- Nomes de arquivos são normalizados.
- URLs externas são validadas contra SSRF.
- Comandos de sistema não usam input direto do usuário.
- Erros não expõem stack trace em produção.

### CSRF (proteção específica)

- Operações que mudam estado (POST/PUT/PATCH/DELETE) exigem token anti-CSRF **ou** dependem de autenticação por header (Bearer) e não por cookie de sessão.
- Para auth baseada em cookie de sessão: token CSRF presente e validado.
- Cookies de sessão com `SameSite=Lax` ou `Strict`.
- Verificação de `Origin`/`Referer` em endpoints sensíveis.

## Payloads de teste controlados

```text
' OR '1'='1
```

```html
<script>alert(1)</script>
```

```text
../../../../etc/passwd
```

```text
http://169.254.169.254/latest/meta-data/
```

## Prompt sugerido

```text
Execute a Fase 7 do SECURITY_LGPD_PLAN.md.
Revise validação de entrada, queries, uploads, tratamento de erro, uso de comandos externos e proteção CSRF.
Para CSRF: identifique se a auth é por cookie ou por header e avalie a necessidade de token anti-CSRF e SameSite.
Atualize SECURITY_REPORT.md com riscos e recomendações.
Se possível, proponha testes automatizados para entradas inválidas e para CSRF.
```

## Critério de aceite

- [ ] Queries inseguras identificadas.
- [ ] Inputs críticos validados.
- [ ] Uploads revisados.
- [ ] Tratamento de erro revisado.
- [ ] Risco de CSRF avaliado e mitigado.

---

# Fase 8 — Exposição de dados pessoais e criptografia em repouso

## Objetivo

Garantir minimização de dados (retornar apenas o necessário) e proteção de dados sensíveis armazenados.

## Verificações

- APIs não retornam `password`, `passwordHash`, `resetToken`, `refreshToken`, `secret`, `cpf` desnecessariamente.
- Listagens não retornam dados sensíveis sem necessidade.
- Frontend não armazena tokens sensíveis em local inseguro sem avaliação.
- Dados pessoais não aparecem em mensagens de erro.
- Dados pessoais não aparecem no HTML público ou bundle frontend.
- Não existe endpoint de debug em produção.
- **Dados sensíveis (CPF, dados financeiros) são criptografados ou pseudonimizados em repouso quando aplicável.**
- **Conexão com o banco usa TLS quando o banco é remoto.**

## Prompt sugerido

```text
Execute a Fase 8 do SECURITY_LGPD_PLAN.md.
Revise DTOs, serializers, responses de API, models de frontend e armazenamento local.
Procure exposição excessiva de dados pessoais ou campos internos.
Avalie criptografia/pseudonimização de dados sensíveis em repouso.
Atualize SECURITY_REPORT.md e PRIVACY_ACTION_ITEMS.md.
```

## Critério de aceite

- [ ] Campos sensíveis removidos das respostas.
- [ ] DTOs revisados.
- [ ] Erros revisados.
- [ ] Frontend revisado.
- [ ] Criptografia em repouso de dados sensíveis avaliada.

---

# Fase 9 — Logs, auditoria e monitoramento

## Objetivo

Evitar vazamento em logs e garantir rastreabilidade mínima.

## Não logar

- Senha.
- Hash de senha.
- Token JWT.
- Refresh token.
- Token de reset de senha.
- CPF completo.
- Dados bancários.
- Payload completo de login.
- Chaves de API.

## Logs úteis

- Login bem-sucedido.
- Falhas de login, sem senha.
- Alteração de senha.
- Solicitação de reset de senha.
- Alterações administrativas.
- Exclusão de dados.
- Exportação de dados.
- Erros de autorização.

## Verificações adicionais

- Retenção de logs definida (não guardar indefinidamente dados pessoais).
- Acesso aos logs restrito.

## Prompt sugerido

```text
Execute a Fase 9 do SECURITY_LGPD_PLAN.md.
Revise logs e auditoria.
Identifique vazamento de dados pessoais, tokens ou senhas em logs.
Sugira mascaramento, eventos mínimos de auditoria, retenção e controle de acesso aos logs.
Atualize SECURITY_REPORT.md.
```

## Critério de aceite

- [ ] Logs sensíveis identificados.
- [ ] Estratégia de mascaramento sugerida.
- [ ] Eventos mínimos de auditoria definidos.
- [ ] Retenção e acesso a logs avaliados.

---

# Fase 10 — Infraestrutura, Docker, banco e deploy

## Objetivo

Reduzir riscos de configuração insegura em produção.

## Verificações

- HTTPS obrigatório.
- Banco de dados não exposto publicamente.
- Redis, RabbitMQ, MongoDB, PostgreSQL ou MySQL não expostos sem necessidade.
- Usuário do banco com menor privilégio.
- Backups protegidos e **criptografados**, com **restauração testada**.
- CORS restrito.
- Headers de segurança configurados.
- Ambiente de produção não roda com debug ativo.
- Swagger, Actuator ou painel admin não ficam públicos sem autenticação.
- Docker não roda como root, se possível.
- Imagens Docker usam versões fixas, não `latest`.

## Headers recomendados

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Security-Policy: default-src 'self'
```

## Prompt sugerido

```text
Execute a Fase 10 do SECURITY_LGPD_PLAN.md.
Revise Dockerfile, docker-compose, configs de produção, CORS, headers, Swagger, Actuator, conexão com banco, backups e exposição de portas.
Atualize SECURITY_REPORT.md e SECURITY_FIX_PLAN.md.
```

## Critério de aceite

- [ ] Banco não público.
- [ ] CORS restrito.
- [ ] HTTPS obrigatório.
- [ ] Swagger/Actuator protegidos.
- [ ] Backups protegidos e restauração testada.
- [ ] Docker revisado.

---

# Fase 11 — Rate limiting e consumo de recursos

## Objetivo

Cobrir os itens `API4:2023 — Unrestricted Resource Consumption` e `API6:2023 — Unrestricted Access to Sensitive Business Flows` do OWASP API Top 10.

## Verificações

- Rate limit não está só no login: aplicar também em reset de senha, cadastro, endpoints caros e exportações.
- Listagens têm paginação com limite máximo (não permitir `limit` arbitrariamente grande).
- Exportação em massa de dados é controlada e auditada.
- Endpoints que disparam e-mail/SMS têm proteção contra abuso.
- Uploads têm limite de tamanho e taxa.
- Operações de negócio sensíveis (compra, transferência, convite) têm anti-automação quando fizer sentido.

## Prompt sugerido

```text
Execute a Fase 11 do SECURITY_LGPD_PLAN.md.
Mapeie endpoints sem rate limit, listagens sem limite de paginação, exportações em massa e fluxos sensíveis sem anti-automação.
Atualize SECURITY_REPORT.md com riscos de consumo de recursos e abuso de fluxos de negócio.
```

## Critério de aceite

- [ ] Endpoints sem rate limit identificados.
- [ ] Paginação com limite máximo verificada.
- [ ] Exportação em massa avaliada.
- [ ] Fluxos sensíveis de negócio avaliados.

---

# Fase 12 — Lógica de negócio

## Objetivo

Encontrar falhas que nenhuma ferramenta automática pega: abuso da lógica da aplicação. É aqui que costuma morar o prejuízo de uma app comercial.

## Verificações

- Race conditions (ex.: usar o mesmo cupom/saldo duas vezes em paralelo).
- Manipulação de preço, quantidade ou desconto no payload.
- Bypass de etapas de um fluxo (pular pagamento, pular validação).
- Reuso indevido de cupons, créditos ou convites.
- Valores negativos ou extremos aceitos onde não deveriam.
- Mudança de estado fora da ordem esperada (ex.: aprovar um pedido já cancelado).
- Limites de plano/assinatura realmente aplicados no backend.

## Prompt sugerido

```text
Execute a Fase 12 do SECURITY_LGPD_PLAN.md.
Analise os principais fluxos de negócio (cadastro, pagamento, planos, cupons, transferências, convites).
Procure race conditions, manipulação de payload, bypass de etapas e reuso indevido.
Atualize SECURITY_REPORT.md com cenários de abuso e recomendações.
Proponha testes para os cenários de maior impacto financeiro.
```

## Critério de aceite

- [ ] Fluxos críticos de negócio mapeados.
- [ ] Cenários de abuso documentados.
- [ ] Validações de limite/estado verificadas no backend.

---

# Fase 13 — Testes automatizados de segurança

## Objetivo

Criar testes que impeçam regressões de segurança.

## Testes recomendados

- Endpoint privado sem token retorna 401.
- Usuário comum acessando admin retorna 403.
- Usuário A acessando recurso do usuário B retorna 403 ou 404.
- Payload tentando alterar `role` é ignorado ou rejeitado.
- Token expirado é rejeitado.
- Reset token usado duas vezes é rejeitado.
- Input inválido retorna 400.
- Senha nunca aparece em resposta JSON.
- Requisição que muda estado sem token CSRF (quando auth é por cookie) é rejeitada.
- Listagem respeita o limite máximo de paginação.

## Scan dinâmico (DAST) opcional

Complementa a revisão de código com um scan contra a instância rodando em ambiente autorizado:

```bash
# baseline não intrusivo do OWASP ZAP contra o ambiente de staging
zap-baseline.py -t https://staging.minha-app.exemplo
```

## Prompt sugerido

```text
Execute a Fase 13 do SECURITY_LGPD_PLAN.md.
Crie ou proponha testes automatizados para autenticação, autorização, exposição de dados, CSRF e validação de entrada.
Priorize testes de maior risco.
Atualize SECURITY_TEST_CASES.md e, se seguro, implemente testes no projeto.
Indique também onde um scan DAST (ex.: ZAP baseline) agregaria, sem executá-lo contra produção.
```

## Critério de aceite

- [ ] Testes de autenticação criados ou propostos.
- [ ] Testes de autorização criados ou propostos.
- [ ] Testes de exposição de dados criados ou propostos.
- [ ] Testes podem rodar no CI.

---

# Fase 14 — Checklist LGPD para comercialização

## Objetivo

Identificar pendências não técnicas necessárias para vender a aplicação com mais segurança.

## Itens mínimos

- [ ] Política de Privacidade publicada.
- [ ] Termos de Uso publicados.
- [ ] Registro de operações de tratamento criado.
- [ ] **Encarregado pelo Tratamento de Dados (DPO) nomeado e publicado** (com canal de contato).
- [ ] Canal de contato para privacidade definido.
- [ ] Processo de exclusão de conta/dados definido (com prazo de resposta ao titular).
- [ ] Processo de exportação/acesso aos dados definido.
- [ ] Processo de correção de dados definido.
- [ ] Processo de revogação de consentimento, se aplicável.
- [ ] **Consentimento de cookies / banner**, se o frontend usa analytics ou cookies não essenciais.
- [ ] Lista de operadores/suboperadores criada.
- [ ] **Transferência internacional de dados avaliada** (se usa cloud/serviços fora do Brasil).
- [ ] Retenção de dados definida.
- [ ] **RIPD (Relatório de Impacto à Proteção de Dados) elaborado**, ao menos simplificado, se há tratamento de dados sensíveis ou em escala.
- [ ] Plano de resposta a incidente criado, **alinhado ao prazo de notificação da ANPD**.
- [ ] Procedimento de backup e restauração documentado.
- [ ] Contratos com clientes incluem cláusulas de proteção de dados, se B2B.

## Notificação de incidente (Resolução CD/ANPD nº 15/2024)

Item para constar no plano de resposta a incidente:

- Comunicação à ANPD e aos titulares afetados em **até 3 dias úteis** a partir do conhecimento do incidente que possa gerar risco ou dano relevante.
- A comunicação **não espera o fim da investigação**: admite-se comunicação preliminar, complementada em até 20 dias úteis.
- Comunicação feita por meio do Encarregado, via formulário eletrônico da ANPD.
- Risco relevante é presumido quando envolve dados sensíveis, dados de crianças/adolescentes/idosos, dados financeiros ou senhas.

> Estes prazos refletem a regulamentação vigente na data deste documento; confirme com revisão jurídica antes de comercializar.

## Prompt sugerido

```text
Execute a Fase 14 do SECURITY_LGPD_PLAN.md.
Com base no código e nas funcionalidades encontradas, crie PRIVACY_ACTION_ITEMS.md com pendências LGPD para comercialização.
Separe itens técnicos, jurídicos e operacionais.
Inclua Encarregado, RIPD, transferência internacional, consentimento de cookies e o fluxo de notificação de incidente (3 dias úteis).
Marque o que pode ser resolvido no código e o que precisa de revisão jurídica/negocial.
```

## Critério de aceite

- [ ] Pendências técnicas listadas.
- [ ] Pendências jurídicas listadas.
- [ ] Pendências operacionais listadas.
- [ ] Riscos antes da comercialização destacados.

---

# Fase 15 — Reteste e fechamento

## Objetivo

Confirmar que cada achado corrigido foi de fato fechado (fechar o ciclo do pentest).

## Tarefas para o agente

- Para cada achado do `SECURITY_REPORT.md` marcado como corrigido, reexecutar o teste/caso correspondente.
- Registrar em `RETEST_REPORT.md`: ID do achado, status anterior, ação aplicada, resultado do reteste (Fechado / Parcial / Reaberto), evidência segura.
- Achados não corrigidos permanecem abertos com justificativa e risco residual aceito (ou não).

## Prompt sugerido

```text
Execute a Fase 15 do SECURITY_LGPD_PLAN.md.
Para cada achado marcado como corrigido no SECURITY_REPORT.md, reexecute o teste correspondente.
Crie RETEST_REPORT.md com status de cada achado (Fechado/Parcial/Reaberto), evidência segura e risco residual.
Não feche nenhum achado sem evidência de que a correção funciona.
```

## Critério de aceite

- [ ] Todos os achados corrigidos foram reretestados.
- [ ] Achados reabertos sinalizados.
- [ ] Risco residual documentado.

---

# Modelo de relatório esperado

O agente deve manter `SECURITY_REPORT.md` neste formato:

```md
# Relatório de Segurança e LGPD

## 1. Escopo

- Aplicação:
- Repositório:
- Ambiente analisado:
- Data:
- Responsável:

## 2. Resumo executivo

| Severidade | Quantidade |
|---|---:|
| Crítica | 0 |
| Alta | 0 |
| Média | 0 |
| Baixa | 0 |
| Informativa | 0 |

## 3. Achados

### SEC-001 — Título do achado

- Severidade:
- CVSS (vetor e score):
- Categoria:
- Local:
- Evidência segura:
- Impacto:
- Recomendação:
- Status:

## 4. Riscos LGPD

| ID | Risco | Dados afetados | Impacto | Recomendação | Status |
|---|---|---|---|---|---|

## 5. Correções propostas

| Prioridade | Correção | Arquivos afetados | Risco de quebra | Status |
|---|---|---|---|---|

## 6. Pendências antes de comercializar

- [ ] Item 1
- [ ] Item 2
- [ ] Item 3
```

---

# Severidade sugerida

Use esta classificação qualitativa e, quando possível, anexe o **vetor e score CVSS v3.1/v4.0** ao achado.

## Crítica

Falha permite acesso indevido massivo, tomada de conta, vazamento de dados pessoais sensíveis, execução remota de código ou comprometimento direto da infraestrutura. (CVSS ~9.0–10.0)

## Alta

Falha permite acesso a dados de outro usuário, bypass de autenticação/autorização, alteração indevida de dados, reset de senha inseguro ou exposição relevante de dados pessoais. (CVSS ~7.0–8.9)

## Média

Falha aumenta risco de exploração, expõe metadados, permite enumeração, falta rate limit ou retorna dados além do necessário. (CVSS ~4.0–6.9)

## Baixa

Configuração melhorável, header ausente, mensagem de erro pouco ideal ou dependência com risco baixo. (CVSS ~0.1–3.9)

## Informativa

Melhoria recomendada sem risco imediato claro. (CVSS 0.0)

---

# Ordem recomendada de execução

1. Fase 1 — Reconhecimento.
2. Fase 2 — Mapeamento LGPD.
3. Fase 3 — Secrets.
4. Fase 5 — Autenticação e sessão.
5. Fase 6 — Autorização.
6. Fase 7 — Validação de entrada e CSRF.
7. Fase 8 — Exposição de dados e criptografia em repouso.
8. Fase 12 — Lógica de negócio.
9. Fase 11 — Rate limiting e consumo de recursos.
10. Fase 9 — Logs.
11. Fase 10 — Infraestrutura.
12. Fase 4 — Dependências.
13. Fase 13 — Testes automatizados.
14. Fase 14 — Checklist comercial/LGPD.
15. Fase 15 — Reteste e fechamento.

---

# Prompt mestre para iniciar

```text
Você é meu assistente de segurança de aplicação, privacidade e LGPD.
Leia SECURITY_LGPD_PLAN.md, incluindo as Regras de Engajamento da seção 2.1.
Se o ambiente alvo, a janela ou a autorização não estiverem preenchidos, pergunte antes de qualquer teste ativo.
Execute primeiro apenas a Fase 1.
Não altere código.
Crie SECURITY_REPORT.md com achados iniciais, arquitetura, dados pessoais prováveis, endpoints, fluxo de dados, riscos e próximos passos.
Classifique riscos por severidade e, quando possível, com CVSS.
Não exponha secrets reais no relatório.
```

---

# Prompt para executar correções depois da análise

```text
Com base no SECURITY_REPORT.md, crie SECURITY_FIX_PLAN.md priorizando correções críticas e altas.
Depois implemente apenas correções de baixo risco e com testes.
Para mudanças que possam quebrar fluxo de autenticação, autorização, banco ou contrato de API, apenas proponha antes de implementar.
Ao concluir correções, execute a Fase 15 (reteste) e registre o resultado em RETEST_REPORT.md.
```

---

# Observação importante

Este plano ajuda a organizar uma revisão técnica e de privacidade, mas **não substitui**:

- pentest profissional independente, principalmente se a aplicação será vendida para empresas;
- revisão jurídica de LGPD, termos de uso, política de privacidade e contratos;
- validação em ambiente controlado/staging antes de produção.

Os prazos e obrigações de LGPD citados refletem a regulamentação vigente na data de elaboração deste documento e devem ser confirmados com assessoria jurídica antes da comercialização.
