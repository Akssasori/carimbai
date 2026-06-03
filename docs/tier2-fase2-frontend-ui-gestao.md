# Tier 2 — Fase 2 (frontend): UI de gestão

## Contexto

Esta entrega fecha a Fase 2 do Tier 2: UI completa de gestão para o ADMIN gerenciar programas, equipe (staff) e lojas (locations) sem precisar de Swagger ou curl. Consome os endpoints implementados na entrega de backend ([`tier2-fase2-backend-ui-gestao.md`](./tier2-fase2-backend-ui-gestao.md)).

**O que entrou:**
- 3 telas de gestão (`ProgramsAdmin`, `StaffAdmin`, `LocationsAdmin`).
- 5 componentes compartilhados reusáveis (`Modal`, `Toggle`, `AdminCard`, `SideNav`, `AdminLayout`).
- `AdminRoute` guard que valida sessão ADMIN no router.
- `useStaffSession` hook compartilhado.
- Refator do `StaffScreen` para reusar o `<SideNav>` extraído.

**Fora de escopo (mantido para Tier 3):**
- Edição de email/senha de staff.
- Endpoint de reset de senha do staff via fluxo de "esqueci a senha".
- Validação avançada (formato de email server-side via Bean Validation, força de senha).
- Paginação/busca nas listas (assume volume MVP < 100 itens por categoria).
- Audit log.

## 1. Arquitetura

### 1.1 Hierarquia de componentes

```
App.tsx
├── /staff/dashboard           → StaffScreen ........... usa <SideNav>
├── /staff/admin/programs      → AdminRoute → ProgramsAdmin
├── /staff/admin/staff         → AdminRoute → StaffAdmin
└── /staff/admin/locations     → AdminRoute → LocationsAdmin
                                              │
                                              ↓
                              AdminLayout ── SideNav (compartilhado)
                                  │
                                  ↓
                              AdminCard, Modal, Toggle (compartilhados)
```

### 1.2 Pasta `src/components/admin/`

| Arquivo | Papel |
|---|---|
| `SideNav.tsx` / `SideNav.css` | Sidebar com NavLinks para Dashboard, Programas, Equipe, Lojas. Items admin escondem-se quando `role !== 'ADMIN'`. Compartilhado entre StaffScreen e as 3 telas admin. |
| `AdminLayout.tsx` / `AdminLayout.css` | Wrapper que renderiza `<SideNav>` + header (título, subtítulo, `headerExtra` opcional para filtros/botões) + `<section>` com children. Também define classes utilitárias `.admin-btn-*`, `.admin-field`, `.admin-empty`, `.admin-error-banner`. |
| `Modal.tsx` / `Modal.css` | Modal genérico: backdrop, escape-to-close, click-outside-to-close. Aceita `title`, `children` (body), `footer`. |
| `Toggle.tsx` / `Toggle.css` | Switch tipo on/off com label e descrição opcional. Usado para `active` e flags JSONB. |
| `AdminCard.tsx` / `AdminCard.css` | Linha visual de lista: título + badges + subtitle + meta + actions. Modo `dimmed` para itens desativados. |
| `ProgramsAdmin.tsx` | Tela de gestão de programas. |
| `StaffAdmin.tsx` | Tela de gestão de equipe. |
| `LocationsAdmin.tsx` | Tela de gestão de lojas. |

### 1.3 Hook `useStaffSession`

`src/hooks/useStaffSession.ts` — lê `localStorage.carimbai_staff_session`, expõe `{ session, logout }`. Se não houver sessão, navega para `/staff` automaticamente. Centraliza o pattern de leitura que antes vivia inline em StaffScreen.

### 1.4 Roteamento e role guard

Em `App.tsx`:
- `AdminRoute` é um wrapper de rota que:
  1. Sem sessão → `<Navigate to="/staff" />`.
  2. Sessão inválida (JSON malformado) → `<Navigate to="/staff" />`.
  3. Sessão com `role !== 'ADMIN'` → `<Navigate to="/staff/dashboard" />`.
  4. Sessão ADMIN válida → renderiza children.
