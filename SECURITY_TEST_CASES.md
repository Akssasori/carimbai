# Casos de Teste de Segurança — carimbai

> Gerado na **Fase 5** do `SECURITY_LGPD_PLAN.md` (autenticação e sessão). Reúne
> **testes manuais** (executáveis em ambiente autorizado — ver RoE, seção 2.1 do
> plano) e **propostas de testes automatizados** (snippets; **não** foram
> adicionados ao `src/test` para não alterar o projeto sem autorização — isso é
> a Fase 13).
>
> ⚠️ Os testes ativos abaixo **só devem rodar em staging/local autorizado**. Não
> executar contra produção. Achados referenciados estão no `SECURITY_REPORT.md`.
>
> Convenção de IDs: `TC-AUTH-xx` (autenticação/sessão). Demais famílias
> (`TC-AUTHZ`, `TC-INPUT`, `TC-DATA`) serão criadas nas Fases 6–8.

---

## 1. Testes manuais — Autenticação e sessão

### 1.1. Login de staff

| ID | Cenário | Passos | Resultado esperado | Achado relacionado |
|---|---|---|---|---|
| TC-AUTH-01 | Senha errada várias vezes | `POST /api/auth/login` com senha incorreta 10× | Sempre `400` genérico `Invalid credentials`; **deveria** haver rate limit/lockout | SEC-006 |
| TC-AUTH-02 | Resposta não revela existência do e-mail | Login com e-mail inexistente vs. e-mail existente + senha errada | Respostas **idênticas**; hoje conta inativa/sem vínculo retornam `409` distintos → enumeração | SEC-008 |
| TC-AUTH-03 | Brute force de senha | Script com lista de senhas contra 1 e-mail | Esperado: bloqueio/atraso após N tentativas. Atual: sem limite | SEC-006, SEC-016 |
| TC-AUTH-04 | Política de senha fraca | Criar staff com senha `1` (`POST /api/staff-users`, como ADMIN) | Esperado: `400` (senha curta). Atual: aceita | SEC-016 |
| TC-AUTH-05 | Hash da senha | Inspecionar `core.staff_users.password_hash` | Valor BCrypt (`$2a$...`), nunca texto puro | OK (controle positivo) |

### 1.2. JWT

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-AUTH-06 | Endpoint privado sem token | `POST /api/stamp` sem `Authorization` | `401`/`403` (negado) | OK |
| TC-AUTH-07 | JWT expirado é rejeitado | Usar token com `exp` no passado | Negado; `SecurityContext` não autenticado | OK |
| TC-AUTH-08 | Assinatura adulterada | Alterar payload/assinatura do JWT | Negado (`JwtException` → contexto limpo) | OK |
| TC-AUTH-09 | `alg=none` / algorithm confusion | Enviar JWT com `alg:none` ou HS forjado | Negado (jjwt exige HMAC) | OK |
| TC-AUTH-10 | Revogação por desativação | Desativar staff (`active=false`) e reusar token válido | Próxima request **negada** (filtro recarrega usuário) | OK (kill switch) |
| TC-AUTH-11 | Sem `iss`/`aud` | Decodificar JWT | Faltam `iss`/`aud`/`jti` | SEC-012 |
| TC-AUTH-12 | Fallback de segredo | Subir app sem `CARIMBAI_JWT_SECRET` | **Não deveria** subir; hoje usa fallback hardcoded | SEC-003 |

### 1.3. Sessão, logout, timeout, concorrência

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-AUTH-13 | Session fixation | Verificar uso de `JSESSIONID` para auth | N/A — API `STATELESS` (sem sessão) | OK |
| TC-AUTH-14 | Logout invalida token | (Não há endpoint de logout) | Token válido até `exp` (8h); só desativação revoga | SEC-012 |
| TC-AUTH-15 | Idle timeout | Token ocioso por horas e reusado < 8h | Aceito (sem idle timeout) | SEC-012 |
| TC-AUTH-16 | Sessões concorrentes | Logar 2× e usar ambos os tokens | Ambos válidos (sem limite) | SEC-012 |

### 1.4. PIN do caixa

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-AUTH-17 | Brute force de PIN | `POST /api/redeem` com PINs `0000..9999` (mesmo JWT) | Esperado: lockout. Atual: sem limite | SEC-017 |
| TC-AUTH-18 | Formato do PIN | `setPin` com `"abcd"` | Esperado: `400` (só dígitos). Atual: aceita | SEC-017 |
| TC-AUTH-19 | PIN hash | Inspecionar `pin_hash` | BCrypt, nunca texto puro | OK |

