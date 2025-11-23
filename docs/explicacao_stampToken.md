## StampTokenService – Visão Técnica

O `StampTokenService` implementa o núcleo do antifraude dos QRs efêmeros (fluxos A e futuramente B).

### Objetivo

Gerar e validar tokens de carimbo (stamp) de curtíssima duração:
- Evitar falsificação (HMAC).
- Evitar replay (nonce único persistido).
- Reduzir janela de uso indevido (TTL ~45s).

### Contrato

- `issueCustomer(cardId)` / `issueStore(locationId)` → gera token temporário.
- `validateAndConsume(payload)` → valida expiração, assinatura e uso único; registra no banco.

### Estrutura do Token

Campos retornados ao front:
| Campo    | Significado                                |
|----------|---------------------------------------------|
| `type`   | `CUSTOMER_QR` ou `STORE_QR`                |
| `idRef`  | Referência (cardId ou locationId)          |
| `nonce`  | UUID único por emissão                     |
| `exp`    | Epoch seconds da expiração                 |
| `sig`    | Assinatura HMAC do payload                 |

Payload assinado atual: `idRef|nonce|exp` (recomendado evoluir para `type|idRef|nonce|exp`).

### Fluxo (CUSTOMER_QR)

1. Cliente abre “Meu cartão” → backend emite token (`issueCustomer`).
2. PWA mostra QR com os campos.
3. Painel do lojista lê e envia para `/stamp`.
4. Controller cria `TokenPayload` → chama `validateAndConsume`.
5. Serviço valida:
   - Expiração (TTL).
   - Assinatura (HMAC).
   - Anti-replay (consulta nonce).
6. Persiste uso do token.
7. Incrementa `stampsCount` no `Card` e registra `Stamp`.

### Segurança Implementada

- Integridade & Autenticidade: HMAC SHA-256 com segredo interno.
- Efemeridade: expiração curta (reduz reuso).
- Anti-replay: nonce gravado, bloqueia segundo uso.
- Comparação constante da assinatura (mitiga timing attacks).
- Separação semântica por `type`.

### Por que ThreadLocal<Mac>?

`Mac` não é thread-safe. Criá-lo e inicializá-lo a cada requisição custa CPU.

Vantagens do `ThreadLocal<Mac>`:
- Uma instância por thread do pool (Tomcat/Jetty/Undertow).
- Sem sincronização/locks.
- Reuso eficiente (apenas `reset()` + `doFinal()`).
- Reduz garbage e latência.

Alternativas menos ideais:
- Criar novo `Mac` por chamada (mais lento).
- Instância única sincronizada (contende).
- Pool manual (mais complexo).

### Por que não usar JWT?

| Aspecto        | HMAC simples | JWT |
|----------------|--------------|-----|
| Tamanho        | Menor        | Maior (header + claims) |
| Replay         | Manual (nonce) | Também requer controle de `jti` |
| Complexidade   | Baixa        | Moderada |
| Necessidade    | Poucas claims | Overkill para 3–4 campos |

Aqui o objetivo é leveza para QR e validação direta.

### Melhorias Sugeridas

1. Incluir `type` no payload assinado.
2. Injetar `Clock` (testabilidade).
3. Preencher `tokenId` no `Stamp` (ligar stamp ↔ token).
4. Rate limit complementar (ex: por `cardId`/minuto).
5. Serialização compacta única (ex: `type.idRef.nonce.exp.sig`).
6. Log estruturado de falhas: motivo (expired, invalid_signature, replay).
7. Uso de DTO tipado para `StampRequest` (remover `Object payload`).
8. Adicionar assinatura do IP / UA opcional para auditoria reforçada.
9. Validar se `cardId` pertence ao merchant/contexto atual (autorização).

### constantTimeEquals

Compara duas strings sem parar no primeiro byte diferente. Minimiza vazamento de tempo que poderia ajudar um atacante a derivar assinaturas.

### Resumo Rápido

O serviço garante que cada QR lido:
- É genuíno (HMAC).
- Não expirou.
- Nunca foi usado antes.
Só então registra o selo. `ThreadLocal<Mac>` é otimização segura para alta frequência sem lock. Reforçar payload assinado e tipagem dos DTOs é próximo passo para robustez.

### Exemplo Recomendado de Payload Futuro

```
type|idRef|nonce|exp
CUSTOMER_QR|128|550e8400-e29b-41d4-a716-446655440000|1731345678
```

Assinatura (Base64 URL): `Kp7iL2yA7dAqfQ9T8jF3YQ...`

QR pode carregar JSON ou formato compacto:

```json
{
  "t":"CUSTOMER_QR",
  "r":128,
  "n":"550e8400-e29b-41d4-a716-446655440000",
  "e":1731345678,
  "s":"Kp7iL2yA7dAqfQ9T8jF3YQ..."
}
```

---

Se quiser já te mando a versão revisada do código com essas melhorias. É só pedir.