# Tier 2 — Fase 2 (backend): endpoints para UI de gestão

## Contexto

Este documento descreve o **backend** da Fase 2 do Tier 2 — os endpoints e mudanças de schema necessárias para suportar a UI de gestão de programas, staff e locations no `carimbai-app`. O **frontend** dessas telas será implementado em sessão dedicada e referenciará estes endpoints.

**Objetivo**: tirar a dependência de Swagger/curl para criar ou modificar programas, staff e locations. Permitir que o admin de um merchant opere via UI.

## 1. Sumário das mudanças

### Banco
- 1 nova migration: adiciona `active BOOLEAN NOT NULL DEFAULT TRUE` em `core.locations`.

### Endpoints novos (5)

| Método | Rota | Autorização | Propósito |
|---|---|---|---|
| GET | `/api/merchants/{mId}/admin/programs` | ADMIN | Lista programs (ativos + inativos), DTO admin |
| GET | `/api/merchants/{mId}/locations` | ADMIN | Lista locations com `active` e `flags` parsed |
| PUT | `/api/merchants/{mId}/locations/{lId}` | ADMIN | Patch parcial de location (name, address, active, flags) |
| GET | `/api/merchants/{mId}/staff-users` | ADMIN | Lista staff do merchant (role + active do vínculo) |
| PATCH | `/api/merchants/{mId}/staff-users/{staffId}` | ADMIN | Patch parcial do vínculo (role, active) com guard de auto-lockout |

### Endpoints existentes endurecidos (4)

Adicionado guard `requirePathMerchantMatchesActive(merchantId)` nos endpoints admin que recebem `merchantId` no path, fechando um vetor de vazamento que escapou da limpeza do Tier 1:

- `POST /api/merchants/{mId}/locations`
- `POST /api/merchants/{mId}/programs`
- `PUT /api/merchants/{mId}/programs/{pId}`

A partir de agora, ADMIN do merchant A tentando passar `merchantId=B` no path recebe **403 Forbidden** em vez de operar no merchant errado.

> Nota: `GET /api/merchants/{mId}/programs` (público, lista ativos para o app cliente) **continua sem o guard** — é intencional para que o front do cliente consiga consultar promoções de qualquer merchant.

## 2. Migration

**Arquivo**: [`V2026.18.05.07.11.00__add_locations_active.sql`](carimbai/src/main/resources/db.migration/V2026.18.05.07.11.00__add_locations_active.sql)

```sql
ALTER TABLE core.locations
  ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
```

Postgres ≥ 11 trata `ADD COLUMN` com DEFAULT constante como rewriteless — operação instantânea mesmo em tabelas grandes. Em volume MVP, é trivial.

## 3. DTOs novos

Todos em `src/main/java/com/app/carimbai/dtos/staff/admin/`. Todos `record`, sem Lombok, seguindo convenção do projeto.

### `AdminProgramItem`
Visão admin de um program — inclui `active` e `terms`, que o `ProgramItemDto` público omite.
```java
public record AdminProgramItem(
    Long id, Long merchantId, String name, String description,
    Integer ruleTotalStamps, String rewardName, Integer expirationDays,
    Boolean active, OffsetDateTime startAt, OffsetDateTime endAt,
    String category, String terms, String imageUrl, Integer sortOrder
) {}
```

### `LocationFlags`
Representação tipada do JSONB armazenado em `core.locations.flags`.
```java
public record LocationFlags(
    Boolean requirePinOnRedeem,
    Boolean enableScanA,
    Boolean enableScanB
) {}
```
**Contrato com o front**: sempre que `flags != null` na request, os **3 campos** devem vir preenchidos. O backend sobrescreve o JSONB inteiro — não faz merge campo-a-campo. Isso evita silenciosamente perder valores em atualizações parciais.

### `LocationItem`
```java
public record LocationItem(
    Long id, Long merchantId, String name, String address,
    Boolean active, LocationFlags flags
) {}
```

### `UpdateLocationRequest`
Patch parcial. Campos `null` = não mexer.
```java
public record UpdateLocationRequest(
    String name, String address, Boolean active, LocationFlags flags
) {}
```

### `StaffItem`
Representa uma linha de `core.staff_user_merchants` enriquecida com email do staff.
```java
public record StaffItem(
    Long staffId, String email, StaffRole role,
    Boolean active, Boolean isDefault, OffsetDateTime createdAt
) {}
```
**Importante**: `role` e `active` refletem o **vínculo** no merchant — não o flag global de `staff_users`. Um staff pode ser ADMIN em um merchant e CASHIER em outro.