### 1.5. Login social

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-AUTH-20 | Token social inválido | `POST /api/customers/social-login` token aleatório | `401 INVALID_SOCIAL_TOKEN` | OK |
| TC-AUTH-21 | Audience errado (Apple/Google) | Token de outro `client_id` | Negado (audience checado) | OK |
| TC-AUTH-22 | Facebook sem `appsecret_proof` | Verificar comportamento quando `appSecret` ausente | Hoje segue sem proof (`""`) | SEC-014 |
| TC-AUTH-23 | PII em log no erro do Facebook | Forçar resposta não-200 da Graph API | Log **não** deve conter id/nome/e-mail | SEC-015 |

### 1.6. Recuperação de senha (quando implementada)

| ID | Cenário | Resultado esperado | Achado |
|---|---|---|---|
| TC-AUTH-24 | Reset para e-mail existente vs. inexistente | Respostas **genéricas idênticas** | SEC-019 |
| TC-AUTH-25 | Token de reset expirado | Rejeitado | SEC-019 |
| TC-AUTH-26 | Reuso de token de reset | Rejeitado (uso único) | SEC-019 |
| TC-AUTH-27 | Token de reset em logs | Nunca logado | SEC-019, SEC-009 |

---

## 2. Testes automatizados propostos (não aplicados)

> Snippets para `src/test/java` usando `@SpringBootTest` + `MockMvc`. São
> **propostas** (Fase 13). Implementar mediante autorização. Ajustar fixtures
> conforme `DevDataSeeder`.

### TC-AUTH-06 — Endpoint privado sem token retorna 401/403

```java
@SpringBootTest
@AutoConfigureMockMvc
class StampAuthTest {

    @Autowired MockMvc mvc;

    @Test
    void stampWithoutToken_isDenied() throws Exception {
        mvc.perform(post("/api/stamp")
                .header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CUSTOMER_QR\",\"payload\":{}}"))
           .andExpect(status().is4xxClientError()); // 401/403
    }
}
```

### TC-AUTH-07 — JWT expirado é rejeitado

```java
@Test
void expiredJwt_isRejected() throws Exception {
    String expired = Jwts.builder()
        .subject("1").claim("role", "ADMIN").claim("merchantId", 1L)
        .issuedAt(Date.from(Instant.now().minusSeconds(36000)))
        .expiration(Date.from(Instant.now().minusSeconds(60)))
        .signWith(testSigningKey)   // mesma chave de teste do JwtService
        .compact();

    mvc.perform(post("/api/redeem")
            .header("Authorization", "Bearer " + expired)
            .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1}"))
       .andExpect(status().isForbidden());
}
```

### TC-AUTH-02/08 — Login genérico (anti-enumeração)

```java
@Test
void login_unknownEmail_and_wrongPassword_areIndistinguishable() throws Exception {
    var r1 = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
            .content("{\"email\":\"naoexiste@x.com\",\"password\":\"x\"}")).andReturn();
    var r2 = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
            .content("{\"email\":\"caixa@demo.com\",\"password\":\"errada\"}")).andReturn();

    // Esperado (após corrigir SEC-008): mesmo status e mesmo corpo
    assertThat(r1.getResponse().getStatus()).isEqualTo(r2.getResponse().getStatus());
    assertThat(r1.getResponse().getContentAsString())
        .isEqualTo(r2.getResponse().getContentAsString());
}
```

### TC-AUTH-09 — `alg=none` é rejeitado

```java
@Test
void algNoneToken_isRejected() throws Exception {
    // header {"alg":"none"} + payload, sem assinatura
    String unsigned = base64Url("{\"alg\":\"none\"}") + "."
                    + base64Url("{\"sub\":\"1\",\"role\":\"ADMIN\",\"merchantId\":1}") + ".";
    mvc.perform(post("/api/admin/staff-users/1/pin")
            .header("Authorization", "Bearer " + unsigned)
            .contentType(APPLICATION_JSON).content("{\"pin\":\"1234\"}"))
       .andExpect(status().isForbidden());
}
```

### TC-AUTH-04 — Política de senha (esperado FALHAR até corrigir SEC-016)

```java
@Test
void createStaff_withWeakPassword_isRejected() throws Exception {
    mvc.perform(post("/api/staff-users")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(APPLICATION_JSON)
            .content("{\"merchantId\":1,\"email\":\"novo@x.com\",\"password\":\"1\",\"role\":\"CASHIER\"}"))
       .andExpect(status().isBadRequest()); // hoje retorna 201 → vira regressão guard após o fix
}
```

### TC-AUTH-10 — Desativar usuário revoga o token

```java
@Test
void deactivatedUser_tokenIsRevoked() throws Exception {
    // 1) login -> token ; 2) marcar active=false no repo ; 3) request com o mesmo token
    staffRepo.findById(cashierId).ifPresent(u -> { u.setActive(false); staffRepo.save(u); });
    mvc.perform(post("/api/redeem").header("Authorization", "Bearer " + cashierToken)
            .contentType(APPLICATION_JSON).content("{\"cardId\":1}"))
       .andExpect(status().isForbidden());
}
```

