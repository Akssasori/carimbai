# Ajustes Necessarios no Modelo para Multi-Merchant e Promocoes Especificas

## Objetivo

Este documento descreve o que precisa ser ajustado no backend e no modelo de dados para suportar os seguintes cenarios:

1. Um `staffUser` poder operar em varios `merchants`.
2. Um `merchant` poder trabalhar com varias promocoes especificas, e consequentemente ter varios `cards` vinculados a essas promocoes, sem ficar preso ao conceito de "almoco".

O objetivo aqui e documentar os impactos tecnicos. Este arquivo nao propoe implementacao de codigo pronta, apenas os ajustes necessarios.

## Estado Atual do Modelo

Hoje o backend esta estruturado assim:

- `core.merchants`: cadastro do estabelecimento.
- `core.locations`: unidades/locais de um merchant.
- `core.staff_users`: usuarios internos do merchant.
- `fidelity.programs`: regras de fidelidade de um merchant.
- `fidelity.cards`: cartoes de fidelidade de um cliente dentro de um programa.
- `fidelity.stamps`: carimbos aplicados em um card.
- `fidelity.rewards`: resgates/recompensas emitidas para um card.

### Relacoes atuais mais importantes

- Um `merchant` possui varias `locations`.
- Um `staff_user` pertence a um unico `merchant`.
- Um `merchant` possui varios `programs`.
- Um `card` pertence a um unico `program`.
- Um `customer` pode ter no maximo um `card` por `program`.

### Pontos que limitam a evolucao

#### 1. Staff user preso a um unico merchant

O modelo atual usa uma chave estrangeira direta:

- tabela `core.staff_users.merchant_id`
- entidade `StaffUser` com `@ManyToOne Merchant merchant`

Isso significa que toda a autenticacao e autorizacao assumem um unico contexto de merchant por usuario.

#### 2. O conceito de promocao ja existe, mas com nome e fluxos ainda amarrados

Na pratica, a tabela `fidelity.programs` ja representa bem uma promocao ou campanha. Ela contem:

- `merchant_id`
- `name`
- `rule_total_stamps`
- `reward_name`
- `expiration_days`

Ou seja: estruturalmente ja existe suporte para um merchant criar varias campanhas.

O problema principal nao esta no banco, mas em algumas decisoes do dominio atual:

- existe `DEFAULT_PROGRAM_ID = 1L`
- existe logica de criacao automatica de card para um programa padrao
- o default de `reward_name` foi pensado como "Refeicao gratis"

Isso faz o sistema parecer orientado a um unico fluxo padrao, mesmo que a modelagem de `programs` ja permita mais de uma promocao.

## Mudanca 1: Permitir que um Staff User tenha varios Merchants

### Problema atual

Hoje a relacao e:

- `staff_user -> merchant` = muitos para um

O requisito novo pede:

- `staff_user -> merchants` = muitos para muitos

### Ajuste recomendado no banco

Remover a dependencia de um unico `merchant_id` em `core.staff_users` e criar uma tabela de associacao, por exemplo:

`core.staff_user_merchants`

Campos sugeridos:

- `id`
- `staff_user_id`
- `merchant_id`
- `role`
- `active`
- `is_default`
- `created_at`

### Restricoes recomendadas

- FK `staff_user_id -> core.staff_users.id`
- FK `merchant_id -> core.merchants.id`
- `UNIQUE (staff_user_id, merchant_id)`

### Sobre o campo role

Este ponto precisa ser decidido no desenho final:

- Se o usuario tiver o mesmo papel em todos os merchants, o `role` pode continuar em `core.staff_users`.
- Se o usuario puder ser `ADMIN` em um merchant e `CASHIER` em outro, o `role` deve sair de `core.staff_users` e ir para a tabela associativa.

Na maioria dos cenarios de multi-tenant, faz mais sentido o papel ser por vinculo com o merchant.

### Sobre o merchant padrao

Como o usuario podera operar em varios merchants, sera necessario definir como o sistema escolhe o contexto ativo:

