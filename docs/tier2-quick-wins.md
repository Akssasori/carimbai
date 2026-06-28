# Tier 2 — Quick wins funcionais

## Contexto

Auditoria identificou seis gaps funcionais que tornavam o MVP impossível de operar em produção. Este documento descreve a primeira leva — as "quick wins" — que resolvem cinco desses gaps com mudanças cirúrgicas (~3-4h de trabalho), deixando os dois itens grandes (UI de gestão e push notifications backend completo) para entregas dedicadas.

**Itens cobertos por esta entrega:**
1. Bootstrap do primeiro admin em produção.
2. Botão de logout do cliente.
3. Esconder botões que apontam para features inexistentes (push notifications, esqueci-senha).
4. Estender TTL do JWT do staff de 8h para 12h.
5. Tratar 401 no front (limpar sessão + redirect para login).

**Itens explicitamente deixados para depois:**
- **UI de gestão** (criar/listar/desativar programas, staff, locations) — Tier 2 funcional fase 2.
- **Push notifications backend completo** (webpush lib + tabela `push_subscriptions` + sender + endpoints VAPID/subscribe) — Tier 2 funcional fase 2.
- **Refresh token real** — Tier 3.
- **Endpoint de reset de senha do staff** — Tier 3.

---

## 1. Bootstrap do primeiro admin

### Problema

`POST /api/staff-users` exige `@PreAuthorize("hasAuthority('ADMIN')")`. Em produção, sem nenhum admin cadastrado, **não há como criar o primeiro ADMIN** sem `INSERT` manual no banco. O `DevDataSeeder` resolve isso apenas no profile `stg`.

### Solução

[`BootstrapAdminInitializer`](carimbai/src/main/java/com/app/carimbai/config/BootstrapAdminInitializer.java) — `@Component` que implementa `CommandLineRunner` e roda em **qualquer profile** quando as variáveis de ambiente estão configuradas.

**Comportamento:**
- Lê `CARIMBAI_BOOTSTRAP_ADMIN_EMAIL` e `CARIMBAI_BOOTSTRAP_ADMIN_PASSWORD`.
- Se ambas estiverem vazias → não faz nada (log debug).
- Se já existe staff com aquele email → não faz nada (log info, idempotente).
- Senão:
  - Resolve merchant: se `CARIMBAI_BOOTSTRAP_MERCHANT_ID` estiver setada, anexa ao existente; senão cria um novo merchant com nome de `CARIMBAI_BOOTSTRAP_MERCHANT_NAME` (default `"Default Merchant"`).
  - Cria `core.staff_users` com `bcrypt(password)` e `active=true`.
  - Cria `core.staff_user_merchants` com `role=ADMIN`, `active=true`, `is_default=true`.
  - Loga em nível WARN: "Bootstrap admin criado ... Recomenda-se rotacionar a senha no primeiro login."

### Como usar em produção

```bash
# Primeira subida em produção:
export CARIMBAI_BOOTSTRAP_ADMIN_EMAIL=admin@minhaempresa.com
export CARIMBAI_BOOTSTRAP_ADMIN_PASSWORD=trocar-isso-no-primeiro-login
export CARIMBAI_BOOTSTRAP_MERCHANT_NAME="Minha Empresa"
# (opcional) export CARIMBAI_BOOTSTRAP_MERCHANT_ID=42  # se merchant já existe

./mvnw spring-boot:run
# ou docker run -e CARIMBAI_BOOTSTRAP_ADMIN_EMAIL=... carimbai

# Após primeira execução bem-sucedida, pode REMOVER as env vars
# (vão ser ignoradas em runs subsequentes porque o admin já existe).
```

### Riscos / cuidados

- **A senha é passada em texto puro via env var.** Aceitável para bootstrap único; usuário deve trocar via fluxo normal de login depois.
- **Reset de senha do staff ainda não existe** (endpoint não implementado). Se você esquecer essa senha bootstrap, hoje a única saída é `UPDATE core.staff_users SET password_hash = ...` direto no banco. Próxima entrega cobre isso.
- **Sem 2FA, sem audit log de uso da senha bootstrap.**