---

## 3. Cobertura e prioridade

| Prioridade | Testes | Objetivo |
|---|---|---|
| Alta | TC-AUTH-06/07/08/09/10/12 | Garantias core de JWT/autorização (impedem regressão das proteções existentes) |
| Alta | TC-AUTH-01/02/03 | Brute force e enumeração no login (SEC-006/008) |
| Média | TC-AUTH-04/17/18 | Política de senha e PIN (SEC-016/017) |
| Média | TC-AUTH-20/21/22/23 | Login social e PII em log (SEC-014/015) |
| Baixa | TC-AUTH-13..16 | Documentar comportamento de sessão (SEC-012) |
| Futuro | TC-AUTH-24..27 | Reset de senha quando existir (SEC-019) |

> **DAST (opcional, Fase 13):** complementar com `zap-baseline.py -t <staging>`
> para checagem de headers/cookies de autenticação — **somente** em ambiente
> autorizado, nunca produção.

## 4. Critério de aceite da Fase 5

- [x] Hash de senha validado (BCrypt — OK; política de senha → SEC-016).
- [x] Reset de senha avaliado (inexistente → SEC-019; checklist para implementação).
- [x] JWT/sessão validado, incl. fixation (N/A, stateless) e timeout (SEC-012).
- [x] Riscos de brute force avaliados (login SEC-006; PIN SEC-017).
- [x] MFA avaliado (SEC-018 — TOTP viável para ADMIN).
- [x] Testes documentados (manuais + automatizados propostos).

> Nenhuma alteração no fluxo de autenticação foi feita nesta fase.

---

# Fase 6 — Autorização e isolamento de dados

> Família `TC-AUTHZ-xx`. Objetivo: provar que **um usuário não acessa nem altera
> dados de outro** (IDOR/BOLA, escalação horizontal entre lojistas e vertical).
> Achados relacionados: SEC-001/002/004 (BOLA sem auth no cliente), **SEC-020**
> (escalação horizontal entre merchants nos endpoints ADMIN), SEC-017 (PIN
> cross-tenant). Rodar apenas em ambiente autorizado.

## 5. Testes manuais — Autorização e isolamento

### 5.1. Escalação vertical (papel) — esperado já PASSAR (controle existente)

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-AUTHZ-01 | CASHIER tenta endpoint ADMIN | Com JWT de CASHIER: `POST /api/merchants` / `POST /api/staff-users` / `POST /api/admin/staff-users/{id}/pin` | `403 FORBIDDEN` | OK (controle positivo) |
| TC-AUTHZ-02 | Elevar papel via payload (mass assignment) | Não há endpoint de auto-update; tentar criar staff como CASHIER | `403` (rota é ADMIN) | OK |
| TC-AUTHZ-03 | Endpoint privado sem token | `POST /api/stamp` / `/redeem` / `/admin/**` sem `Authorization` | `401/403` | OK |
| TC-AUTHZ-04 | Papel vem do token, não do corpo | Inspecionar que `role` não é aceito em DTO de login/cliente | Sem campo `role`/`isAdmin` manipulável | OK |

### 5.2. BOLA no lado do cliente (sem autenticação) — esperado FALHAR até corrigir SEC-001/002/004

| ID | Cenário | Passos | Resultado atual | Resultado esperado (pós-fix) | Achado |
|---|---|---|---|---|---|
| TC-AUTHZ-05 | Ler cartões de qualquer cliente | `GET /api/cards/customer/{id}` enumerando `id` 1..N **sem token** | `200` com dados de qualquer cliente | `401/403`; só o dono autenticado | SEC-001 |
| TC-AUTHZ-06 | Emitir QR de cartão alheio | `GET /api/qr/{cardId}` e `GET /api/cards/{cardId}/redeem-qr` sem token | `200` com token HMAC válido | `401/403`; só o dono | SEC-002/004 |
| TC-AUTHZ-07 | Criar/obter cartão arbitrário | `POST /api/cards` com `programId`/`customerId` quaisquer, sem token | `200/201` | `401/403` | SEC-004 |
| TC-AUTHZ-08 | Sobrescrever dados de cliente por e-mail | `POST /api/customers/login-or-register` com e-mail de vítima + telefone novo | Sobrescreve e retorna PII | Negado/escopado | SEC-001 |

### 5.3. Escalação horizontal entre lojistas — esperado FALHAR até corrigir SEC-020

> Pré-condição: dois merchants A e B; um **ADMIN do merchant A** autenticado
> (`tokenAdminA` com `merchantId=A`). Recursos de B: `programIdB`, `staffIdB`.