- merchant padrao salvo no vinculo (`is_default = true`)
- merchant enviado no login
- merchant selecionado pela interface apos login
- merchant enviado em header ou claim do token por request

Sem isso, o backend nao consegue saber em qual merchant o staff esta operando em cada acao.

## Impactos tecnicos da Mudanca 1

### Banco de dados

Sera necessario:

- criar a tabela `core.staff_user_merchants`
- migrar os dados atuais de `core.staff_users.merchant_id` para essa nova tabela
- depois remover ou descontinuar o uso de `merchant_id` em `core.staff_users`

### Entidades JPA

Sera necessario revisar:

- `StaffUser`
- possivelmente criar uma entidade de associacao como `StaffUserMerchant`
- possivelmente ajustar `Merchant` para relacao bidirecional, se fizer sentido

### Autenticacao e JWT

Hoje o token carrega um unico `merchantId`.

Com multi-merchant, sera necessario decidir entre duas abordagens:

1. Token com `merchantId` ativo
2. Token sem merchant fixo, exigindo selecao de merchant no contexto da request

A abordagem 1 costuma ser mais simples para autorizacao, mas exige troca de contexto quando o usuario muda de merchant.

### Autorizacao de negocio

Hoje varias validacoes comparam diretamente:

- merchant do staff logado
- merchant do card
- merchant da location

Essas validacoes precisarao mudar para:

- verificar se o staff possui vinculo com o merchant alvo
- validar se o papel naquele merchant permite a operacao

### DTOs e endpoints

Sera necessario revisar os contratos que hoje assumem um unico merchant:

- criacao de `staffUser`
- resposta de login
- possiveis listagens e filtros por merchant

Tambem sera importante decidir se a criacao de usuario ja recebe uma lista de merchants ou se o vinculo sera feito em etapa separada.

## Mudanca 2: Permitir varias promocoes especificas por Merchant

### Diagnostico

Para este requisito, o modelo atual ja esta quase preparado.

Hoje:

- um `merchant` ja pode ter varios `programs`
- cada `program` ja pode ter sua propria regra
- cada `customer` ja pode ter um `card` para cada `program`

Entao, do ponto de vista de banco, o sistema ja suporta varios cartoes por merchant de forma indireta, porque suporta varios programas por merchant.

Em outras palavras:

- se o merchant criar 5 programas diferentes
- o cliente pode ter 5 cards diferentes, um para cada programa

### O que esta faltando na pratica

O problema maior e conceitual e operacional:

- `Program` ainda parece "programa padrao de fidelidade"
- nao esta explicitamente tratado como campanha/promocao
- existe um programa default fixo no codigo
- existe criacao automatica de card nesse programa default

### Ajuste recomendado de dominio

Tratar `fidelity.programs` como a entidade principal de promocao.

Isso significa que cada promocao especifica pode ser um registro em `programs`, por exemplo:

- "Almoco"
- "Cafe da manha"
- "Sobremesa gratis"
- "Combo executivo"
- "Promocao de sexta"

Cada uma com suas proprias regras e recompensas.

## Ajustes recomendados em `fidelity.programs`

O schema atual pode continuar, mas seria interessante enriquecer a tabela para representar melhor campanhas:

Campos que podem ser adicionados:

- `description`
- `active`
- `start_at`
- `end_at`
- `category` ou `promo_type`
- `terms`
- `image_url` ou `banner_url`
- `sort_order`

Esses campos nao sao obrigatorios para o requisito minimo, mas ajudam bastante quando o front precisar listar promocoes especificas.

## Ajustes recomendados em `fidelity.cards`

### Cenario atual

A tabela `fidelity.cards` tem a restricao:

- `UNIQUE (program_id, customer_id)`

Isso significa:

- um cliente pode ter apenas um card por programa

### Quando isso ja atende

Se a regra desejada for:

- um card por cliente dentro de cada promocao

entao essa restricao pode continuar exatamente como esta.

Esse e o desenho mais natural para fidelidade por campanha.

### Quando isso nao atende

Se a regra desejada for:

- um cliente poder ter varios cards simultaneos para a mesma promocao

