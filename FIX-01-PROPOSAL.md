# Proposta FIX-01 — Profile de produção + rotação de segredos

> **Achados cobertos:** SEC-003 (segredo JWT/HMAC commitado), SEC-028 (sem profile
> de produção), SEC-005 (Swagger), SEC-011 (`show-sql`), e parte de SEC-013 (TLS ao banco).
> **Status:** PARTE DE CÓDIGO **APLICADA** (2026-06-13). Rotação/VPS/limpeza de
> histórico **pendentes (você)**. Referências: `SECURITY_REPORT.md`,
> `SECURITY_FIX_PLAN.md` (FIX-01), `RETEST_REPORT.md`.
>
> **Decisões do responsável:** (1) manter envs `DB_URL/DB_USERNAME/DB_PASSWORD`;
> (2) VPS setará `SPRING_PROFILES_ACTIVE=prod`; (3) CORS por profile → adiado para
> FIX-15; (4) trocar fallback dev/local por valor obviamente falso — **aprovado**.
>
> **Aplicado nesta rodada (código, `./mvnw test` → 21/21):**
> - ✅ `src/main/resources/application-prod.yaml` criado (datasource via `DB_URL*`,
>   `show-sql:false`, Swagger/api-docs **off**, sem default de segredo → fail-closed).
> - ✅ Fallback de `CARIMBAI_JWT_SECRET` em `application-dev.yaml` e
>   `application-local.yaml` trocado de `h9V$…` por
>   `dev-only-insecure-do-not-use-in-prod-change-me`.
> - ⏳ **Pendente (você):** rotacionar segredos reais, criar `EnvironmentFile` na
>   VPS, trocar senha do banco, e **limpar o histórico do git** (o `h9V$…` ainda
>   vive no histórico — eu preparo o `replacements.txt` quando for fazer o BFG).

## 1. Objetivo e princípio

Hoje não existe `application-prod.yaml`. O profile default é `local`, e
`application-dev.yaml`/`application-local.yaml` carregam um **fallback de segredo
JWT/HMAC commitado** (`h9V$…`/`dev-secret-change-me`). Se a produção subir sem as
envs corretas, a app **assina JWT com o segredo que está no git** → forja de ADMIN.

**Princípio do fix:** em produção, **a app não deve subir se faltar segredo**
(fail-closed). Isso já é o comportamento do `application.yaml` base — que usa
`${CARIMBAI_JWT_SECRET}` / `${CARIMBAI_HMAC_SECRET}` **sem default**. O profile
`prod` só precisa: (a) **não** reintroduzir fallback, (b) **desabilitar Swagger/
`show-sql`**, (c) **fornecer o banco por env com TLS**.

> Elegância: o base já é correto. O `dev`/`local` é que enfraquecem. Então `prod`
> = base + datasource + endurecimento, e o fallback some por herança.

## 2. O que EU altero no código (baixo risco, reversível)

### 2.1. Criar `src/main/resources/application-prod.yaml`

```yaml
server:
  port: 1234
spring:
  flyway:
    enabled: true
    locations: classpath:db.migration
    baseline-on-migrate: true
    validate-on-migrate: true
  datasource:
    # Inclua ?sslmode=require na URL quando o banco for remoto (SEC-013)
    url: ${CARIMBAI_DB_URL}
    username: ${CARIMBAI_DB_USERNAME}
    password: ${CARIMBAI_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false            # SEC-011
    properties:
      hibernate:
        format_sql: false
        dialect: org.hibernate.dialect.PostgreSQLDialect

# Swagger/OpenAPI desligados em produção (SEC-005)
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

logging:
  level:
    root: INFO
    org.hibernate.SQL: WARN

# IMPORTANTE: NÃO declarar carimbai.jwt.secret / hmac-secret aqui.
# Eles são herdados de application.yaml (base), que já exige a env sem default.
# Assim, se CARIMBAI_JWT_SECRET/HMAC faltarem, a app FALHA ao subir (fail-closed).
```

> Nota sobre nomes de env: o `application-dev.yaml` **commitado** usa `${DB_URL}`/
> `${DB_USERNAME}`/`${DB_PASSWORD}`. Esta proposta padroniza para o prefixo do
> projeto **`CARIMBAI_DB_*`** (convenção `CARIMBAI_*` do CLAUDE.md). **Decisão
> necessária:** alinhar a VPS a esses nomes **ou** eu mantenho os nomes que a VPS
> já fornece. Ver §6.