| ID | Cenário | Passos | Resultado atual | Resultado esperado (pós-fix) | Achado |
|---|---|---|---|---|---|
| TC-AUTHZ-09 | Criar localização sob outro lojista | `POST /api/merchants/{B}/locations` com `tokenAdminA` | `201` (cria em B) | `403` | SEC-020 |
| TC-AUTHZ-10 | Criar promoção sob outro lojista | `POST /api/merchants/{B}/programs` com `tokenAdminA` | `201` | `403` | SEC-020 |
| TC-AUTHZ-11 | Editar/desativar promoção de outro lojista | `PUT /api/merchants/{B}/programs/{programIdB}` `{ "active": false }` com `tokenAdminA` | `200` (altera B) | `403` | SEC-020 |
| TC-AUTHZ-12 | Criar staff sob outro lojista | `POST /api/staff-users` `{ "merchantId": B, "role":"ADMIN", … }` com `tokenAdminA` | `201` (vincula a B) | `403` | SEC-020 |
| TC-AUTHZ-13 | Redefinir PIN do caixa de outro lojista | `POST /api/admin/staff-users/{staffIdB}/pin` com `tokenAdminA` | `200` (altera PIN de B) | `403` | SEC-020, SEC-017 |
| TC-AUTHZ-14 | Selo/resgate cross-tenant (controle existente) | `POST /api/stamp` com cartão de B usando token de A | `400` "Card does not belong to staff merchant" | mantém negado | OK (positivo) |

## 6. Testes automatizados propostos — Autorização (não aplicados)

> Snippets `@SpringBootTest` + `MockMvc`. Propostas (Fase 13); implementar sob
> autorização. Os de §5.2/§5.3 servem como **testes de regressão** que passam a
> verde **após** a correção de SEC-001/004/020.

### TC-AUTHZ-01 — CASHIER não acessa endpoint ADMIN

```java
@Test
void cashier_cannotCreateMerchant() throws Exception {
    mvc.perform(post("/api/merchants")
            .header("Authorization", "Bearer " + cashierToken)
            .contentType(APPLICATION_JSON).content("{\"name\":\"X\"}"))
       .andExpect(status().isForbidden());
}
```

### TC-AUTHZ-05 — Cartões de outro cliente exigem dono (regressão de SEC-001)

```java
@Test
void listCards_ofAnotherCustomer_isDenied() throws Exception {
    // Atual: 200 (falha). Após o fix: 401/403 sem token, ou 403 com token de outro cliente.
    mvc.perform(get("/api/cards/customer/{id}", otherCustomerId))
       .andExpect(status().is4xxClientError());
}
```

### TC-AUTHZ-11 — ADMIN de A não edita promoção de B (regressão de SEC-020)

```java
@Test
void adminOfA_cannotUpdateProgramOfB() throws Exception {
    mvc.perform(put("/api/merchants/{b}/programs/{p}", merchantB, programOfB)
            .header("Authorization", "Bearer " + tokenAdminA)
            .contentType(APPLICATION_JSON).content("{\"active\":false}"))
       .andExpect(status().isForbidden()); // hoje retorna 200 → vira guard após o fix
}
```

### TC-AUTHZ-13 — ADMIN de A não redefine PIN de staff de B (regressão de SEC-020/017)

```java
@Test
void adminOfA_cannotSetPinOfStaffOfB() throws Exception {
    mvc.perform(post("/api/admin/staff-users/{id}/pin", staffOfB)
            .header("Authorization", "Bearer " + tokenAdminA)
            .contentType(APPLICATION_JSON).content("{\"pin\":\"1234\"}"))
       .andExpect(status().isForbidden());
}
```

### TC-AUTHZ-14 — Selo cross-tenant permanece negado (não-regressão do controle existente)

```java
@Test
void stamp_cardOfAnotherMerchant_isRejected() throws Exception {
    // tokenAdminA + cardId pertencente ao merchant B
    mvc.perform(post("/api/stamp")
            .header("Authorization", "Bearer " + tokenAdminA)
            .header("Idempotency-Key", "k-cross")
            .contentType(APPLICATION_JSON)
            .content(stampPayloadForCard(cardOfB)))
       .andExpect(status().isBadRequest()); // "Card does not belong to staff merchant"
}
```

## 7. Cobertura e prioridade — Autorização

| Prioridade | Testes | Objetivo |
|---|---|---|
| Alta | TC-AUTHZ-09..13 | Isolamento entre lojistas (SEC-020) — maior risco para SaaS |
| Alta | TC-AUTHZ-05..08 | BOLA do cliente sem auth (SEC-001/002/004) |
| Alta | TC-AUTHZ-01/03/14 | Não-regressão dos controles que já funcionam (vertical + selo cross-tenant) |
| Média | TC-AUTHZ-02/04 | Garantir ausência de mass assignment de papel |

## 8. Critério de aceite da Fase 6

