# Tier 1 — Correções de segurança

## Contexto

Auditoria do MVP identificou três vazamentos de segurança que precisavam sair antes de qualquer usuário real tocar o sistema. Este documento descreve o que foi corrigido, por quê, e o que mudou nos contratos.

**Itens cobertos por esta entrega:**
1. `GET /api/cards/customer/{customerId}` permitia enumeração de cartões alheios.
2. `GET /api/cards/{cardId}/redeem-qr` permitia gerar QR de resgate para cartão alheio.
3. `POST /api/admin/staff-users/{id}/pin` permitia reset de PIN entre merchants diferentes.

**Item explicitamente fora de escopo:**
- Segredos default no `application-dev.yaml` — arquivo é local da máquina do dono do projeto e não foi tocado.

---

## 1. Vulnerabilidades antes da correção

### 1.1 `GET /api/cards/customer/{customerId}` (item 1)

- Rota estava em `SecurityConfig.requestMatchers("/api/cards/**").permitAll()`.
- `customerId` é `BIGINT IDENTITY` (sequencial). Qualquer um chuta `1, 2, 3...` e recebe **todos os cartões daquele cliente em todos os merchants**, incluindo nome, email do programa, status, contagem de carimbos.
- Sem autenticação. Sem rate limit. PII expondo via simples `curl`.

### 1.2 `GET /api/cards/{cardId}/redeem-qr` (item 2)

- Mesma rota `permitAll`. Sem checar dono.
- Se um cartão está em `READY_TO_REDEEM`, o endpoint **gera um QR de resgate válido** (assinado com HMAC, com nonce e expiração). Em combinação com o item 1 (enumeração de cartões), permitia: descobrir cartões prontos para resgate de outros clientes → gerar QR → tentar resgatar via staff fraudulento.

### 1.3 `POST /api/admin/staff-users/{id}/pin` (item 3)

- `@PreAuthorize("hasAuthority('ADMIN')")` só verificava que o chamador era ADMIN.
- Não verificava que o `staffId` alvo pertence ao **mesmo merchant** do chamador.
- Resultado: ADMIN do merchant A podia resetar o PIN de qualquer staff de qualquer merchant. Combinado com a regra de PIN obrigatório no resgate (`location.flags.requirePinOnRedeem`), isso abria caminho para fraude entre lojas.

---

## 2. Causa-raiz dos itens 1 e 2

O cliente final **não tinha autenticação**. O endpoint de login retornava só `{customerId, name, email, phone, providerId}` — **sem token JWT**. O front guardava esse objeto em `localStorage.carimbai_customer` e chamava a API passando apenas o `customerId` no URL. O backend confiava cegamente.

Não havia como validar "quem está chamando" sem antes introduzir um token de cliente. Por isso a correção dos itens 1 e 2 exigiu **adicionar autenticação real do cliente** como pré-requisito.

---

## 3. Modelo de autenticação — antes vs depois

### Antes

| Ator | Mecanismo |
|---|---|
| Staff (cashier/admin) | JWT com `subject=staffId`, claims `role` + `merchantId` + `email`. Expiração 8h. |
| Cliente final | **Nenhum.** O backend confiava no `customerId` passado no URL. |

### Depois

| Ator | Mecanismo |
|---|---|
| Staff (cashier/admin) | **Inalterado.** Mesmo token, mesmo TTL. |
| Cliente final | **Novo:** JWT com `subject=customerId`, claim `type=CUSTOMER`, claim `email`. Expiração 30 dias (configurável via `carimbai.jwt.customer-expiration-seconds`). |

A infraestrutura é compartilhada: mesma `signingKey`, mesmo `JwtService`, mesmo `JwtAuthenticationFilter`. O filtro agora **ramifica pelo claim `type`** — se for `"CUSTOMER"`, carrega `Customer` do `CustomerRepository` e seta `authority=CUSTOMER` no `SecurityContext`. Token staff antigo (sem claim `type`) cai no caminho original.

---

## 4. Endpoints alterados — antes vs depois

| Endpoint | Auth antes | Auth depois | Ownership check |
|---|---|---|---|
| `POST /api/customers/login-or-register` | permitAll | permitAll | — |
| `POST /api/customers/social-login` | permitAll | permitAll | — |
| `GET /api/cards/customer/{customerId}` | **permitAll** | `hasAuthority('CUSTOMER')` | `customerId` do URL == customerId do JWT |
| `GET /api/cards/{cardId}/redeem-qr` | **permitAll** | `hasAuthority('CUSTOMER')` | `card.customer.id` == customerId do JWT |
| `GET /api/qr/{cardId}` | **permitAll** | `hasAuthority('CUSTOMER')` | `card.customer.id` == customerId do JWT |
| `POST /api/cards` | **permitAll** | `hasAnyAuthority('CASHIER','ADMIN')` | `program.merchant.id` == activeMerchantId do JWT |
| `POST /api/admin/staff-users/{id}/pin` | `ADMIN` (sem merchant check) | `ADMIN` + check de merchant | staff alvo deve ter link **ativo** no `staff_user_merchants` para o mesmo merchant do chamador |