### 2.2. Trocar o fallback "real" do `dev`/`local` por valor obviamente falso

O valor `h9V$…` está queimado (vive no histórico do git) e **precisa ser
rotacionado de qualquer forma**. Para o dev local continuar funcionando sem
expor um segredo de aparência real, proponho trocar o fallback por algo
inequivocamente de desenvolvimento, em `application-dev.yaml` e `application-local.yaml`:

```yaml
# antes
  jwt:
    secret: ${CARIMBAI_JWT_SECRET:h9V$3uP!Qk2zA7mF@0sYwE4#RbT8nLjC}
# depois
  jwt:
    secret: ${CARIMBAI_JWT_SECRET:dev-only-insecure-do-not-use-in-prod-change-me}
```

(idem para `hmac-secret`, que já é `dev-secret-change-me` — aceitável; manter.)

> Isso **não** quebra o `./mvnw spring-boot:run` local (continua tendo default no
> dev). Apenas remove o segredo de aparência real do código. **Não** mexe em
> `application-prod.yaml` (que não tem default).

### 2.3. (Opcional, recomendado) CORS por profile — encadeia FIX-15/SEC-030

Hoje as origens CORS são uma lista fixa no `SecurityConfig` (inclui `localhost`).
Para restringir em produção sem profile-specific Java, externalizar para
propriedade:

```yaml
# application.yaml (base) — default de dev
carimbai:
  cors:
    allowed-origins:
      - https://carimbai-app.vercel.app
      - http://localhost:5173
      - http://localhost:3000
      - http://localhost:1234

# application-prod.yaml — só o PWA
carimbai:
  cors:
    allowed-origins:
      - https://carimbai-app.vercel.app
```

E o `SecurityConfig` passa a ler `@Value("${carimbai.cors.allowed-origins}")`.
**Isto é mudança no `SecurityConfig` (autorização de origem)** → só aplico junto
se você aprovar; caso contrário fica para FIX-15.

## 3. O que VOCÊ executa (rotação + infra — não é código)

### 3.1. Gerar segredos novos e fortes

```bash
# JWT (>= 256 bits)
openssl rand -base64 48
# HMAC dos tokens de QR
openssl rand -base64 48
# (senha de banco — gere/rotacione conforme seu provedor)
```

### 3.2. Rotacionar

- **JWT:** definir `CARIMBAI_JWT_SECRET` com o novo valor. **Efeito:** todos os
  JWTs atuais ficam inválidos → staff precisa **relogar** (aceitável e desejado).
- **HMAC:** definir `CARIMBAI_HMAC_SECRET`. **Efeito:** tokens de QR em voo
  expiram (TTL ~90s) — impacto desprezível.
- **Banco:** trocar a senha no Postgres **e** atualizar `CARIMBAI_DB_PASSWORD`
  **na mesma janela** (evita lockout). Garantir `sslmode=require` na URL se remoto.

### 3.3. Configurar as envs na VPS e no CI

- **VPS (systemd):** no unit `carimbai.service`, via `Environment=` ou, melhor,
  um `EnvironmentFile=/etc/carimbai/carimbai.env` (permissão `600`, dono do serviço):
  ```
  SPRING_PROFILES_ACTIVE=prod
  CARIMBAI_JWT_SECRET=...
  CARIMBAI_HMAC_SECRET=...
  CARIMBAI_DB_URL=jdbc:postgresql://HOST:5432/carimbai?sslmode=require
  CARIMBAI_DB_USERNAME=...
  CARIMBAI_DB_PASSWORD=...
  CARIMBAI_GOOGLE_CLIENT_ID=...
  CARIMBAI_APPLE_CLIENT_ID=...
  CARIMBAI_APPLE_TEAM_ID=...
  CARIMBAI_FACEBOOK_APP_ID=...
  CARIMBAI_FACEBOOK_APP_SECRET=...
  ```
- **GitHub Secrets:** o `deploy.yml` só usa `SSH_*` para copiar o jar; os
  `CARIMBAI_*` ficam **na VPS**, não no Action. Confirmar que nada de segredo
  novo precisa ir ao Action.