- [x] Endpoints com ID revisados (cards/qr, programs, locations, staff, pin).
- [x] Regras admin revisadas (papel global vs. escopo de merchant — SEC-020).
- [x] Campos sensíveis protegidos contra mass assignment (sem auto-update de `role`; OK).
- [x] Testes de usuário A versus usuário B documentados (`TC-AUTHZ-05..14`).

> Nenhuma alteração de código/autorização foi feita nesta fase — apenas análise,
> evidências no `SECURITY_REPORT.md` e testes propostos. As correções de SEC-020
> (escopo de merchant) têm impacto funcional e serão **propostas antes** de
> implementar.

---

# Fase 7 — Validação de entrada, ataques comuns e CSRF

> Famílias `TC-INPUT-xx` (validação/injeção) e `TC-CSRF-xx`. Achados:
> SEC-022 (validação), SEC-023 (conteúdo armazenado/XSS), SEC-007 (erro/500),
> SEC-009 (CSRF). Rodar apenas em ambiente autorizado.

## 9. Testes manuais — Validação, injeção e erro

| ID | Cenário | Passos | Resultado atual | Resultado esperado (pós-fix) | Achado |
|---|---|---|---|---|---|
| TC-INPUT-01 | SQLi em parâmetro de rota | `GET /api/cards/customer/1 OR 1=1` e `'; DROP TABLE…` | `400`/`404` (tipo `Long` rejeita) — **sem SQLi** | mantém | OK (positivo) |
| TC-INPUT-02 | SQLi em corpo (e-mail) | `login-or-register` com `email = "' OR '1'='1"` | Tratado como string literal (JPQL param) | mantém | OK (positivo) |
| TC-INPUT-03 | E-mail inválido aceito | `login-or-register` `email="naoeemail"` | Aceito e persistido | `400` (`@Email`) | SEC-022 |
| TC-INPUT-04 | Over-length → 500 | `createCustomer` com `email` de 500 chars | `500` (DataIntegrityViolation não tratada) | `400`/`409` | SEC-022, SEC-007 |
| TC-INPUT-05 | E-mail duplicado | Criar 2 clientes com mesmo e-mail | `500` (UNIQUE) | `409` genérico | SEC-007 |
| TC-INPUT-06 | Mensagem de erro vaza interno | `POST /api/stamp` com `cardId` inexistente (autenticado) | `400 "Card not found: 123"` | `400` genérico (detalhe só em log) | SEC-007 |
| TC-INPUT-07 | Payload de stamp malformado | `StampRequest.payload` sem `sig`/`nonce` | Erro em runtime (HMAC/UUID) → `400` | `400` previsível (validação cascateada) | SEC-022 |
| TC-INPUT-08 | `imageUrl` com esquema perigoso | `createProgram` `imageUrl="javascript:alert(1)"` | Aceito e devolvido | `400` (só `http(s)`) | SEC-023 |
| TC-INPUT-09 | Stored XSS em texto | `createProgram` `name="<script>alert(1)</script>"` | Persistido sem sanitização | Persistido + **frontend faz escaping** | SEC-023 |
| TC-INPUT-10 | Path traversal / arquivos | Procurar endpoint de upload/download | Inexistente | mantém | OK (positivo) |
| TC-INPUT-11 | Command injection | Procurar uso de shell com input | Inexistente | mantém | OK (positivo) |
| TC-INPUT-12 | SSRF | Tentar host arbitrário em integração | Hosts fixos; não explorável | mantém | OK (positivo) |

## 10. Testes manuais — CSRF (SEC-009)

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-CSRF-01 | Sem cookie de sessão | Inspecionar resposta de login/headers | Nenhum `Set-Cookie` de sessão; auth só por header | OK |
| TC-CSRF-02 | Requisição cross-site sem header | Página externa faz `POST /api/stamp` (sem `Authorization`) | `401/403` (navegador não anexa o Bearer) | OK |
| TC-CSRF-03 | CORS não permite credenciais | Pré-flight de origem não listada | Bloqueado; `allowCredentials=false` | OK |
| TC-CSRF-04 | Guard de regressão | Garantir que não foi introduzido cookie de sessão | Se surgir cookie → exigir CSRF + SameSite | SEC-009 |

## 11. Testes automatizados propostos — Validação e CSRF (não aplicados)

### TC-INPUT-03 — E-mail inválido é rejeitado (regressão de SEC-022)

```java
@Test
void register_withInvalidEmail_isRejected() throws Exception {
    mvc.perform(post("/api/customers/login-or-register")
            .contentType(APPLICATION_JSON)
            .content("{\"email\":\"naoeemail\",\"name\":\"x\"}"))
       .andExpect(status().isBadRequest()); // hoje passa → guard após adicionar @Email/@Valid
}
```

### TC-INPUT-04 — Entrada acima do limite não retorna 500 (SEC-022/007)

