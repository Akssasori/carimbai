---
name: security-review
description: Revisão de segurança das mudanças do branch atual no backend carimbai (segredos, validação, autorização, integridade de tokens HMAC/JWT, queries JPA).
---

# Security review — carimbai (backend)

Use ao revisar mudanças antes de abrir PR ou commitar em `main`. Foco no que é
sensível neste backend de fidelidade.

## Passos

1. **Diff em foco**: rode `git diff` (ou `git diff main...HEAD`) e revise só o
   que mudou.

2. **Segredos**: nenhum secret/chave/senha hardcoded em `.java` ou commitado.
   Conferir que `CARIMBAI_JWT_SECRET`, `CARIMBAI_HMAC_SECRET` e credenciais
   OAuth vêm de env/config. Buscar por strings suspeitas (`secret =`,
   `password`, chaves base64 longas).

3. **Validação de entrada**: todo DTO de request com Bean Validation
   apropriada; controllers usando `@Valid`. Sem confiar em campos não validados
   (ex.: ids, quantidades de selo).

4. **Autorização**: operações administrativas e de staff checam `StaffRole`.
   Cliente não consegue acessar dados de outro cliente/merchant (conferir filtros
   por `merchantId`/`customerId`). Endpoints novos passam pelo
   `JwtAuthenticationFilter`.

5. **Integridade de token**: fluxo de selo valida **assinatura HMAC** e
   **expiração** antes de aplicar; idempotência respeitada (`IdempotencyKey`)
   para evitar selo duplicado / replay.

6. **JPA**: nenhuma query com concatenação de string (injeção). Atenção a N+1 em
   novos `findAll`/relacionamentos `LAZY`.

7. **Exposição de dados**: respostas usam DTOs, não entidades cruas; não vazar
   hash de senha, segredos ou campos internos.

## Saída

Lista priorizada (Alta/Média/Baixa) com arquivo:linha e correção sugerida. Se
nada crítico, dizer explicitamente.