---

## 2. Botão de logout do cliente

### Problema

`CustomerScreen` não tinha botão de logout. O usuário ficava preso no app — única forma de sair era limpar `localStorage` manualmente via DevTools.

### Solução

- [`useCustomer`](carimbai-app/src/hooks/useCustomer.ts) já expunha `logout()` (limpa `localStorage.carimbai_customer` e zera o estado React).
- [`App.tsx`](carimbai-app/src/App.tsx): agora desestrutura `logout` do hook e passa como prop `onLogout` para `HomeScreen`.
- [`CustomerScreen.tsx`](carimbai-app/src/components/CustomerScreen.tsx): nova prop `onLogout: () => void`. Adicionado botão de logout no canto direito do navbar, ao lado do avatar (ícone de "sair", estilo coerente com o resto da UI). Quando clicado, chama `onLogout()` → `App` re-renderiza → como `customer` fica `null`, o branch `<CustomerOnboarding />` é mostrado.
- [`CustomerScreen.css`](carimbai-app/src/components/CustomerScreen.css): novas classes `.nav-right` (flex container) e `.nav-logout` (botão circular semelhante ao avatar).

Nenhuma chamada de API envolvida — logout é puramente local (a sessão do cliente é apenas um JWT no localStorage; não há sessão server-side a invalidar).

---

## 3. Esconder features inexistentes

### Decisão

Optamos por **esconder** botões que apontam para features ainda não implementadas no backend, em vez de mantê-los visíveis com toast "em breve". Razão: promessas quebradas geram suporte e fricção; ausência é silenciosa.

### 3.1 Botão "Ativar notificações"

- Hook `usePushNotifications` chama `apiService.getVapidPublicKey()` e `apiService.subscribePush()`. Ambos os endpoints (`/api/notifications/vapid-public-key` e `/api/notifications/subscribe`) **não existem** no backend (busca por "notification" em `src/main/java` retorna zero arquivos).
- Em [`CustomerScreen.tsx`](carimbai-app/src/components/CustomerScreen.tsx): import do hook **comentado** (não removido — fácil reabilitar quando o backend for implementado). Bloco JSX do botão removido com comentário explicativo. Os métodos `getVapidPublicKey` e `subscribePush` em `api.ts` continuam definidos (apenas não chamados).
- O hook em si (`usePushNotifications.ts`) continua intocado.

### 3.2 Link "Esqueceu a senha?"

- [`StaffLogin.tsx`](carimbai-app/src/components/StaffLogin.tsx): handler `handleForgotPassword` era um `console.log('Recuperar senha')` stub. Não há endpoint correspondente no backend.
- Removido o bloco JSX (link + divisor "ou") e a função handler. Comentário explica que será reintroduzido quando o endpoint de reset existir.
- As entradas em `styles` (`divider`, `dividerLine`, `dividerText`, `forgotLink`, `link`) ficaram não usadas — não removi para minimizar diff e manter o estilo disponível para reuso futuro.

---

## 4. JWT do staff: 8h → 12h

### Problema

JWT do staff expirava em 28800s (8h). Um turno típico em loja é 8-10h. Staff trabalhando, fazia uma ação após o expiry → recebia 401 com mensagem genérica do fetch.

### Solução

[`application.yaml`](carimbai/src/main/resources/application.yaml): `carimbai.jwt.expiration-seconds: 43200` (12 horas).

Por que 12h e não mais:
- Cobre turno completo + folga.
- Não vira "lifetime" (sessão indefinida = risco se token vaza).
- Refresh token real fica para o Tier 3 — quando vier, podemos reduzir para 1h e rotacionar.

Token de cliente fica em 30 dias (`customer-expiration-seconds: 2592000`) — uso esporádico, não faz sentido pedir login frequente.