- As 3 rotas admin são envolvidas com `<AdminRoute>`.

Para CASHIER: tentar acessar `/staff/admin/*` direto na URL redireciona pro dashboard. O sidebar também esconde os itens admin (via prop `role`).

## 2. Tela: ProgramsAdmin (`/staff/admin/programs`)

### Funcionalidades
- **Lista** todos os programs do merchant (ativos + inativos), via `GET /api/merchants/{mId}/admin/programs`.
- **Filtro** "Mostrar inativos" (default ligado, para o admin ver tudo).
- **Criar** novo programa → modal com campos: nome*, recompensa*, carimbos necessários*, descrição, categoria, URL da imagem.
- **Editar** programa → mesmo modal pré-preenchido.
- **Toggle ativo/inativo** inline via `PUT /api/merchants/{mId}/programs/{pId}` com `{active: !current}`.

### Validações no front
- Nome obrigatório.
- `ruleTotalStamps` deve ser número positivo.

### Renderização
- `AdminCard` por programa.
- Badges: status (`Ativo`/`Inativo`), categoria.
- Meta: 🎯 N carimbos, 🎁 nome da recompensa.
- Modo `dimmed` em programas inativos.

## 3. Tela: LocationsAdmin (`/staff/admin/locations`)

### Funcionalidades
- **Lista** todas as locations do merchant, via `GET /api/merchants/{mId}/locations`.
- **Criar** nova location → modal com campos: nome*, endereço.
  - A criação usa o endpoint legacy `POST /api/merchants/{mId}/locations` que não aceita flags. O admin cria e depois edita para configurar flags.
- **Editar** location → modal completo: nome, endereço, toggle "Loja ativa", e os 3 toggles de flags (`requirePinOnRedeem`, `enableScanA`, `enableScanB`).
- **Toggle ativo/inativo** inline.

### Detalhe importante das flags JSONB
- Quando o admin clica "Salvar" na edição, o front envia **sempre os 3 booleans juntos** dentro de `flags`. O backend sobrescreve o JSONB inteiro (sem merge campo-a-campo). Isso é por design: garante que toggles dos 3 flags sejam atômicos e sem perda silenciosa.
- Toggle inline de `active` na lista chama `PUT` com `{active: !current}` apenas — `flags` é `undefined` → backend não toca em `flags`.

### Renderização
- Badges por flag ativada ("PIN no resgate", "Scan A", "Scan B").
- Modo `dimmed` em locations inativas.

## 4. Tela: StaffAdmin (`/staff/admin/staff`)

### Funcionalidades
- **Lista** staff do merchant ativo (via `GET /api/merchants/{mId}/staff-users`).
- **Criar** novo staff → modal com campos: email*, senha inicial*, role (CASHIER/ADMIN).
  - Usa `POST /api/staff-users` com `merchantId` da sessão.
  - Senha em texto puro nesta tela. Aviso explícito sobre canal seguro.
- **Alterar role inline** via select (CASHIER↔ADMIN) → `PATCH /api/merchants/{mId}/staff-users/{staffId}`.
- **Toggle ativo/inativo** inline.
- **Definir/resetar PIN** → modal pequeno com PIN + confirmação → `POST /api/admin/staff-users/{id}/pin`.

### Guards de UX (auto-lockout)
- A própria linha do admin logado:
  - Tem badge "Você".
  - `select` de role está **desabilitado** (não pode se rebaixar).
  - Botão "Desativar" está **desabilitado** (não pode se desativar).
- Backend tem os mesmos guards: ainda que alguém burle o front (DevTools), o `PATCH` retornará 403 com mensagem clara.

### Validações no front
- Email + senha obrigatórios.
- Senha mínimo 6 caracteres.
- PIN entre 4-10 caracteres; confirmação deve conferir.

## 5. Sessão e logout