```java
@Test
void createCustomer_overlongEmail_isClientError_not500() throws Exception {
    String longEmail = "a".repeat(500) + "@x.com";
    mvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + longEmail + "\"}"))
       .andExpect(status().is4xxClientError()); // hoje 500
}
```

### TC-INPUT-02 — Payload tipo-SQLi é tratado como literal (não-regressão)

```java
@Test
void login_sqliLikeEmail_isHandledAsLiteral() throws Exception {
    // Não deve causar erro de SQL nem retornar dados indevidos
    mvc.perform(post("/api/auth/login")
            .contentType(APPLICATION_JSON)
            .content("{\"email\":\"' OR '1'='1\",\"password\":\"x\"}"))
       .andExpect(status().isBadRequest()); // credenciais inválidas, sem SQLi
}
```

### TC-INPUT-08 — `imageUrl` com esquema perigoso é rejeitado (SEC-023)

```java
@Test
void createProgram_withJavascriptImageUrl_isRejected() throws Exception {
    mvc.perform(post("/api/merchants/{m}/programs", merchantId)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(APPLICATION_JSON)
            .content("{\"name\":\"P\",\"imageUrl\":\"javascript:alert(1)\"}"))
       .andExpect(status().isBadRequest()); // após validação de esquema http(s)
}
```

### TC-CSRF-02 — Requisição sem header de auth é negada

```java
@Test
void stateChange_withoutAuthHeader_isDenied() throws Exception {
    // Simula CSRF: sem Authorization (navegador não anexaria o Bearer)
    mvc.perform(post("/api/stamp")
            .header("Idempotency-Key", "k")
            .contentType(APPLICATION_JSON).content("{}"))
       .andExpect(status().is4xxClientError());
}
```

## 12. Cobertura e prioridade — Fase 7

| Prioridade | Testes | Objetivo |
|---|---|---|
| Alta | TC-INPUT-01/02/10/11/12, TC-CSRF-02/03 | Não-regressão das defesas existentes (SQLi/cmd/upload/SSRF/CSRF) |
| Média | TC-INPUT-03/04/05/07 | Validação de entrada e erro 500 (SEC-022/007) |
| Média | TC-INPUT-08/09 | Esquema de `imageUrl` e stored XSS (SEC-023) |
| Baixa | TC-INPUT-06, TC-CSRF-01/04 | Mensagens de erro e guards de configuração |

## 13. Critério de aceite da Fase 7

- [x] Queries inseguras identificadas (nenhuma — JPQL/derived parametrizado).
- [x] Inputs críticos validados (lacunas em SEC-022).
- [x] Uploads revisados (inexistentes — sem superfície).
- [x] Tratamento de erro revisado (SEC-007 ampliado: 500 em DataIntegrityViolation).
- [x] Risco de CSRF avaliado e mitigado (auth por header → `csrf(disable)` correto; SEC-009).

> Nenhuma alteração de código foi feita nesta fase.

---

# Fase 12 — Lógica de negócio (foco em impacto financeiro)

> Família `TC-BIZ-xx`. Fluxo de valor = **acúmulo de selos → resgate de
> recompensa** (recompensa grátis = risco financeiro). Achados: SEC-034 (resgate
> sem PIN/QR), SEC-035 (parâmetros de programa). Pré-condição: caixa autenticado.

## 14. Testes manuais — Lógica de negócio

| ID | Cenário | Passos | Resultado esperado | Achado |
|---|---|---|---|---|
| TC-BIZ-01 | Não ultrapassar o limiar | Carimbar além de `needed` (concorrente/sequencial) | `stamps_count` nunca passa de `needed`; depois → `READY_TO_REDEEM` | OK (positivo) |
| TC-BIZ-02 | Race de selo no mesmo cartão | 2 requisições simultâneas de selo (tokens válidos) | Uma vence; outra `409` (lock otimista) ou bloqueio por rate/nonce | OK (positivo) |
| TC-BIZ-03 | Reuso do QR de selo | Reenviar o mesmo token CUSTOMER_QR | `409` "Replay detected" (nonce) | OK (positivo) |
| TC-BIZ-04 | Manipulação de payload | Alterar `cardId`/`exp` no payload mantendo `sig` | `400` "Invalid signature" (HMAC) | OK (positivo) |
| TC-BIZ-05 | **Resgate sem PIN (omitindo `locationId`)** | `POST /api/redeem` `{ "cardId": <ready> }` sem `locationId` e sem `X-Cashier-Pin` | **Hoje: `200` (resgata sem PIN)** → esperado pós-fix: `400/403` exigindo PIN/QR | SEC-034 |
| TC-BIZ-06 | **Resgate sem QR do cliente** | `POST /api/redeem` só com `cardId` (sem `redeemQr`) | Hoje: `200` → esperado pós-fix: exigir token REDEEM_QR | SEC-034 |
| TC-BIZ-07 | Double-redeem (race) | 2 resgates simultâneos do mesmo cartão pronto | Um emite reward; outro `409`/`CARD_NOT_READY` (lock + status) | OK (positivo) |
| TC-BIZ-08 | Resgate de cartão não-pronto | `redeem` de cartão `ACTIVE` com poucos selos | `409 CARD_NOT_READY_TO_REDEEM` | OK (positivo) |
| TC-BIZ-09 | Reuso do REDEEM_QR | Reenviar o mesmo token REDEEM_QR | `409` "Replay detected" | OK (positivo) |
| TC-BIZ-10 | Programa com `ruleTotalStamps<=0` | Criar programa `ruleTotalStamps=0` e tentar carimbar | Esperado: `400` na criação (`@Min(1)`) | SEC-035 |

