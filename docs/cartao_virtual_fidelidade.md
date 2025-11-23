# Mapa do Cartão Fidelidade Virtual

Bora! 👇 Vou te dar um mapa claro pra sair do papel com um cartão fidelidade virtual simples de usar pelo cliente e fácil de operar pelo lojista — sem precisar imprimir nada.

## Objetivo do MVP

- 1 selo por almoço; 10 selos = 1 almoço grátis.
- Cliente sempre tem o cartão no celular.
- Marcação rápida no caixa, com antifraude.
- Lojista tem um painel simples com contagem, validade e resgate.

## Três Caminhos Possíveis (e Quando Usar)

### 1) App Nativo (Android/iOS)

- ✅ Total controle de UX, offline, NFC, câmera, etc.
- ❌ Alto custo inicial (2 apps), fricção pra instalar, manter lojas.

**Use se**: você quiser features pesadas (NFC, Bluetooth, programa complexo, cupons, etc.).

### 2) PWA (Web App Instalável)

- ✅ Um código só (React/Vite), instala como “app” no celular, câmera para QR, push.
- ✅ Mais rápido de lançar e barato.
- ❌ Algumas limitações de push/scan no iOS (mas dá pra contornar).

**Use se**: você quer velocidade, baixo custo e boa experiência.

### 3) Carteira (Google Wallet / Apple Wallet)

- ✅ Fica junto dos cartões do usuário, super visível.
- ✅ Sem precisar baixar app.
- ❌ Você precisa atualizar o “pass” via backend a cada selo; Apple requer conta dev (US$ 99/ano) e setup.

**Use se**: quer zero fricção de instalação e UI minimalista — ótimo complemento à PWA.

## Recomendação Prática
Comece com PWA + integração opcional com Wallet (híbrido). Assim você lança rápido e, quando quiser, adiciona “Adicionar ao Google/Apple Wallet”.

## Como a Marcação Vai Funcionar (Fluxo de Loja)

### Opção A – “Lojista Escaneia o QR do Cliente” (Recomendo)

1. Cliente abre a PWA (ou Wallet pass) e mostra QR do cartão.
2. Caixa abre o Painel do Lojista (tablet/PC/smartphone) e escaneia o QR.
3. Painel chama o backend `/stamp` e o servidor acrescenta o selo, atualiza o pass (se Wallet), e mostra “Selo #7 marcado”.

✅ **Antifraude**: só a loja dá o carimbo.

### Opção B – “Cliente Escaneia um QR Fixo do Caixa”

1. Na PWA, cliente lê um QR na parede.
2. PWA envia token curto e assinado + geolocalização (opcional) para `/stamp`.

✅ **Sem device extra pro lojista.**

⚠️ **Exige tokens curtos e com HMAC + TTL** pra evitar o cliente “guarda e usa em casa”.

**Opcional** (mais tarde): NFC no balcão para Android (tap-to-stamp), ou PIN do lojista como dupla confirmação em resgates.

## Antifraude (Simples e Eficaz no MVP)

- Tokens efêmeros (30s–60s) emitidos pelo servidor e embutidos no QR do cliente (ou QR do balcão).
- Assinatura HMAC com segredo do servidor (evita falsificação).
- **Replay protection**: cada token só vale uma vez.
- **Rate limit** por cliente e por loja.
- Resgate exige confirmação do lojista (PIN de 4–6 dígitos e/ou assinatura do caixa).
- Log de marcações (IP, device, horário) para auditoria.

## Modelo de Dados (Essencial)

- `tenants (id, nome)` – multiloja/multimarcas.
- `locations (tenant_id, nome, endereço)`.
- `programs (tenant_id, nome, regra: 10 selos = 1 prêmio, validade, regras por dia/hora)`.
- `customers (id, phone/email opcional, provider_id se social login)`.
- `cards (program_id, customer_id, saldo_de_selos, status, expiracao)`.
- `stamps (card_id, carimbou_em, location_id, cashier_id opcional, token_id)`.
- `rewards (card_id, emitido_em, resgatado_em, location_id, comprovante)`.
- `staff_users (tenant_id, roles: cashier/admin)`.
- `wallet_passes (card_id, wallet_type, pass_id, estado)`.

