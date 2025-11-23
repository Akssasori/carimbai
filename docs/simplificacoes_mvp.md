# Simplificações Implementadas no MVP

## 🎯 Objetivo
Reduzir complexidade do projeto focando apenas nas funcionalidades essenciais para o MVP de um sistema de fidelidade por carimbos digitais.

## ✅ Mudanças Implementadas

### 1. Removido Fluxo B (STORE_QR)
**Arquivos Deletados:**
- `LocationsController.java` - Endpoint `/api/locations/{id}/qr`
- `StoreQrPayload.java` - DTO para fluxo STORE_QR

**Código Simplificado:**
- `StampsController.java` - Removido case `STORE_QR` do switch
- `StampsService.java` - Removido método `handleStore()`

**Justificativa:** Fluxo B permite que o cliente escaneie QR da loja. Para MVP, apenas o Fluxo A (loja escaneia QR do cliente) é suficiente.

---

### 2. Removido LocationPolicy e Flags Complexos
**Arquivos Deletados:**
- `LocationPolicy.java` - Record com flags de políticas
- `LocationPolicyService.java` - Service para processar flags

**Código Simplificado:**
- `RedeemService.java` - Removida lógica de verificação de policies

**Justificativa:** Para MVP com poucas lojas, hard-coded rules são suficientes. Policies configuráveis adicionam complexidade desnecessária.

---

### 3. Simplificado Resgate de Recompensas
**Mudanças no RedeemRequest:**
```java
// ANTES:
RedeemRequest(Long cardId, Long cashierId, String cashierPin, Long locationId)

// DEPOIS:
RedeemRequest(Long cardId, Long locationId)
```

**Mudanças no RedeemService:**
- ❌ Removido: Validação de PIN de caixa
- ❌ Removido: Dependência de `StaffService`
- ❌ Removido: Dependência de `ProgramRepository`
- ✅ Adicionado: Constante configurável `carimbai.stamps-needed` (padrão: 10)

**RedeemController:** Atualizada documentação Swagger removendo campos de cashier

**Justificativa:** Para MVP, autenticação via JWT do merchant é suficiente. PIN de caixa adiciona complexidade de gerenciamento de usuários e segurança de senhas.

---

### 4. Simplificado Rate Limit
**Antes:**
```java
checkRateLimit(cardId, locationId)
stampRepo.existsRecentByCardAndLocation(cardId, locationId, since)
```

**Depois:**
```java
checkRateLimit(cardId)
stampRepo.existsRecentByCard(cardId, since)
```

**Configuração:** `carimbai.rate-limit.seconds` (padrão: 120s)

**Justificativa:** Rate limit global por cartão é mais simples e evita abuso sem complexidade de rastrear por location.

---

### 5. Idempotência Obrigatória
**Mudanças no StampsController:**
```java
// ANTES:
@RequestHeader(name = "Idempotency-Key", required = false) String idemKey

// DEPOIS:
@RequestHeader(name = "Idempotency-Key") String idemKey
```

**Mudanças no StampsService:**
```java
// ANTES:
if (idemKey != null && !idemKey.isBlank()) {
    idempotencyService.acquireOrThrow(idemKey);
}

// DEPOIS:
idempotencyService.acquireOrThrow(idemKey);
```

**Justificativa:** Tornar obrigatório simplifica a lógica e garante que todos os stamps sejam idempotentes, evitando duplicações em retries de rede.

---

### 6. Regras de Programa Simplificadas
**Antes:**
- Entidade `Program` com `ruleTotalStamps`
- Query ao banco para buscar regra: `programRepo.findById(card.getProgram().getId())`

**Depois:**
- Constante configurável: `carimbai.stamps-needed=10`
- Sem queries adicionais ao banco

**Usado em:**
- `StampsService.handleCustomer()` - Para calcular `rewardIssued`
- `RedeemService.redeem()` - Para validar se tem carimbos suficientes

**Justificativa:** Para MVP com regra única (10 carimbos = 1 prêmio), constante é mais simples. Entidade Program pode ser adicionada posteriormente quando houver necessidade de múltiplos programas.

---

## 📊 Impacto das Mudanças

### Arquivos Removidos: 4
- `LocationsController.java`
- `LocationPolicy.java`
- `LocationPolicyService.java`
- `StoreQrPayload.java`

### Arquivos Simplificados: 6
- `StampsController.java`
- `StampsService.java`
- `StampRepository.java`
- `RedeemController.java`
- `RedeemService.java`
- `RedeemRequest.java`

### Dependências Removidas:
- `ProgramRepository` (de StampsService e RedeemService)
- `LocationPolicyService` (de RedeemService)
- `StaffService` (de RedeemService)

### Configurações Adicionadas (application.yml):
```yaml
carimbai:
  stamps-needed: 10
  rate-limit:
    seconds: 120
  hmac-secret: ${HMAC_SECRET}
```

---

## 🚀 Funcionalidades Mantidas (Core do MVP)

### ✅ Endpoints Ativos:
1. **GET /api/cards/{id}/qr** - Gera token efêmero CUSTOMER_QR
2. **POST /api/stamp** - Aplica carimbo com validações
3. **POST /api/redeem** - Resgata recompensa

### ✅ Segurança Mantida:
- **HMAC SHA-256** para assinatura de tokens
- **TTL de 45 minutos** nos tokens
- **Anti-replay** com nonce
- **Rate limit** (120s entre carimbos)
- **Idempotência obrigatória** em stamps

### ✅ Fluxo Completo:
1. Cliente abre app → mostra QR code
2. Loja escaneia → aplica carimbo
3. Sistema valida (HMAC + TTL + replay + rate + idem)
4. Incrementa contador do card
5. Quando atinge 10 carimbos → pode resgatar
6. Resgate cria Reward e zera contador

---

## 🔄 Próximos Passos (Pós-MVP)

Quando houver necessidade, considerar adicionar:

1. **Autenticação JWT** - Para merchants e customers
2. **Fluxo B (STORE_QR)** - Se houver demanda de inversão do fluxo
3. **Entidade Program** - Para múltiplos programas de fidelidade
4. **LocationPolicy** - Para regras específicas por loja
5. **StaffUser e PIN** - Para controle granular de caixas
6. **Multi-tenancy avançado** - Isolamento total por merchant
7. **Auditoria avançada** - Logs estruturados e rastreamento completo

---

## 📝 Resumo

**Antes:** Sistema enterprise-ready com múltiplos fluxos, policies complexas, multi-tenancy avançado

**Depois:** MVP focado, simples e funcional com segurança essencial

**Resultado:** Código mais limpo, menos bugs, entrega mais rápida

**Filosofia:** "Menos código = menos manutenção = mais valor"