## 15. Testes automatizados propostos — Lógica de negócio (maior impacto financeiro)

### TC-BIZ-05 — Resgate exige PIN mesmo sem `locationId` (regressão de SEC-034)

```java
@Test
void redeem_withoutLocationId_stillRequiresPinOrQr() throws Exception {
    // cartão READY_TO_REDEEM do merchant do caixa; sem locationId e sem PIN
    mvc.perform(post("/api/redeem")
            .header("Authorization", "Bearer " + cashierToken)
            .contentType(APPLICATION_JSON)
            .content("{\"cardId\":" + readyCardId + "}"))
       .andExpect(status().is4xxClientError()); // hoje 200 → guard após o fix
}
```

### TC-BIZ-01/02 — Selo nunca ultrapassa o limiar, mesmo concorrente (não-regressão)

```java
@Test
void concurrentStamps_doNotExceedNeeded() throws Exception {
    int threads = 8;
    var pool = Executors.newFixedThreadPool(threads);
    var latch = new CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
        pool.submit(() -> { try { stampOnce(cardId); } finally { latch.countDown(); } });
    }
    latch.await(5, TimeUnit.SECONDS);
    Card c = cardRepo.findById(cardId).orElseThrow();
    assertThat(c.getStampsCount()).isLessThanOrEqualTo(neededForCard);
}
```

### TC-BIZ-04 — Payload adulterado é rejeitado pelo HMAC (não-regressão)

```java
@Test
void tamperedStampPayload_isRejected() throws Exception {
    // troca cardId mantendo sig original → assinatura inválida
    mvc.perform(post("/api/stamp")
            .header("Authorization", "Bearer " + cashierToken)
            .header("Idempotency-Key", "k-tamper")
            .contentType(APPLICATION_JSON)
            .content(tamperedCustomerQrPayload(otherCardId, originalSig)))
       .andExpect(status().isBadRequest()); // "Invalid signature"
}
```

## 16. Cobertura e prioridade — Fase 12

| Prioridade | Testes | Objetivo |
|---|---|---|
| Alta | TC-BIZ-05/06 | Fechar o bypass de PIN/QR no resgate (SEC-034) — maior impacto financeiro |
| Alta | TC-BIZ-01/02/07 | Não-regressão das defesas anti-race/limiar |
| Média | TC-BIZ-03/04/09 | Anti-replay e integridade de payload |
| Baixa | TC-BIZ-10 | Validação de regra de programa (SEC-035) |

## 17. Critério de aceite da Fase 12

- [x] Fluxos críticos de negócio mapeados (selo → resgate; sem pagamento/cupom/transferência/convite no domínio).
- [x] Cenários de abuso documentados (SEC-034 resgate sem PIN/QR; SEC-035 parâmetros).
- [x] Validações de limite/estado verificadas no backend (limiar/`@Version`/status/anti-replay — robustas; PIN/QR contornáveis).
- [x] Testes para os cenários de maior impacto financeiro propostos (TC-BIZ-05/06).

> Nenhuma alteração de código foi feita nesta fase.

---

# Fase 13 — Testes automatizados de segurança

> Consolida a estratégia de automação. **Implementados** os testes seguros
> (unitários do core de cripto, que passam contra o código atual); **propostos**
> (acima, Fases 5–12) os que dependem de correções ou de contexto/DB.

## 18. O que foi IMPLEMENTADO neste repositório (✅ verde)

Critério: seguro = não depende de achado em aberto, não precisa de banco/contexto
Spring, e **passa** contra o código atual (guarda de regressão dos controles que já
funcionam). Rodam com `./mvnw test` (sem Postgres).

| Arquivo | Cobre | Achados/Controle |
|---|---|---|
| `src/test/java/com/app/carimbai/services/JwtServiceTest.java` | Emissão/leitura de claims; **expiração** (fail-closed); **assinatura adulterada**; **`alg=none`** | Auth/JWT (Fase 5) |
| `src/test/java/com/app/carimbai/services/StampTokenServiceTest.java` | Token válido; **manipulação de `cardId`** (HMAC); **anti-replay** (nonce); **type confusion** CUSTOMER_QR≠REDEEM_QR; **expiração** | Lógica de negócio/integridade (Fases 7/12) |