`useStaffSession` é o ponto único de leitura da sessão do staff. As 3 telas admin chamam `const { session, logout } = useStaffSession()` no topo. Se a sessão sumir (token revogado por outro lugar, localStorage limpo, etc.), o hook navega para `/staff` automaticamente.

Logout invalida apenas o localStorage (não há endpoint server-side para invalidar JWT — refresh token virá no Tier 3).

## 6. Tratamento de erro

- **401**: handler centralizado em `api.ts` (`handleUnauthorized`) já implementado no Tier 2 quick wins. Limpa localStorage e redireciona para `/staff`. Funciona aqui automaticamente em todas as chamadas autenticadas das telas admin.
- **403**: NÃO força logout (continua sendo violação de ownership legítima). A tela mostra a mensagem do erro num banner vermelho. Ex: tentar PATCH `active=false` em si mesmo → o backend devolve 403 com `"You cannot deactivate yourself in this merchant"` → banner mostra esse texto.
- **Outros erros**: a mensagem do servidor é exibida no banner ou no formulário (modal).

## 7. Refator do StaffScreen

A markup inline do sidebar (linhas ~445-498 do `StaffScreen.tsx` antigo) foi removida e substituída por:

```tsx
<SideNav role={session.role} onLogout={handleLogout} />
```

Comportamento idêntico para CASHIER (vê só Dashboard + Sair). Para ADMIN, agora vê Dashboard + Programas + Equipe + Lojas + Sair, com NavLinks reais.

## 8. CSS — convenção e organização

- Cada componente admin tem seu `.css` colocalizado.
- Classes utilitárias globais (`.admin-btn-primary`, `.admin-field`, `.admin-empty`, `.admin-error-banner`, `.admin-filter-toggle`) vivem em `AdminLayout.css` — qualquer tela que use o `AdminLayout` herda.
- Estilos do sidebar existem em **dois lugares** intencionalmente: `SideNav.css` (para as telas admin) e `StaffScreen.css` (legado — regras idênticas, sem conflito). Não removi de `StaffScreen.css` para minimizar diff e risco de quebra visual.

## 9. Arquivos novos / modificados

### Novos (frontend) — 13
- `src/hooks/useStaffSession.ts`
- `src/components/admin/SideNav.tsx`
- `src/components/admin/SideNav.css`
- `src/components/admin/AdminLayout.tsx`
- `src/components/admin/AdminLayout.css`
- `src/components/admin/Modal.tsx`
- `src/components/admin/Modal.css`
- `src/components/admin/Toggle.tsx`
- `src/components/admin/Toggle.css`
- `src/components/admin/AdminCard.tsx`
- `src/components/admin/AdminCard.css`
- `src/components/admin/ProgramsAdmin.tsx`
- `src/components/admin/StaffAdmin.tsx`
- `src/components/admin/LocationsAdmin.tsx`

### Modificados (frontend) — 4
- `src/App.tsx` — `AdminRoute` + 3 novas rotas.
- `src/types/index.ts` — tipos admin (AdminProgramItem, LocationFlags, LocationItem, StaffItem, etc.).
- `src/services/api.ts` — 10 métodos novos (programs/locations/staff CRUD + setStaffPin).
- `src/components/StaffScreen.tsx` — sidebar inline substituído por `<SideNav>`.

## 10. Verificação end-to-end

### Pré-condições
1. Backend Fase 2 rodando.
2. Migration `V2026.18.05.07.11.00__add_locations_active.sql` aplicada.
3. Existe pelo menos um ADMIN cadastrado (via bootstrap do Tier 2 quick wins ou seeder).

### Cenários

**A. Login como ADMIN → sidebar mostra os itens admin**
1. `npm run dev`, login com admin.
2. Conferir que o sidebar tem 4 ícones acima de Sair: Dashboard, Programas, Equipe, Lojas.
3. Clicar em cada → navega para a tela correspondente.