---

## 5. Tratar 401 no front

### Problema

Antes desta entrega, expiração de token manifestava como:
- Filter JWT pegava token expirado, chamava `SecurityContextHolder.clearContext()` silenciosamente, seguia o filterChain.
- Spring Security, com a sessão anônima, negava o `.hasAuthority(...)` com **403** (não 401).
- Frontend via `Erro 403 - Forbidden` em um toast genérico — sem clear de localStorage, sem redirect.

Resultado: usuário ficava preso numa tela quebrada, com sessão zumbi no localStorage que continuava falhando todas as chamadas.

### Solução em duas pontas

**Backend** — [`JwtAuthenticationFilter`](carimbai/src/main/java/com/app/carimbai/security/JwtAuthenticationFilter.java):

Quando um `Authorization: Bearer ...` é enviado mas o token está expirado ou inválido (assinatura ruim, payload corrompido), o filter agora responde **401 explícito** e aborta o chain:

```java
if (jwtService.isExpired(token)) {
    SecurityContextHolder.clearContext();
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
    return;
}
// ... e no catch:
} catch (JwtException | IllegalArgumentException e) {
    SecurityContextHolder.clearContext();
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
    return;
}
```

Endpoints públicos (que não recebem Bearer) continuam funcionando — o `if (StringUtils.hasText(header) && header.startsWith("Bearer "))` guarda o caminho novo.

**Frontend** — [`api.ts`](carimbai-app/src/services/api.ts):

Helper centralizado `handleUnauthorized(response, kind)`:

```ts
function handleUnauthorized(response: Response, kind: 'staff' | 'customer'): void {
  if (response.status !== 401) return;
  const key = kind === 'staff' ? 'carimbai_staff_session' : 'carimbai_customer';
  localStorage.removeItem(key);
  window.location.replace(kind === 'staff' ? '/staff' : '/');
  throw new Error('Sessão expirada. Faça login novamente.');
}
```

Aplicado em todos os 10 métodos autenticados do `apiService`, com o `kind` correto (`'staff'` para staff endpoints, `'customer'` para customer endpoints):

| Método | kind |
|---|---|
| `redeem` | staff |
| `switchMerchant` | staff |
| `enrollCustomer` | staff |
| `getCustomerCards` | customer |
| `getCardQR` | customer |
| `applyStamp` | staff |
| `getRedeemQR` | customer |
| `redeemWithQr` | staff |
| `getStaffDashboardMetrics` | staff |
| `getRecentStamps` | staff |
| `getRecentRewards` | staff |

**Não trato 403** porque 403 com token válido é um ownership violation legítimo (ex: cliente A tentando ver cartão do cliente B) — não queremos forçar logout nesse caso. Só 401 (= sessão claramente inválida do ponto de vista do servidor) dispara o redirect.

### UX resultante

- Staff carimbando, token expira → próxima ação → 401 → localStorage limpo → redirect para `/staff` → tela de login com input pronto.
- Mesma coisa para cliente: 401 → `/`.
- `window.location.replace()` (não `push`) — não polui o histórico do browser.

---

## 6. Arquivos modificados

### Backend (`carimbai`)

| Arquivo | Mudança |
|---|---|
| `src/main/java/com/app/carimbai/config/BootstrapAdminInitializer.java` | **Novo.** `@Component` + `CommandLineRunner` que cria admin se env vars setadas. |
| `src/main/java/com/app/carimbai/security/JwtAuthenticationFilter.java` | Responde 401 explícito quando token enviado é expirado/inválido. |
| `src/main/resources/application.yaml` | `expiration-seconds: 43200` (era 28800). |

### Frontend (`carimbai-app`)