Resultado da execução: **`Tests run: 9, Failures: 0, Errors: 0` — BUILD SUCCESS**.

> Achado lateral confirmado pelo teste: `JwtService.isExpired()` **lança**
> `ExpiredJwtException` para token expirado (em vez de retornar `true`) — é
> *fail-closed* (o filtro captura e trata como não-autenticado), mas o nome do
> método é enganoso. Registrado como nota em SEC-012.

## 19. O que ficou PROPOSTO (não implementado) e por quê

Não foram adicionados como testes ativos para **não deixar o build vermelho** nem
exigir infraestrutura ausente:

- **Tests de "comportamento corrigido"** (BOLA→403, resgate exige PIN, e-mail
  inválido→400, anti-enumeração…) **falhariam hoje** porque a vulnerabilidade
  existe. Viram verdes **após** o `FIX` correspondente — usar como *regression
  guards* (TC-AUTHZ-05/09/11/13, TC-INPUT-03/04/08, TC-BIZ-05/06, TC-AUTH-02/04).
- **Tests de integração `@SpringBootTest`+`MockMvc`** precisam de **Postgres** e
  dos segredos; o projeto **não tem H2/Testcontainers**. Para habilitá-los:
  adicionar **Testcontainers (PostgreSQL)** ou `spring-boot-testcontainers` ao
  `pom.xml` (escopo `test`) e um `@ServiceConnection` — então os snippets
  `MockMvc` das Fases 5–12 tornam-se executáveis.

## 20. Priorização (maior risco primeiro)

| Prioridade | Conjunto | Quando habilitar |
|---|---|---|
| **P0 (já verde)** | `JwtServiceTest`, `StampTokenServiceTest` | Implementado — manter no CI |
| P1 | TC-AUTHZ-05..13 (BOLA/cross-tenant), TC-BIZ-05/06 (resgate) | Junto/depois de FIX-02/03/23 |
| P1 | TC-AUTH-06/07/10/12 (auth/JWT via MockMvc) | Ao adicionar Testcontainers |
| P2 | TC-INPUT-03/04/08 (validação), TC-AUTH-02 (anti-enumeração) | Junto de FIX-06/07/08 |
| P2 | TC-CSRF-02/03, exposição de dados (senha nunca no JSON) | Ao adicionar Testcontainers |

## 21. CI

Habilitar `./mvnw test` no GitHub Actions (hoje o deploy usa `-DskipTests`).
Sugestão: um workflow `ci.yml` em PRs rodando `./mvnw test` (com Testcontainers
quando adicionado) — separado do `deploy.yml`.

## 22. Onde um scan DAST (ex.: OWASP ZAP baseline) agregaria

Complementa a revisão estática/unitária validando a **instância em execução** —
**somente em staging/local autorizado, nunca produção** (RoE, seção 2.1):

- **Headers de segurança** (SEC-029): HSTS, CSP, Referrer-Policy, `X-*` — o ZAP
  baseline reporta ausências diretamente na resposta.
- **Cookies/CSRF** (SEC-009): confirma que não há cookie de sessão e flags.
- **Swagger/`/api-docs` expostos** (SEC-005): o spider acha a superfície pública.
- **Mensagens de erro/500** (SEC-007/022): detecta vazamento de stack trace e 500.
- **TLS/redirect** (SEC-029): valida HTTPS e *downgrade*.
- **BOLA/autenticação** (SEC-001/020): ZAP **baseline** (passivo) **não** prova
  IDOR — isso exige os testes de integração A-vs-B (TC-AUTHZ) ou ZAP *active scan*
  com autenticação configurada, em ambiente isolado e autorizado.

Comando (não executar contra produção):

```bash
# passivo, não intrusivo, contra a instância de staging autorizada
zap-baseline.py -t https://staging.carimbai.exemplo -r zap-report.html
```

## 23. Critério de aceite da Fase 13

- [x] Testes de autenticação criados (JWT: expiração/tamper/alg=none) — **implementados e verdes**.
- [x] Testes de autorização criados ou propostos (TC-AUTHZ — propostos; viram guard pós-fix).
- [x] Testes de exposição de dados criados ou propostos (senha nunca em resposta — proposto; integridade de token implementada).
- [x] Testes podem rodar no CI (`./mvnw test` verde; CI sugerido em §21).
- [x] DAST indicado (ZAP baseline em staging — §22), sem executar contra produção.

> **Alteração de código nesta fase:** apenas **adição de testes** em `src/test`
> (sem tocar em `src/main`). Verificado: `./mvnw test` → BUILD SUCCESS (9/9).