### 3.4. Limpar o histórico do git (depois de rotacionar)

A rotação é o que neutraliza o vazamento; a limpeza é complementar:
```bash
# substitui o segredo antigo em todo o histórico (replacements.txt: VALOR_ANTIGO==>***REMOVED***)
bfg --replace-text replacements.txt
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push --force   # coordenar: todos reclonam
```

## 4. Checklist de deploy (ordem segura)

1. [ ] Gerar segredos novos (JWT/HMAC/DB) — §3.1.
2. [ ] Criar o `EnvironmentFile` na VPS com **todas** as envs + `SPRING_PROFILES_ACTIVE=prod` — §3.3.
3. [ ] Trocar a senha do Postgres e refletir em `CARIMBAI_DB_PASSWORD` (mesma janela).
4. [ ] Garantir `sslmode=require` na `CARIMBAI_DB_URL` (se banco remoto).
5. [ ] (Eu) merge do PR com `application-prod.yaml` + ajuste de fallback dev/local.
6. [ ] Deploy (push em `main` → Action). **Validar que a app sobe** — se faltar
       qualquer segredo, ela **falha ao iniciar** (comportamento esperado).
7. [ ] Smoke test em produção:
   - [ ] `GET /api-docs` e `/swagger-ui/**` → **404/desabilitado** (SEC-005 fechado).
   - [ ] `GET /actuator/health` → 200.
   - [ ] Login de staff funciona (exige relogin pós-rotação).
   - [ ] Resposta de erro **não** traz stack trace (SEC-007).
8. [ ] Confirmar que CORS de produção não aceita `localhost` (se §2.3 aplicado).
9. [ ] Rotacionar concluída → limpar histórico do git (BFG) — §3.4.
10. [ ] Atualizar `RETEST_REPORT.md` (SEC-003/005/011/028) e fechar os itens.

## 5. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| App não sobe por env faltando | **É o objetivo** (fail-closed). Validar todas as envs no passo 2 antes do deploy. |
| Rotação do JWT desloga todos | Comunicar; janela de baixo uso. Staff reloga. |
| Lockout de banco na troca de senha | Trocar senha no DB e na env na **mesma janela**; ter rollback do valor antigo à mão até confirmar. |
| VPS hoje usa profile `dev` com `${DB_URL}` | Decidir nomes de env (§6); se mantiver `dev`, o Swagger fica off (commitado), mas o **fallback de segredo continua** — por isso `prod` é necessário. |
| Reescrita de histórico quebra clones | Coordenar `--force` + reclonagem; fazer **após** a rotação. |

## 6. Decisões que preciso de você

1. **Nomes de env do banco:** uso `CARIMBAI_DB_URL/USERNAME/PASSWORD` (convenção
   do projeto) e você alinha a VPS, **ou** eu mantenho `DB_URL/...` que a VPS já usa?
2. **Profile de produção:** confirmo que a VPS pode setar `SPRING_PROFILES_ACTIVE=prod`?
   (Hoje pode estar em `dev` ou indefinido.)
3. **CORS por profile (§2.3):** aplico junto (mexe no `SecurityConfig`) ou deixo para FIX-15?
4. **Ajuste do fallback dev/local (§2.2):** aprova trocar o valor de aparência real
   por `dev-only-insecure-...`?

## 7. Resumo do split

| Parte | Responsável | Risco |
|---|---|---|
| Criar `application-prod.yaml` (§2.1) | **Eu (código)** | Baixo — arquivo inerte até `prod` ativo |
| Trocar fallback dev/local (§2.2) | **Eu (código)** | Baixo — não quebra dev |
| CORS por profile (§2.3) | **Eu (código, se aprovado)** | Médio — toca `SecurityConfig` |
| Gerar/rotacionar segredos (§3) | **Você** | Médio — relogin/janela de DB |
| Envs na VPS + `SPRING_PROFILES_ACTIVE=prod` (§3.3) | **Você** | Médio |
| Limpeza de histórico (§3.4) | **Você** (eu preparo o `replacements.txt`) | Médio — coordenar force-push |

> Ao aprovar §2.1/§2.2, eu crio os arquivos e ajusto, **sem** rodar rotação nem
> tocar na VPS. O resto do checklist é seu, e eu acompanho o reteste (Fase 15).