**B. Login como CASHIER → não vê admin**
1. Logar como cashier.
2. Sidebar tem só Dashboard + Sair.
3. Tentar acessar URL `/staff/admin/programs` direto → redireciona para `/staff/dashboard`.

**C. Criar e desativar um programa**
1. Em `/staff/admin/programs`, clicar "+ Novo programa", preencher, salvar.
2. Aparece na lista com badge "Ativo".
3. Clicar "Desativar" → status muda para "Inativo", linha fica dimmed.
4. Desmarcar "Mostrar inativos" → programa some da lista.
5. Editar programa inativo → toggle ativo via modal.

**D. Criar location e configurar flags**
1. Em `/staff/admin/locations`, criar nova loja (só nome + endereço).
2. Clicar "Editar" → modal mostra os 3 toggles de flags.
3. Marcar `requirePinOnRedeem` → salvar.
4. Lista mostra badge "PIN no resgate" naquela location.

**E. Promover CASHIER → ADMIN**
1. Em `/staff/admin/staff`, mudar select de role do cashier para ADMIN.
2. Confirmar via `GET /api/merchants/{mId}/staff-users` que `role: ADMIN`.
3. Aquele staff faz logout/login → sidebar mostra itens admin.

**F. Auto-lockout (verificar guard)**
1. Em `/staff/admin/staff`, tentar mudar próprio role: select desabilitado.
2. Tentar via DevTools: chamar `apiService.updateStaffInMerchant(..., { active: false }, ...)` com o próprio staffId.
3. Backend responde 403; banner mostra mensagem.

**G. Definir PIN**
1. Clicar "PIN" em uma linha → modal.
2. Digitar 4 dígitos + confirmar.
3. Submit → fecha modal sem erro.
4. Cashier consegue resgatar prêmio em loja com `requirePinOnRedeem` usando o PIN.

**H. Sessão expirada**
1. Limpar manualmente o token em `localStorage.carimbai_staff_session` (deixar inválido).
2. Próxima ação em qualquer tela admin → backend 401 → `handleUnauthorized` limpa session e redireciona para `/staff`.

## 11. Riscos e gaps conhecidos

| Risco/gap | Impacto | Mitigação atual / próximo passo |
|---|---|---|
| Bundle aumentou ~23 KB JS / 6.5 KB CSS gzip | Negligível em desktop, perceptível em mobile lento | Code-splitting por rota (Vite lazy import) — opcional, Tier 3 |
| Senha do staff em texto puro na tela | Aceitável para admin → admin onboarding | Reset por email (Tier 3) |
| Sem busca/paginação nas listas | Funciona até ~100 itens; depois fica desconfortável | Adicionar quando aparecer demanda real |
| Edição não permite trocar email do staff | Bloqueador real se admin precisar corrigir email digitado errado | Adicionar PATCH de email no Tier 3 |
| Listas não invalidam cache em outras abas (sem websocket) | Admin A muda algo, admin B só vê depois de F5 | Aceitável para MVP — refetch on mount basta |

## 12. Próximos passos sugeridos

1. **Validar localmente** os 8 cenários da seção 10.
2. **Tier 3** prioridades sugeridas (em ordem):
   - Reset de senha do staff (endpoint + fluxo "esqueci a senha" + SMTP).
   - Edição de email do staff (com checagem de duplicado).
   - Rate limit no `POST /api/auth/login` (Bucket4j).
   - Refresh token (rotativo, com tabela `refresh_tokens` e endpoint `/api/auth/refresh`).
   - Push notifications backend (se for prioridade de produto).
   - Audit log das ações admin (quem alterou o quê e quando).

---

*Documento gerado após implementação das Fases D–H do plano Tier 2 Fase 2 (frontend). Referência cruzada: [`tier1-correcoes-seguranca.md`](./tier1-correcoes-seguranca.md), [`tier2-quick-wins.md`](./tier2-quick-wins.md), [`tier2-fase2-backend-ui-gestao.md`](./tier2-fase2-backend-ui-gestao.md).*