### Mudança nos contratos de resposta

**`POST /api/customers/login-or-register` e `POST /api/customers/social-login`** agora retornam um campo `token: string` adicional. Campos existentes inalterados. Cliente da API que ignore campos desconhecidos continua funcionando.

```json
{
  "customerId": 17,
  "name": "Maria",
  "email": "maria@x.com",
  "phone": null,
  "providerId": null,
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Erros novos retornados

Endpoints de cliente:
- `401 Unauthorized` — sem header `Authorization`.
- `403 Forbidden` (`{"error":"FORBIDDEN", ...}`) — token válido mas para outro cliente, ou token de staff tentando acessar endpoint de cliente.

Endpoint de PIN:
- `403 Forbidden` quando o staff alvo não pertence ao merchant ativo do chamador.

---

## 5. Arquivos modificados

### Backend (`carimbai`)

| Arquivo | Mudança |
|---|---|
| `src/main/java/com/app/carimbai/services/JwtService.java` | Adicionado `generateCustomerToken(Customer)` e `extractTokenType(String)`. Nova property `customer-expiration-seconds`. |
| `src/main/java/com/app/carimbai/security/JwtAuthenticationFilter.java` | Ramifica por claim `type`. Carrega `Customer` quando `type=CUSTOMER`. Novo construtor recebe `CustomerRepository`. |
| `src/main/java/com/app/carimbai/utils/SecurityUtils.java` | Adicionado `getRequiredCustomerId()`. |
| `src/main/java/com/app/carimbai/dtos/customer/CustomerLoginResponse.java` | Adicionado campo `token`. |
| `src/main/java/com/app/carimbai/mappers/CustomerMapper.java` | Assinatura `customerToCustomerLoginResponse(Customer, String token)`. |
| `src/main/java/com/app/carimbai/controllers/CustomerController.java` | Injeta `JwtService`. Gera token após login/registro e propaga ao mapper. |
| `src/main/java/com/app/carimbai/config/SecurityConfig.java` | Injeta `CustomerRepository`. Rotas de cliente exigem `CUSTOMER`. `POST /api/cards` exige `CASHIER` ou `ADMIN`. |
| `src/main/java/com/app/carimbai/services/CardService.java` | Validações de ownership em `getCustomerCards`, `generateRedeemQr`, `getOrCreateCard`. |
| `src/main/java/com/app/carimbai/controllers/QrCodeController.java` | Injeta `CardRepository`. Valida que `card.customer.id == authenticatedCustomerId` antes de emitir QR. |
| `src/main/java/com/app/carimbai/services/StaffService.java` | `setPin` agora recebe `callerMerchantId` e valida via `findByStaffUserIdAndMerchantIdAndActiveTrue`. |
| `src/main/java/com/app/carimbai/controllers/AdminController.java` | Obtém `merchantId` via `SecurityUtils.getActiveMerchantId()` e passa para `setPin`. |
| `src/main/resources/application.yaml` | Adicionada property `carimbai.jwt.customer-expiration-seconds: 2592000`. |

### Frontend (`carimbai-app`)

| Arquivo | Mudança |
|---|---|
| `src/types/index.ts` | Campo `token: string` em `CustomerLoginResponse` e `CustomerData`. |
| `src/services/api.ts` | `getCustomerCards`, `getCardQR`, `getRedeemQR` agora recebem `token` e enviam `Authorization: Bearer ...`. `enrollCustomer` também recebe `token` (do staff). |
| `src/App.tsx` | Propaga `customer.token` ao `HomeScreen`. |
| `src/components/CustomerScreen.tsx` | Prop nova `customerToken`. Passa para as 3 chamadas de API. |
| `src/components/StaffScreen.tsx` | `handleEnrollCustomer` passa `session.token` para `apiService.enrollCustomer`. |

---

## 6. Como validar (end-to-end)

### Backend

```bash
# 1. Compilar
cd carimbai && ./mvnw -DskipTests compile

# 2. Subir Postgres + app local

# 3. Customer login → response inclui token
curl -X POST http://localhost:1234/api/customers/login-or-register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@a.com"}'
# → {"customerId":1, ..., "token":"eyJ..."}

