# carimbai — backend

API de cartão de fidelidade (selos/carimbos). Lojistas aplicam selos no cartão
do cliente via QR code; ao completar a cartela, o cliente resgata a recompensa.

> Frontend que consome esta API: `../carimbai-app` (React + Vite).

## Stack

- **Spring Boot 3.5.9**, **Java 25**, build com **Maven wrapper** (`./mvnw`).
- **PostgreSQL 16** (via `docker-compose`).
- **MapStruct 1.6.3** (mapeamento DTO↔entidade), **Lombok**, **springdoc 2.8.9**
  (Swagger), Bean Validation, `spring-security-crypto` (BCrypt).

## Como rodar (dev)

```bash
docker-compose up -d                 # sobe Postgres + pgAdmin
./mvnw spring-boot:run               # profile `dev` (via .claude/settings.json)
```

- App sobe na porta **1234**.
- **Swagger UI**: http://localhost:1234/  •  **OpenAPI**: http://localhost:1234/api-docs
- Compilar rápido (sem testes): `./mvnw -q -DskipTests compile`
- Rodar testes: `./mvnw test`

Profiles em `src/main/resources/`: `application.yaml` (base, profile ativo
`local` por default), `application-dev.yaml`, `application-local.yaml`.
O profile usado nas sessões é `dev` (`SPRING_PROFILES_ACTIVE` em
`.claude/settings.json`).

## Arquitetura

Pacote base `com.app.carimbai`. Fluxo de uma request:

```
controllers → services (e facade) → repositories (Spring Data JPA)
DTOs entram/saem nos controllers; mappers (MapStruct) convertem para models.
Erros sobem para o handler central; auth fica isolada em security/.
```

Pacotes:
- `controllers/` — endpoints REST finos (Admin, Auth, Cards, Customer, Merchant,
  QrCode, Redeem, StaffUser, Stamps). Sem regra de negócio aqui.
- `services/` (+ `services/social/`) e `facade/` — regra de negócio.
- `repositories/` — Spring Data JPA.
- `dtos/` — com subpastas `admin/`, `customer/`, `login/`, `redeem/`. Validação
  via Bean Validation nos DTOs de request.
- `mappers/` — MapStruct (`CardMapper`, `MerchantMapper`, ...).
- `models/` — entidades JPA: `core/` (`Merchant`, `Location`, `StaffUser`,
  `StaffUserMerchant`), `fidelity/` (`Program`, `Customer`, `Card`, ...),
  e raiz (`StampToken`, `IdempotencyKey`).
- `security/` — `JwtAuthenticationFilter`. **Toda lógica de auth mora aqui.**
- `handler/` — tratamento central de exceções.
- `enums/` — `CardStatus`, `StaffRole`, `StampSource`, `StampType`.
- `utils/`.

### Domínio & fluxo de QR (resumo)

- **core**: `Merchant`, `Location`, `StaffUser`  •  **fidelity**: `Program`,
  `Customer`, `Card`, `Stamp`, `Reward`  •  **ops**: `StampToken`.
- O cliente abre o cartão → `GET /api/cards/{id}/qr` gera um **token efêmero**
  assinado por **HMAC SHA-256** (`idRef|nonce|exp` em base64url, TTL ~90s).
- O lojista escaneia → `POST /api/stamp` valida a assinatura/expiração, aplica o
  selo e incrementa a cartela. Idempotência via `IdempotencyKey`.
- Detalhes em `docs/informacoes_projeto.md` e `docs/explicacao_stampToken.md`.

## Auth & segredos

- **JWT** para staff/cliente (`CARIMBAI_JWT_SECRET`, expiração 8h).
- **HMAC** para os tokens de selo (`CARIMBAI_HMAC_SECRET`).
- **OAuth social**: Google / Apple / Facebook (`CARIMBAI_GOOGLE_CLIENT_ID`,
  `CARIMBAI_APPLE_*`, `CARIMBAI_FACEBOOK_*`).

⚠️ **Nunca commitar segredos.** Em produção vêm de env. Valores de dev ficam
apenas em `application-dev.yaml` (placeholders) — não copie-os para código.

## Convenções

- Pacote base `com.app.carimbai`.
- ⚠️ Existe uma pasta `execption/` (typo herdado de "exception"). É intencional
  por ora — **não renomeie/conserte em massa** sem combinar antes.
- Controllers delegam; nunca acessam repositório direto.
- Mapeamento sempre via MapStruct, não na mão.
- Veja regras detalhadas de código em `.claude/rules/java.md`.

## MCP

`.mcp.json` define um servidor **Postgres** opcional para inspeção do banco em
dev. Ele usa `${CARIMBAI_DB_URL}` (defina em `.claude/settings.local.json` →
`env`). Se não quiser MCP, apague `.mcp.json`.

## Deploy

Automático: `push` em `main` dispara o GitHub Action `.github/workflows/deploy.yml`
(build do jar → scp para a VPS → `systemctl restart carimbai.service`). Veja
`/deploy`. Não há passo manual de deploy local.