## Arquitetura Sugerida (Alinhada ao que Você Já Usa)

### Backend

- Java 21 + Spring Boot 3, PostgreSQL + Flyway, JWT, MapStruct.
- Endpoints: `/auth`, `/cards/{id}`, `/stamp`, `/redeem`, `/wallet/update`.

### Libs Wallet

- **Google Wallet**: criar JWT pass (LoyaltyClass/LoyaltyObject) e update via API.
- **Apple Wallet (PassKit)**: gerar .pkpass assinado e push updates (registrations/webServiceURL).

### Frontend (PWA)

- React + Vite, câmera QR (jsqr/ZXing), Add to Home Screen, push.
- **App Cliente**: ver cartão, QR dinâmico (com token), histórico, botão “Adicionar à Wallet”.
- **Painel Lojista**: scanner, botões “Marcar 1 selo” e “Resgatar”, relatório simples (dia/semana).

## Fluxos-chave (Detalhe Técnico)

### Gerar QR do Cliente (Dinâmico e Seguro)

1. Cliente abre “Meu cartão” → front chama `/cards/{id}/qr`.
2. Backend responde `{ token, ttl, hmac }`.
3. QR carrega `cardId`, `nonce`, `exp`, `signature`.

**Painel do Lojista lê o QR** → envia para `/stamp` → servidor valida HMAC+TTL+nonce → grava stamps.

### Atualizar Google/Apple Wallet

Ao marcar selo ou resgatar:

- Atualize o loyalty object (Google) com pontos/mensagens.
- Reassine e notifique o pass (Apple) via push para refletir a nova contagem.

## Roadmap (4 Sprints Curtas)

### Sprint 1 – Fundamentos (1–2 semanas)

- Auth (lojista e cliente), modelos, CRUD básico de programa.
- Card único por cliente/programa.
- Painel Lojista com scanner e Marcar.
- PWA Cliente com QR dinâmico.

### Sprint 2 – Regras e Resgates

- Regra “10 selos = 1 prêmio”, expiração opcional.
- Fluxo Resgatar com PIN do caixa.
- Logs + relatório simples.

### Sprint 3 – Carteiras

- Botão “Adicionar ao Google Wallet”.
- Atualização automática do pass ao marcar/resgatar.
- (Se der) Apple Wallet PassKit (precisa conta Apple Dev).

### Sprint 4 – Polimento

- Push “Você ganhou um almoço!”.
- Export CSV, multi-unidades (locations), permissões de usuários.
- Branding por loja (cores/logo).

## Custos e Operação

- **Infra**: 1 Postgres + 1 app (Tsuru/K8s ou Fly.io/Render/EC2) — baixo custo.
- **Apple Dev**: US$ 99/ano (só se usar Wallet da Apple).
- **Google Wallet**: sem custo.

### Modelo de Negócio SaaS

- Plano por loja (R$ 49–199/mês) + taxa por unidade extra.
- Valor visível: menos papel, controle, dados de recorrência.

## Experiência do Usuário (Reduz Fricção)

- Login social (Google/Apple).
- PWA com “Instalar app”.
- QR grande e com alto contraste.
- Se o cliente esquecer o celular: busque por telefone/e-mail no painel e carimbe manualmente.

## Próximos Passos Práticos

1. Escolher nome e domínio.
2. Definir regra padrão (10 selos, validade 6 meses?).
3. Subir Postgres (Docker) + Spring Boot skeleton com Flyway.
4. Criar PWA mínima: “Meu Cartão” (contador) + QR dinâmico.
5. Painel do lojista com scanner e endpoint `/stamp`.
6. Piloto com esse restaurante do seu almoço.

Se quiser, já te entrego um esqueleto de projeto (Spring Boot + React PWA) com as rotas, entidades e um fluxo de QR com token HMAC pra você plugar e testar na loja. Quer que eu gere esse boilerplate agora?
