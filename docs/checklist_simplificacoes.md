# Checklist de Simplificações MVP - Concluído ✅

## Arquivos Removidos (4)
- ✅ `LocationsController.java`
- ✅ `LocationPolicy.java`
- ✅ `LocationPolicyService.java`
- ✅ `StoreQrPayload.java`

## Arquivos Modificados (7)

### Controllers (2)
- ✅ `StampsController.java`
  - Removido case STORE_QR
  - Idempotency-Key agora é obrigatório
  
- ✅ `RedeemController.java`
  - Atualizada documentação Swagger

### Services (2)
- ✅ `StampsService.java`
  - Removido método handleStore()
  - Rate limit simplificado (apenas por cardId)
  - Idempotência obrigatória
  - Removida dependência de ProgramRepository
  - Usa constante configurável para stampsNeeded

- ✅ `RedeemService.java`
  - Removida validação de PIN
  - Removidas dependências: StaffService, LocationPolicyService, ProgramRepository
  - Usa constante configurável para stampsNeeded

### DTOs (1)
- ✅ `RedeemRequest.java`
  - Removidos campos: cashierId, cashierPin
  - Mantidos: cardId (obrigatório), locationId (opcional)

### Repositories (1)
- ✅ `StampRepository.java`
  - Método simplificado: existsRecentByCard() sem locationId

### Configuration (1)
- ✅ `application.yaml`
  - Adicionado: carimbai.stamps-needed=10
  - Ajustado: carimbai.rate-limit.seconds=120
  - Removido: carimbai.policy.use-location-policy

## Endpoints Ativos (3)

```
✅ GET  /api/cards/{id}/qr     - Gera token CUSTOMER_QR
✅ POST /api/stamp             - Aplica carimbo (requer Idempotency-Key)
✅ POST /api/redeem            - Resgata recompensa
```

## Segurança Mantida

- ✅ HMAC SHA-256
- ✅ TTL 45 minutos
- ✅ Anti-replay (nonce)
- ✅ Rate limit (120s)
- ✅ Idempotência obrigatória

## Configurações Necessárias

```yaml
carimbai:
  hmac-secret: ${CARIMBAI_HMAC_SECRET:dev-secret-change-me}
  stamps-needed: 10
  rate-limit:
    seconds: 120
```

## Próximos Passos

1. **Testar os endpoints** com as mudanças
2. **Atualizar testes unitários** (se houver)
3. **Validar frontend** - Remover envio de cashierId/PIN no redeem
4. **Frontend** - Sempre enviar Idempotency-Key no POST /api/stamp
5. **Deploy** - Configurar variável de ambiente CARIMBAI_HMAC_SECRET

## Documentação

📄 Veja `docs/simplificacoes_mvp.md` para detalhes completos
