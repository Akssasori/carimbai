# Smoke Tests — validação manual dos FIXes

> Roda contra o backend local (`http://localhost:1234/api`) após
> `docker-compose up -d && ./mvnw spring-boot:run` (profile `local`).
> O `DevDataSeeder` popula 2 merchants, 4 staffs e 2 customers — veja o log do
> startup ou `config/DevDataSeeder.java`. Senha staff: `senhasegura123`. PIN
> caixa: `1234`.
>
> Todos os comandos abaixo usam `bash` (Git Bash no Windows funciona). Para
> PowerShell, troque as aspas simples de fora por `"` e escape o JSON com `\"`.
>
> Para inspecionar respostas: `| jq .` é opcional — se não tiver `jq`, basta
> remover.

---

## 0. Setup — variáveis úteis no shell

```bash
BASE=http://localhost:1234/api
```

---

## 1. FIX-08 — login anti-enumeração

**Hipótese:** e-mail inexistente, senha errada, usuário inativo e sem-vínculo
devem produzir **a mesma resposta**.

```bash
# 1a) e-mail inexistente
curl -s -o /dev/null -w "ghost:  %{http_code} %{size_download}b\n" \
  -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ghost@x.com","password":"qualquerum"}'

# 1b) senha errada (e-mail existe)
curl -s -o /dev/null -w "wrong:  %{http_code} %{size_download}b\n" \
  -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"platform@demo.com","password":"erradissima"}'

# 1c) corpo da resposta (deve ser idêntico nos dois)
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ghost@x.com","password":"qualquerum"}'
echo
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"platform@demo.com","password":"erradissima"}'
echo
```

**Esperado:** ambos `401` + body literal `{"error":"INVALID_CREDENTIALS"}`. O
mesmo tamanho em bytes nos dois.

---

## 2. FIX-04 — rate limit no login

**Hipótese:** a 11ª chamada na mesma janela de 1 minuto retorna 429.

```bash
for i in $(seq 1 12); do
  code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST $BASE/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"ghost@x.com","password":"x"}')
  echo "tentativa $i: $code"
done
```

**Esperado:** 1-10 → `401`; 11-12 → `429`. Header `Retry-After` presente:

```bash
curl -i -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ghost@x.com","password":"x"}' | head -10
```

⚠️ Após este teste, **espere ~60s** antes do próximo (o bucket precisa recarregar).

---

## 3. FIX-11 — login OK + claims hardened (iss/aud/jti)

```bash
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"platform@demo.com","password":"senhasegura123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')
echo "token: $TOKEN"

# Decodifica o payload (parte do meio, base64url)
PAYLOAD=$(echo "$TOKEN" | cut -d. -f2)
# Padding do base64
PAD=$(printf '%*s' $(( (4 - ${#PAYLOAD} % 4) % 4 )) '' | tr ' ' '=')
echo "$PAYLOAD$PAD" | tr '_-' '/+' | base64 -d 2>/dev/null
echo
```

**Esperado** no payload decodificado: `"iss":"carimbai"`,
`"aud":"carimbai:staff"`, `"jti":"<uuid>"`, `"role":"ADMIN"`, `"merchantId":1`.

---

## 4. FIX-11 — logout revoga o token

```bash
# Token deve funcionar antes
curl -s -o /dev/null -w "antes:    %{http_code}\n" \
  -X POST $BASE/auth/switch-merchant \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"merchantId":1}'

# Logout (idempotente, 204)
curl -s -o /dev/null -w "logout:   %{http_code}\n" \
  -X POST $BASE/auth/logout -H "Authorization: Bearer $TOKEN"

# Mesmo token agora deve ser rejeitado (401/403)
curl -s -o /dev/null -w "depois:   %{http_code}\n" \
  -X POST $BASE/auth/switch-merchant \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"merchantId":1}'
```

**Esperado:** `antes: 200`, `logout: 204`, `depois: 401` ou `403`.

---

## 5. FIX-03 — cross-tenant (admin1 não toca em merchant 2)

```bash
ADMIN1=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin1@demo.com","password":"senhasegura123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# admin1 tenta criar programa em merchant 2 (proibido)
curl -s -i -X POST $BASE/merchants/2/programs \
  -H "Authorization: Bearer $ADMIN1" -H 'Content-Type: application/json' \
  -d '{"name":"Invasor","ruleTotalStamps":10,"rewardName":"X"}' | head -3
```

**Esperado:** `HTTP/1.1 403` + body `{"error":"FORBIDDEN", ...}`. **Não** cria.

---

## 6. FIX-03 — PLATFORM_ADMIN exigido para `POST /api/merchants`