entao sera necessario:

- remover a restricao `UNIQUE (program_id, customer_id)`
- redefinir o criterio de busca e criacao de card

Hoje o backend sempre procura um unico card por:

- `programId`
- `customerId`

Se passarem a existir varios cards no mesmo programa, toda essa logica precisara ser redesenhada.

### Recomendacao

Para manter o dominio simples, a recomendacao e:

- continuar com um card por cliente por promocao
- usar `programs` como promocoes distintas

Isso ja atende a necessidade de "varias cards para promocoes especificas" sem multiplicar complexidade operacional.

## Impactos tecnicos da Mudanca 2

### Banco de dados

No cenario recomendado, o banco precisa de poucos ajustes:

- remover a dependencia conceitual de um programa default
- opcionalmente enriquecer `fidelity.programs`
- manter `fidelity.cards` com `UNIQUE (program_id, customer_id)`

### Servicos

Sera necessario revisar a logica que assume programa padrao:

- criacao automatica de card default
- uso de `DEFAULT_PROGRAM_ID`

O ideal e que os cards sejam criados:

- quando o cliente aderir a uma promocao
- ou quando houver primeira interacao com um programa especifico

### API

O sistema deve expor melhor a ideia de promocao:

- listar promocoes disponiveis por merchant
- permitir criar promocao com nome e regra proprios
- permitir criar/ativar card para um programa especifico

### Frontend e experiencia de uso

Provavelmente o front tambem precisara sair da ideia de "um cartao principal" e passar a trabalhar com:

- lista de promocoes do merchant
- lista de cards do cliente
- estado por promocao

## Recomendacao de Modelagem Final

### Para staff user e merchants

Adotar:

- `staff_users`
- `staff_user_merchants`
- papel por vinculo, se houver necessidade de permissao diferente por merchant

### Para promocoes

Adotar:

- `programs` como entidade de promocao/campanha
- varios programas por merchant
- um card por cliente por programa

### Vantagens dessa abordagem

- preserva boa parte da modelagem atual
- reduz ruptura no historico de `stamps` e `rewards`
- evita complexidade de varios cards simultaneos para a mesma campanha
- permite evoluir o produto para campanhas sazonais e promocionais

## Ordem sugerida de evolucao

### Etapa 1: Multi-merchant para staff

- criar tabela associativa
- migrar dados atuais
- ajustar autenticacao
- ajustar autorizacao por merchant ativo

### Etapa 2: Remover conceito de programa padrao

- retirar dependencia de `DEFAULT_PROGRAM_ID`
- parar de criar card automatico obrigatorio
- exigir selecao explicita de programa/promocao

### Etapa 3: Enriquecer promocoes

- adicionar campos extras em `fidelity.programs`
- melhorar listagem e ativacao por campanha

## Conclusao

O requisito de multi-merchant para `staffUser` exige mudanca estrutural real no modelo de dados e na autorizacao.

Ja o requisito de varias promocoes especificas por `merchant` quase ja existe no modelo atual, porque a tabela `fidelity.programs` pode cumprir esse papel. O principal ajuste nesse caso e deixar de tratar o sistema como se existisse um unico programa padrao, e passar a trabalhar com campanhas explicitas.

## Referencias no codigo atual

Arquivos mais diretamente impactados por essas mudancas:

- `src/main/resources/db.migration/V2025.11.09.11.16.00__init_core_loyalty_ops.sql`
- `src/main/java/com/app/carimbai/models/core/StaffUser.java`
- `src/main/java/com/app/carimbai/models/fidelity/Program.java`
- `src/main/java/com/app/carimbai/models/fidelity/Card.java`
- `src/main/java/com/app/carimbai/services/AuthService.java`
- `src/main/java/com/app/carimbai/services/JwtService.java`
- `src/main/java/com/app/carimbai/services/StaffService.java`
- `src/main/java/com/app/carimbai/services/CardService.java`
- `src/main/java/com/app/carimbai/services/StampsService.java`
- `src/main/java/com/app/carimbai/services/RedeemService.java`
