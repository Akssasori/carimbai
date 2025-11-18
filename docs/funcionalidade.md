## 🚀 Visão Geral do Sistema de Fidelidade (Card Digital)

### ✅ Funcionalidades Principais Implementadas

| # | Funcionalidade | Descrição | Endpoints Relacionados |
| :---: | :--- | :--- | :--- |
| **1.** | **Cartão Digital no Celular** | O cliente possui um **Card** (cartão digital) no aplicativo. | `GET /api/cards/{id}/qr` |
| **2.** | **Loja Carimba o Cartão** | A loja escaneia o QR code do cliente para aplicar o carimbo. | `POST /api/stamp` |
| **3.** | **Sistema de Prêmios (Cada 10 Carimbos)** | Ao atingir 10 carimbos, o cliente pode resgatar um prêmio. | `POST /api/redeem` |

---

### 🌟 Detalhamento das Funcionalidades

#### 1. Cartão Digital no Celular
* O cliente tem um **Card** (cartão digital) dentro do app.
* O *Endpoint* `GET /api/cards/{id}/qr` gera um **QR code temporário** (válido por **45 minutos**).

#### 2. Loja Carimba o Cartão
* A loja escaneia o **QR code** do cliente.
* O *Endpoint* `POST /api/stamp` **aplica o carimbo**.
* **Incrementa** `stampsCount` no cartão.
* Registra o *Stamp* para fins de **auditoria**.

#### 3. Sistema de Prêmios (Cada 10 Carimbos)
* O *Program* define a regra `ruleTotalStamps = 10` (**configurável**).
* Quando `stampsCount >= 10`, o sistema detecta que o cliente **ganhou prêmio**.
* O *Endpoint* `POST /api/redeem` permite **resgatar o prêmio**.
* Após o resgate, a contagem de carimbos é **zerada** e um `Reward` é criado.

---

### 🔒 Funcionalidades de Segurança

* **Tokens HMAC:** QR codes são **assinados** e **temporários** (45 min).
* **Anti-replay:** Cada QR code só pode ser usado **1 vez**.
* **Rate limit:** Previne múltiplos carimbos rápidos (**120s** entre carimbos).
* **Idempotência:** Evita **duplicação** de carimbos.
* **PIN do Caixa:** Validação necessária ao resgatar o prêmio.

---

### 📊 Estrutura do Fluxo

1.  **Cliente** → Abre o app → Mostra o **QR code**.
2.  **Loja** → Escaneia o QR → Aplica **carimbo**.
3.  `Card.stampsCount++` (7, 8, 9, 10...).
4.  Quando atinge **10** → Cliente pode **resgatar prêmio**.
5.  **Resgate** → Cria `Reward` + **Zera contador**.