```bash
# admin1 tenta — deve falhar
curl -s -o /dev/null -w "admin1:   %{http_code}\n" \
  -X POST $BASE/merchants \
  -H "Authorization: Bearer $ADMIN1" -H 'Content-Type: application/json' \
  -d '{"name":"Novo","document":"22.222.222/0001-22"}'

# platform admin pode
PLAT=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"platform@demo.com","password":"senhasegura123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl -s -o /dev/null -w "platform: %{http_code}\n" \
  -X POST $BASE/merchants \
  -H "Authorization: Bearer $PLAT" -H 'Content-Type: application/json' \
  -d '{"name":"Novo","document":"22.222.222/0001-22"}'
```

**Esperado:** `admin1: 403`, `platform: 201`.

---

## 7. FIX-04 — PIN lockout (5 erros em 10min → bloqueia 15min)

```bash
CAIXA=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"caixa1@demo.com","password":"senhasegura123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Card pronto pra resgate (seed): cliente2 → card em p1, status READY_TO_REDEEM.
# 5 PINs errados:
for i in 1 2 3 4 5; do
  code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST $BASE/redeem \
    -H "Authorization: Bearer $CAIXA" \
    -H "X-Cashier-Pin: 9999" \
    -H 'Content-Type: application/json' \
    -d '{"cardId":2,"locationId":1}')
  echo "erro #$i: $code"
done

# 6ª tentativa: deve dar 423 PIN_LOCKED
curl -s -i -X POST $BASE/redeem \
  -H "Authorization: Bearer $CAIXA" \
  -H "X-Cashier-Pin: 1234" \
  -H 'Content-Type: application/json' \
  -d '{"cardId":2,"locationId":1}' | head -5
```

**Esperado:** 1-5 → `400 Invalid cashier PIN`; 6ª → `HTTP/1.1 423` + header
`Retry-After: ~900` + body `{"error":"PIN_LOCKED","retryAfter":...}`.

> Para destravar sem esperar 15min: reinicie o backend (estado in-memory).

---

## 8. FIX-23 — redeem sem PIN fail-safe

> ⚠️ Reinicie o backend antes deste teste se você acabou de fazer o teste 7
> (o caixa1 ficou bloqueado).

```bash
# Tenta resgate sem locationId E sem PIN — deve ser rejeitado (FIX-23)
curl -s -i -X POST $BASE/redeem \
  -H "Authorization: Bearer $CAIXA" \
  -H 'Content-Type: application/json' \
  -d '{"cardId":2}' | head -5
```

**Esperado:** `HTTP/1.1 400` + body com mensagem mencionando `PIN`.

```bash
# Com PIN correto, mesmo sem locationId, agora funciona
curl -s -X POST $BASE/redeem \
  -H "Authorization: Bearer $CAIXA" \
  -H "X-Cashier-Pin: 1234" \
  -H 'Content-Type: application/json' \
  -d '{"cardId":2}'
echo
```

**Esperado:** `{"ok":true,"rewardId":N,"cardId":2,"stampsAfter":0}`.

---

## 9. Bônus — FIX-02 Fase C / SEC-001: cartões só do dono

> Cliente não tem login direto; vamos forjar um token de cliente para o teste
> usando o `JwtService` via Swagger UI ou via teste unitário. Em produção
> isso vai via social-login real.
>
> Caminho alternativo: como `/api/customers/login-or-register` agora exige
> staff, podemos chamar como caixa para "criar" e simular — mas isso não
> emite token. **A validação real do SEC-001** vem do `CardServiceAuthzTest`
> nos unitários (5/5 verde) e do fluxo PWA em produção.

---

## 10. Sanidade — logs de auditoria saindo

```bash
# Dispara um login OK (vai gerar entrada no logger "audit")
curl -s -o /dev/null -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"platform@demo.com","password":"senhasegura123"}'

# Procure no console do backend uma linha começando com
# event=STAFF_LOGIN outcome=SUCCESS ip=127.0.0.1 path=/api/auth/login staffId=...
```

> Em `local`/`dev` o `audit` cai no console (additivity isolada em
> `logback-spring.xml`). Em prod vai pra `audit.log`.

---

## Checklist final

- [x] Test 1 — `INVALID_CREDENTIALS` idêntico nas duas formas
- [x] Test 2 — 429 na 11ª chamada + `Retry-After`
- [x] Test 3 — JWT tem `iss`/`aud`/`jti`
- [x] Test 4 — logout invalida o token
- [x] Test 5 — admin1 → 403 em merchant 2
- [x] Test 6 — `POST /merchants` só PLATFORM_ADMIN
- [x] Test 7 — PIN lockout 423 na 6ª tentativa
- [x] Test 8 — redeem sem PIN → 400
- [x] Test 10 — logs de auditoria saem no console

Se tudo passar, está pronto pro **FIX-01** (gerar segredos, configurar VPS,
limpar histórico) → deploy → bootstrap `platform_admin=TRUE` no Postgres prod.