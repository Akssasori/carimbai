# Análise do Projeto de Fidelidade

Analisando seu projeto de fidelidade, vejo que está bem estruturado mas com complexidade excessiva para algo simples. Aqui estão melhorias focadas em simplificação:

## 🎯 Simplificações Recomendadas

### 1. Remover/Adiar Features Desnecessárias

- ❌ **Fluxo B (STORE_QR)**: Já está desabilitado (501). Remova todo código relacionado até precisar
- ❌ **LocationPolicy e flags complexos**: Se tem 1 loja, não precisa de políticas avançadas ainda
- ❌ **Rate limit por location específico**: Use só por `cardId` globalmente
- ❌ **StaffUser e PIN de caixa**: Para MVP, apenas validação simples de merchant

### 2. Simplificar Segurança (manter o essencial)

**MANTER:**
- ✅ HMAC no token (core security)
- ✅ TTL de 45min (evita QR antigo)
- ✅ Anti-replay com nonce (evita reuso)

**SIMPLIFICAR:**
- ⚠️ **Idempotency-Key**: Fazer obrigatório ao invés de opcional. Gere no front sempre
- ⚠️ **Rate limit**: Simplifique para "1 carimbo a cada 2min" sem complicações de location
- ⚠️ **PIN do caixa**: Deixe para fase 2. MVP: apenas autenticação do merchant

### 3. Consolidar Entidades
```
ATUAL: Program → Card → Customer + Stamp + StampToken + Reward
SIMPLIFICADO: Card → Customer + Stamp + Reward (program rules direto no Card)
```

Para MVP:
- Regra fixa: 10 carimbos = 1 prêmio
- Sem necessidade de entidade `Program` por enquanto
- Zerar contador ao resgatar é suficiente (sem versionamento complexo)

### 4. Endpoints - Manter apenas o essencial
```
✅ GET  /api/cards/{id}/qr        (gerar QR)
✅ POST /api/stamp                (aplicar carimbo)
✅ POST /api/redeem               (resgatar prêmio)
❌ GET  /api/locations/{id}/qr    (remover até precisar)
```

### 5. Melhorias Simples e Efetivas

#### A. Tornar idempotência obrigatória
```javascript
// No front, sempre gere:
headers: {
  'Idempotency-Key': `${cardId}-${timestamp}-${uuid()}`
}
```

#### B. Simplificar rate limit
```java
// Apenas: "último carimbo deste card foi há menos de 2min?"
if (lastStamp.timestamp > now - 120s) throw TooSoonException();
```

#### C. Resgate sem PIN (MVP)
```java
// POST /api/redeem
{ "cardId": "123", "merchantId": "456" }
// Validação: JWT do merchant é suficiente
```

#### D. Remover logging "estruturado" complexo
```java
// MVP: Log simples é suficiente
logger.info("Stamp applied: cardId={} stampsCount={}", cardId, count);
```

## 📉 O que REMOVER do código

1. StaffUser e cashierPin → Adiar para fase 2
2. LocationPolicy e flags → Hard-coded rules
3. Fluxo STORE_QR completo → Código morto
4. Program como entidade → Migrar rules para config/constante
5. ThreadLocal no HMAC → Instanciar direto (premature optimization)
6. Multi-segredo e rotação → Over-engineering para MVP

## ✅ O que FOCAR

1. Fluxo único simples: Cliente mostra QR → Loja escaneia → Carimbo aplicado
2. Segurança básica: HMAC + TTL + anti-replay + idempotência obrigatória
3. Regra fixa: 10 carimbos = prêmio automático
4. 1 endpoint por ação: `/qr`, `/stamp`, `/redeem`

## 🎓 Resumo

Para algo simples, você está implementando features de scale/enterprise que não precisa agora:

- Multi-tenancy complexo → Use merchantId simples
- Policies configuráveis → Hard-code as regras
- Dois fluxos de QR → Mantenha apenas 1
- PIN de caixa → JWT do merchant é suficiente

**Menos código = menos bugs = entrega mais rápida.**