# 4. Sem token: 401
curl -i http://localhost:1234/api/cards/customer/1
# → HTTP/1.1 403 (Spring devolve 403 para anonymous quando rota exige authority)

# 5. Com token do cliente 1, pedindo cartões do cliente 1: 200
curl -H "Authorization: Bearer $TOKEN_1" http://localhost:1234/api/cards/customer/1
# → 200 OK

# 6. Com token do cliente 1, pedindo cartões do cliente 2: 403
curl -H "Authorization: Bearer $TOKEN_1" http://localhost:1234/api/cards/customer/2
# → 403 FORBIDDEN

# 7. Com token do cliente 1, pedindo redeem-qr de cartão do cliente 2: 403
curl -H "Authorization: Bearer $TOKEN_1" http://localhost:1234/api/cards/2/redeem-qr
# → 403 FORBIDDEN

# 8. Admin do merchant 1 tentando trocar PIN de staff do merchant 2: 403
curl -X POST -H "Authorization: Bearer $ADMIN_M1_TOKEN" -H 'Content-Type: application/json' \
  http://localhost:1234/api/admin/staff-users/999/pin \
  -d '{"pin":"1234"}'
# → 403 FORBIDDEN
```

### Frontend

1. `cd carimbai-app && npm run dev`.
2. Login do cliente → abrir DevTools → `localStorage.getItem('carimbai_customer')` agora contém `"token":"..."`.
3. F5 na página → cartões carregam normalmente (token persiste).
4. Botão "Mostrar QR Code" funciona (gera CUSTOMER_QR autenticado).
5. Resgate de prêmio funciona.
6. Staff loga, "Inscrever Cliente em Promoção" → funciona (passa staff token agora).

---

## 7. Breaking changes

### Para clientes finais já logados (em PROD, se houver)

Sessões antigas em `localStorage.carimbai_customer` **não têm `token`**. Próxima chamada de API vai falhar com 401/403. O cliente precisa **fazer logout e login novamente** para receber a sessão nova.

**Mitigação opcional (não implementada, mas trivial):** o front pode detectar `token == null` no boot e forçar logout silencioso, redirecionando para a tela de login sem mostrar erro.

### Para qualquer cliente externo

- Quem chamava `GET /api/cards/customer/{id}`, `GET /api/qr/{id}`, `GET /api/cards/{id}/redeem-qr` sem token agora recebe 403.
- Quem chamava `POST /api/cards` sem token agora recebe 403. Único caller hoje é o front próprio, já corrigido.

### Nenhuma migration de banco foi necessária

A correção é puramente em camada de serviço + segurança. Esquema não mudou.

---

## 8. O que não foi feito (gaps conhecidos)

Esta entrega cobre **só os 3 itens do Tier 1**. Os seguintes problemas continuam abertos:

| Gap | Severidade | Próximo tier |
|---|---|---|
| Segredos default no `application-dev.yaml` | Crítico em prod | Tier 1 (fora desta entrega por solicitação do dono) |
| Refresh token (cliente expira em 30 dias, staff em 8h) | Importante | Tier 2 |
| Rate limit no `POST /api/auth/login` (anti brute force) | Importante | Tier 3 |
| Push notifications backend não existe (botão "Ativar" falha silenciosamente) | Importante | Tier 2 funcional |
| UI de gestão (criar merchants/programas/staff via Swagger só) | Bloqueador funcional | Tier 2 funcional |
| Bootstrap do primeiro ADMIN em prod | Bloqueador funcional | Tier 2 funcional |
| Customer logout button | Importante | Tier 2 funcional |
| `MethodArgumentNotValidException` retorna `getBindingResult().toString()` (verboso) | Operacional | Tier 3 |
| Sem testes automatizados | Qualidade | Tier 3 |
| `mvn ... -DskipTests` no `.github/workflows/deploy.yml` | Qualidade | Tier 3 |

---

## 9. Próximos passos recomendados

1. **Antes de prod**: resolver segredos default no `application-dev.yaml` (rotacionar JWT/HMAC secrets, remover defaults, garantir env vars em prod).
2. **Logo após**: bootstrap do primeiro admin via migration que lê env vars, e UI mínima de gestão (programas + staff).
3. **Antes de tráfego real**: refresh token para o cliente + rate limit no login do staff.
4. **Em paralelo**: decidir destino do push notifications (implementar backend completo ou esconder o botão no front).

---

*Documento gerado após implementação do Tier 1 — referência cruzada com o plano em `plans/tenho-dois-projetos-abertos-replicated-spindle.md` (seção ADENDO).*