| Arquivo | Mudança |
|---|---|
| `src/App.tsx` | Desestrutura `logout` de `useCustomer()` e passa como `onLogout` para `HomeScreen`. |
| `src/components/CustomerScreen.tsx` | Nova prop `onLogout`. Botão de logout no navbar. Hook `usePushNotifications` comentado, bloco do botão de push removido. |
| `src/components/CustomerScreen.css` | Classes `.nav-right` e `.nav-logout`. |
| `src/components/StaffLogin.tsx` | Removido bloco "Esqueceu a senha?" + handler stub. |
| `src/services/api.ts` | Helper `handleUnauthorized` + aplicado em 10 métodos autenticados. |

---

## 7. Verificação end-to-end

### Bootstrap admin

```bash
# 1. Banco vazio (ou sem o email):
docker-compose up -d postgres
export CARIMBAI_BOOTSTRAP_ADMIN_EMAIL=test@admin.com
export CARIMBAI_BOOTSTRAP_ADMIN_PASSWORD=Test123!
./mvnw spring-boot:run
# → Log "Bootstrap admin criado: email=test@admin.com, staffId=N, merchantId=M, merchantName=Default Merchant"

# 2. Reiniciar com mesmas env vars:
./mvnw spring-boot:run
# → Log "Bootstrap: admin test@admin.com ja existe, nao faz nada."

# 3. Login funciona:
curl -X POST http://localhost:1234/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@admin.com","password":"Test123!"}'
# → 200 com token
```

### Logout cliente

1. `npm run dev`.
2. Login do cliente.
3. Conferir botão de logout no navbar (ícone de "sair" ao lado do avatar).
4. Clicar → cai na tela de onboarding. `localStorage.carimbai_customer` removido.

### Botões escondidos

1. Tela do cliente: não há mais botão "Ativar" notificações.
2. Tela `/staff` (login do staff): não há mais "Esqueceu a senha?".

### TTL 12h

- Login do staff → decodificar JWT em jwt.io → `exp - iat == 43200`.

### 401 handler

**Backend**:
```bash
# Token expirado:
curl -H "Authorization: Bearer eyJ.expired.token" http://localhost:1234/api/staff/dashboard/metrics
# → HTTP/1.1 401 Unauthorized

# Token malformado:
curl -H "Authorization: Bearer not-a-jwt" http://localhost:1234/api/staff/dashboard/metrics
# → HTTP/1.1 401 Unauthorized
```

**Front-end**:
1. Logar como staff.
2. No DevTools → Application → localStorage, manualmente trocar o `token` em `carimbai_staff_session` por uma string lixo.
3. Próxima ação na UI → automaticamente redireciona para `/staff` (tela de login). localStorage limpo.

---

## 8. Breaking changes

Nenhum. Todas as mudanças são puramente aditivas ou correções de comportamento que já estavam quebrados:

- Nenhum endpoint mudou contrato.
- Nenhuma migration de banco.
- Frontend: chamadas existentes seguem funcionando. Sessões antigas válidas continuam válidas (TTL estendido só beneficia tokens **novos**; tokens já emitidos com 8h não retroagem).

---

## 9. Gaps que continuam abertos

Para Tier 2 fase 2 (sessão dedicada):

| Gap | Esforço | Por que precisa |
|---|---|---|
| UI de gestão (programas + staff + locations) | 6-8h | Hoje, admin precisa do Swagger para tudo. Não dá pra entregar para cliente final. |
| Push notifications backend completo | 6-10h | Hoje só está escondido. Decisão de produto: implementar ou descartar. |

Para Tier 3:

| Gap | Esforço |
|---|---|
| Refresh token real | 4-8h |
| Endpoint de reset de senha do staff | 4-6h (precisa SMTP) |
| Rate limit no `POST /api/auth/login` | 1-2h (Bucket4j) |
| Audit log estruturado | 2-3h |
| Mensagem do `MethodArgumentNotValidException` formatada como JSON limpo | 30min |
| Testes automatizados e remoção do `-DskipTests` do CI | contínuo |

---

*Documento gerado após implementação dos quick wins do Tier 2. Referência cruzada: [tier1-correcoes-seguranca.md](./tier1-correcoes-seguranca.md).*