### `UpdateStaffMerchantRequest`
```java
public record UpdateStaffMerchantRequest(
    StaffRole role, Boolean active
) {}
```

## 4. Detalhes por endpoint

### 4.1 `GET /api/merchants/{mId}/admin/programs`

- Reusa `ProgramService.listPrograms(merchantId, false)` (já existia, retorna ativos + inativos).
- Mapeia via `ProgramMapper.programToAdminProgramItem` (novo).
- Response: `List<AdminProgramItem>`.

### 4.2 `GET /api/merchants/{mId}/locations`

- Novo método `LocationService.listByMerchant(merchantId)`.
- Para cada location, parseia `flags` JSONB via `LocationService.parseFlags(loc)` — usa `ObjectMapper` injetado.
- Mapeia via `LocationMapper.toLocationItem(loc, flags)` (novo método `default`).
- Em caso de JSONB corrompido, devolve flags `(false, false, false)` em vez de quebrar.

### 4.3 `PUT /api/merchants/{mId}/locations/{lId}`

- Novo método `LocationService.updateLocation(merchantId, locationId, request)`.
- Valida `location.merchant.id == merchantId` (defesa em profundidade).
- Patch parcial: `name`, `address`, `active`, `flags`.
- **Comportamento de `flags`**: se a request envia `flags != null`, sobrescreve o JSONB inteiro com `objectMapper.writeValueAsString(request.flags())`. Se `null`, não toca em `flags`.
- Response: `LocationItem` atualizado.

### 4.4 `GET /api/merchants/{mId}/staff-users`

- Novo método `StaffUserMerchantRepository.findAllByMerchantIdWithStaff(merchantId)` com `JOIN FETCH sum.staffUser` para evitar N+1.
- Ordenado por `createdAt asc`.
- Mapeia via `StaffMapper.toStaffItem(link)` (novo).
- Response: `List<StaffItem>`.

### 4.5 `PATCH /api/merchants/{mId}/staff-users/{staffId}`

- Novo método `StaffService.updateStaffInMerchant(merchantId, staffId, request, callerStaffId)`.
- Carrega o vínculo via `findByStaffUserIdAndMerchantId(staffId, merchantId)`; se inexistente, 400.
- **Guard de auto-lockout** (`isSelf = callerStaffId.equals(staffId)`):
  - Se admin tenta se desativar (`active == false`): **403 — "You cannot deactivate yourself in this merchant"**.
  - Se admin atual tem role `ADMIN` e tenta mudar para `CASHIER`: **403 — "You cannot demote yourself from ADMIN in this merchant"**.
- Aplica patch parcial e salva.
- `callerStaffId` vem de `SecurityUtils.getRequiredStaffUser().getId()` no controller.
- Response: `StaffItem` atualizado.

## 5. SecurityUtils — novo helper

```java
public static void requirePathMerchantMatchesActive(Long pathMerchantId) {
    Long active = getActiveMerchantId();
    if (!active.equals(pathMerchantId)) {
        throw new AccessDeniedException(
            "Path merchantId does not match the staff's active merchant");
    }
}
```

Chamado no **início** de todos os endpoints admin que recebem `merchantId` no path. Defesa em profundidade contra um vetor que sobrou do Tier 1 (o `@PreAuthorize("hasAuthority('ADMIN')")` original só garante que o caller é ADMIN, não que o ADMIN é deste merchant).

Como o `AccessDeniedException` já é mapeado para 403 no `GlobalExceptionHandler` (vide Tier 1), a integração é transparente.

## 6. Arquivos modificados

### Novos (8)
- `src/main/resources/db.migration/V2026.18.05.07.11.00__add_locations_active.sql`
- `src/main/java/com/app/carimbai/dtos/staff/admin/AdminProgramItem.java`
- `src/main/java/com/app/carimbai/dtos/staff/admin/LocationFlags.java`
- `src/main/java/com/app/carimbai/dtos/staff/admin/LocationItem.java`
- `src/main/java/com/app/carimbai/dtos/staff/admin/UpdateLocationRequest.java`
- `src/main/java/com/app/carimbai/dtos/staff/admin/StaffItem.java`
- `src/main/java/com/app/carimbai/dtos/staff/admin/UpdateStaffMerchantRequest.java`

### Modificados (7)
- `src/main/java/com/app/carimbai/models/core/Location.java` — campo `active`.
- `src/main/java/com/app/carimbai/utils/SecurityUtils.java` — `requirePathMerchantMatchesActive`.
- `src/main/java/com/app/carimbai/repositories/StaffUserMerchantRepository.java` — `findAllByMerchantIdWithStaff`.
- `src/main/java/com/app/carimbai/services/LocationService.java` — `listByMerchant`, `updateLocation`, `parseFlags`, injeção de `ObjectMapper`.
- `src/main/java/com/app/carimbai/services/StaffService.java` — `listStaffByMerchant`, `updateStaffInMerchant` com guard de auto-lockout.
- `src/main/java/com/app/carimbai/mappers/ProgramMapper.java` — `programToAdminProgramItem`.
- `src/main/java/com/app/carimbai/mappers/LocationMapper.java` — `toLocationItem(Location, LocationFlags)`.
- `src/main/java/com/app/carimbai/mappers/StaffMapper.java` — `toStaffItem(StaffUserMerchant)`.
- `src/main/java/com/app/carimbai/controllers/MerchantController.java` — 5 endpoints novos + guards nos 3 admin endpoints existentes; injeção de `StaffService` e `StaffMapper`.

## 7. Verificação end-to-end (curl autenticado como ADMIN)

```bash
# 1. Aplicar migration
./mvnw flyway:migrate  # ou subir o app (Flyway roda na inicialização se habilitado)
psql -c "\d core.locations"  # confirma coluna active

# Setup: login do admin do merchant 1
TOKEN=$(curl -sX POST http://localhost:1234/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@m1.com","password":"..."}' | jq -r .token)

# 2. List admin programs (com inativos)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:1234/api/merchants/1/admin/programs

# 3. List locations (já trazendo flags parsed)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:1234/api/merchants/1/locations

# 4. Update location (todas as flags juntas)
curl -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  http://localhost:1234/api/merchants/1/locations/10 \
  -d '{"flags":{"requirePinOnRedeem":true,"enableScanA":true,"enableScanB":false}}'

# 5. List staff
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:1234/api/merchants/1/staff-users

# 6. Promover staff CASHIER → ADMIN
curl -X PATCH -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  http://localhost:1234/api/merchants/1/staff-users/42 \
  -d '{"role":"ADMIN"}'

# 7. Desativar staff
curl -X PATCH -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  http://localhost:1234/api/merchants/1/staff-users/42 \
  -d '{"active":false}'

# 8. Auto-lockout guard: admin tenta se desativar
SELF_ID=$(echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq -r .sub)
curl -X PATCH -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  http://localhost:1234/api/merchants/1/staff-users/$SELF_ID \
  -d '{"active":false}'
# → 403 "You cannot deactivate yourself in this merchant"

# 9. Cross-merchant guard
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:1234/api/merchants/2/locations
# → 403 "Path merchantId does not match the staff's active merchant"
```

## 8. Breaking changes

**Nenhum para o front atual.** Mas o endurecimento dos endpoints existentes pode quebrar callers externos não documentados:

| Mudança | Quem pode quebrar | Probabilidade |
|---|---|---|
| `POST/PUT /api/merchants/{X}/programs` agora exige `X == merchant ativo` | Scripts que iteravam merchants via Swagger | Baixa — só vc fazia isso |
| `POST /api/merchants/{X}/locations` mesma coisa | Idem | Baixa |
| `Location.active` é novo campo nullable retornado por `CreateLocationResponse`? | Não — `CreateLocationResponse` não mudou; mantém só name/address/merchantId | Zero |

## 9. Fora de escopo (mantido para frontend e Tier 3)

- **Frontend das 3 telas** (`ProgramsAdmin`, `StaffAdmin`, `LocationsAdmin`) — sessão dedicada Fase 2 frontend (D-H do plano).
- **`AdminLayout`, `Modal`, `Toggle`, `AdminCard`** — componentes UI compartilhados, frontend.
- **Extração do `<SideNav>`** do StaffScreen — frontend.
- **Roles guards no router** (`AdminRoute`) — frontend.
- **Soft-delete real** (hard delete) — não pretendido.
- **Endpoint de reset de senha do staff** — Tier 3.
- **Audit log** das mudanças admin — Tier 3.
- **Paginação** das listas — assume merchant tem < 100 itens; suficiente para MVP.

## 10. Próximos passos

1. **Validar localmente**: aplicar migration, subir app, exercitar os 9 cenários da seção 7.
2. **Frontend Fase 2 (D-H)**: criar componentes shared, refator do sidebar, 3 telas admin, role guard no router. Estimativa ~5h.
3. **Após frontend**: documentar Fase 2 frontend em `tier2-fase2-frontend-ui-gestao.md`.

---

*Documento gerado após implementação backend das Fases A+B+C do plano Tier 2 Fase 2. Referência cruzada: [`tier1-correcoes-seguranca.md`](./tier1-correcoes-seguranca.md), [`tier2-quick-wins.md`](./tier2-quick-wins.md).*
