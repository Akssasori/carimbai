# Plano detalhado de correções técnicas

Data de referência: 2026-10-25.
Status em 2026-10-27: correcoes e migracao Boot 4 validadas localmente;
TEC-06 sem alertas OSV conhecidos. Homologacao e liberacao externa pendentes.
Escopo: backend `carimbai`, integração com `../carimbai-app` e validação de implantação.

Atualizacao de implementacao: veja
[relatorio atual](docs/IMPLEMENTACAO_PLANO_TECNICO.md) e
[plano de migracao para Spring Boot 4](docs/PLANO_MIGRACAO_SPRING_BOOT_4.md).
As evidencias e impactos abaixo preservam o diagnostico anterior a estas mudancas.
Em 2026-10-25 o usuario decidiu manter o deploy bloqueado pelos dois alertas
restantes do Spring, sem excecoes no scanner. Em 2026-10-27, a migracao foi
implementada: 151 testes aprovados e OSV sem alertas conhecidos em 153 dependencias.
Veja [evidencias da migracao](docs/IMPLEMENTACAO_MIGRACAO_BOOT_4.md).

## 1. Objetivo e limites

Resolver os sete problemas priorizados na revisão de código, com entregas pequenas,
testes de regressão e evidências verificáveis. As pendências de privacidade e
governança estão em [PLANO_ADEQUACAO_LGPD.md](PLANO_ADEQUACAO_LGPD.md).

A revisão foi estática: não comprova o comportamento da VPS, as configurações
efetivas de produção ou a aprovação dos testes atuais. Os totais históricos de
testes em `RETEST_REPORT.md` não substituem uma execução na revisão implementada.

Os documentos anteriores permanecem como histórico. Quando cada correção for
executada, reconciliar seu resultado com `SECURITY_FIX_PLAN.md`,
`SECURITY_REPORT.md`, `SECURITY_TEST_CASES.md` e `RETEST_REPORT.md`.

## 2. Prioridades e dependências

| Ordem | ID | Problema | Prioridade | Responsáveis | Dependência | Status |
|---|---|---|---|---|---|---|
| 1 | TEC-01 | Alteração do cadastro global e do vínculo social por funcionário | Alta | Backend + produto + frontend | Definir fluxo seguro de balcão | Implementado e testado localmente |
| 2 | TEC-02 | Permissões antigas continuam no JWT após mudança do vínculo | Alta | Backend | Nenhuma | Implementado e testado localmente |
| 3 | TEC-03 | Logout incompatível e revogações perdidas no reinício | Média | Backend + frontend | Contrato de sessão; coordenar com TEC-04 | Implementado; reinicio completo em homologacao pendente |
| 4 | TEC-04 | Recuperação de senha, refresh e gestão sem integração completa | Média | Backend + frontend + infraestrutura | TEC-01/02; TEC-03 para invalidação | Implementado; SMTP e PWA em homologacao pendentes |
| 5 | TEC-05 | Painel e notificações negados pelas regras de segurança | Média | Backend + frontend | TEC-02 e testes de autorização | Implementado; entrega push real pendente |
| 6 | TEC-06 | Nimbus JOSE + JWT 9.40 com vulnerabilidade publicada | Média | Backend | Nenhuma | Nimbus corrigido; liberacao bloqueada por dois alertas Spring |
| 7 | TEC-07 | Limite configurado não limita corpos JSON | Média | Backend + infraestrutura | Nenhuma | Aplicacao testada; exemplo de proxy entregue, VPS pendente |

Essa ordem preserva a priorização da análise. TEC-06 e TEC-07 podem ser antecipados
em entregas independentes enquanto os contratos de sessão são definidos.

Rastreio anterior: TEC-01 relaciona-se ao FIX-02; TEC-02 ao FIX-03/FIX-11;
TEC-03 ao FIX-11/FIX-12; TEC-04 ao FIX-21 e às entregas Tier 2/3;
TEC-05 ao FIX-02/FIX-03; TEC-07 ao FIX-04. TEC-06 é uma atualização da análise
de dependências e não deve ser considerado coberto por um teste antigo.

## 3. Preparação comum

1. Registrar branch, commit, versões de Java/Maven e estado inicial dos testes.
2. Usar dados fictícios e ambiente local ou de homologação para testes ativos.
3. Montar uma massa com duas lojas, dois clientes, ADMIN, CASHIER e PLATFORM_ADMIN;
   incluir um funcionário com vínculo nas duas lojas.
4. Inventariar o contrato real entre controllers, DTOs e `services/api.ts` do PWA.
5. Para alterações de banco, verificar o histórico Flyway aplicado antes de criar
   a próxima migration. Não editar migrations já executadas.
6. Manter controllers delegando, autenticação/autorização em `security/` e
   mapeamento de DTOs via MapStruct, conforme as convenções do projeto.

## 4. TEC-01: proteger cadastro global e identidade social

### Evidência e impacto

`CustomerController.loginOrRegister` permite CASHIER, ADMIN e PLATFORM_ADMIN.
`CustomerService.loginOrRegister` pesquisa globalmente por `providerId`, e-mail ou
telefone, atualiza nome/contato e aceita sobrescrever o `providerId` existente.
`socialLoginOrRegister` usa esse identificador para reconhecer o titular.

Exigir login de funcionário não comprova autorização para alterar uma identidade
global de cliente. O risco inclui acesso a dados de outras lojas e apropriação
de conta após uma vinculação social indevida.

### Implementação proposta

1. Aplicar a contenção inicial: o fluxo de balcão não pode sobrescrever dados de
   um cliente existente nem receber um identificador de login social confiável.
2. Separar a operação de balcão do login social. Criar DTO de balcão sem
   `providerId`; manter o identificador social exclusivamente no fluxo validado
   pelo provedor. Revisar também outros caminhos administrativos de criação.
3. Retirar o uso de `providerId` arbitrário como chave de busca do balcão.
4. Definir com produto quais dados o funcionário realmente precisa consultar e
   quais alterações pertencem ao titular. Relação comercial com uma loja não
   autoriza, sozinha, editar o cadastro compartilhado entre todas as lojas.
5. Para associar um cliente existente a um programa, preferir uma prova emitida
   pelo servidor para o cliente autenticado, com validade curta e uso único.
   Um ID sequencial ou um e-mail informado não serve como prova de identidade.
6. Definir o cadastro de novos clientes no balcão: dados mínimos, comprovação de
   contato quando necessária e nenhuma vinculação automática baseada apenas em
   e-mail não verificado.
7. Revisar o vínculo por e-mail no login social: só utilizá-lo quando houver
   garantia adequada de titularidade; não fundir contas por coincidência textual.
8. Revisar duplicidades antes de propor constraints para identificadores sociais.
   Não mesclar nem apagar registros automaticamente durante a migration.
9. Ajustar o frontend e registrar ator, alvo, loja, ação e resultado na auditoria,
   sem registrar tokens ou o conteúdo integral do cadastro.

Arquivos principais: `CustomerController`, `CustomerService`,
`UserRegistrationFacade`, DTOs de cliente, `CustomerMapper`,
`CustomerRepository` e os consumidores do cadastro no PWA.

### Testes e aceite

- [x] Caixa da loja A não lê nem altera o cadastro de B sem autorização adequada.
- [x] Enviar `providerId` pelo balcão nunca altera o vínculo de autenticação.
- [x] Tentativa de substituir vínculo de cliente já cadastrado é rejeitada.
- [x] Login social legítimo e acesso aos próprios cartões continuam funcionando em testes locais.
- [x] Prova de associação inválida, expirada ou reutilizada é rejeitada.
- [x] Respostas do balcão contêm somente os campos necessários.
- [x] Há teste HTTP com os filtros reais e teste do serviço; conferir somente
      a presença de `@PreAuthorize` não é suficiente.

Risco de implantação: mudança de contrato do balcão. Publicar a adaptação do PWA
antes de tornar obrigatório um novo campo de prova; não restaurar a atualização
global insegura como estratégia de rollback.

## 5. TEC-02: usar permissões atuais em cada requisição

### Evidência e impacto

`JwtAuthenticationFilter` recarrega o `StaffUser` e verifica seu estado global,
mas usa `role` e `merchantId` do JWT sem consultar o vínculo atual em
`StaffUserMerchant`. Desativar somente o vínculo ou rebaixar o papel não remove
as permissões do token já emitido. O TTL padrão do staff é oito horas.

### Implementação proposta

1. Revalidar assinatura, expiração, emissor, tipo e audience esperada do JWT.
   `aud` já é emitida, mas `JwtService.parseToken` atualmente exige só `iss`.
2. Centralizar em `security/` a resolução do contexto autenticado de staff.
3. Exigir usuário globalmente ativo, loja ativa e vínculo ativo entre os IDs
   autenticados. Falta de vínculo deve impedir o uso daquele contexto.
4. Derivar as autoridades de ADMIN/CASHIER do papel atual do vínculo no banco,
   não do papel histórico do JWT. Manter PLATFORM_ADMIN derivado do cadastro
   global atual, sem permitir sua concessão por um administrador de loja.
5. Reutilizar a mesma validação ao trocar loja e renovar sessão.
6. Padronizar 401 para credencial inválida/expirada/revogada e 403 para usuário
   autenticado sem permissão. O PWA depende dessa distinção para tratar a sessão.
7. Rejeitar tipos desconhecidos e divergências de audience, evitando tratar
   automaticamente qualquer token desconhecido como token de staff.

Arquivos principais: `JwtAuthenticationFilter`, `JwtService`, `SecurityUtils`,
`StaffUserMerchantRepository`, `AuthService` e testes de autenticação.

### Testes e aceite

- [x] Token emitido antes da desativação do vínculo é recusado na próxima chamada.
- [x] ADMIN rebaixado para CASHIER perde acesso administrativo imediatamente.
- [x] Usuário desativado globalmente e loja desativada não operam.
- [x] Vínculo da loja B não autoriza acessar a loja A.
- [x] Cliente não autentica como staff e staff não autentica como cliente.
- [x] Audience ausente/errada e emissor inválido são rejeitados.
- [x] Vínculos legítimos em várias lojas continuam funcionando via troca de loja.

Risco de implantação: tokens com claims legadas podem exigir novo login. Definir
essa transição explicitamente e medir o custo da consulta de vínculo, sem criar
cache que mantenha permissões revogadas por tempo indefinido.

## 6. TEC-03: unificar logout e persistir revogações

### Evidência e impacto

`AuthController.logout` revoga apenas o JWT recebido em `Authorization`.
`apiService.logoutStaff` envia `refreshToken` no corpo, sem esse header.
`useStaffSession` pode nem chamar o backend se não houver refresh token; o login
atual retorna esse campo como `null`. A denylist de `TokenRevocationService`
é um mapa em memória e se perde quando a aplicação reinicia.

### Implementação proposta

1. Definir um contrato único: logout recebe o JWT corrente no Bearer e,
   opcionalmente, o refresh token associado. Manter a resposta 204 idempotente
   para tokens ausentes, inválidos ou já revogados.
2. Alterar todos os pontos de saída do PWA, inclusive telas administrativas,
   para chamar esse contrato. Não condicionar a revogação do JWT à existência
   de um refresh token. Limpar o estado local mesmo em falha de rede.
3. Persistir os `jti` revogados e sua expiração no PostgreSQL existente, com
   índice e limpeza de entradas expiradas. Não persistir o JWT em claro.
4. Se houver refresh token, revogar a sessão correspondente sem revogar sessões
   de outros usuários. Definir o comportamento de logout de todas as sessões.
5. Preparar a invalidação de todos os tokens após reset de senha. Revogar apenas
   refresh tokens não invalida os access tokens já emitidos; usar um marcador
   persistido de validade por usuário ou sessão e validá-lo no filtro.
6. Vincular auditoria ao ator/sessão sem expor credenciais e definir o
   comportamento em indisponibilidade do armazenamento de revogação.

Arquivos principais: `AuthController`, `TokenRevocationService`,
`JwtAuthenticationFilter`, modelos/repositórios de sessão e nova migration;
no PWA, `services/api.ts`, `useStaffSession`, `useCustomer` e `StaffScreen`.

### Testes e aceite

- [ ] Logout do cliente, do painel de staff e das telas administrativas revoga o JWT.
- [ ] Reutilizar o JWT após logout falha, inclusive após reiniciar o backend.
- [x] Logout repetido e credencial invalida retornam 204 sem vazar informações.
- [x] Logout funciona para sessões antigas que não possuem refresh token.
- [ ] Token revogado em uma instância é recusado pelas demais, se houver mais de uma.
- [x] Expurgo remove somente entradas cujo token já expirou.
- [x] Reset de senha invalida access e refresh tokens anteriores.

Implantação: aplicar a migration antes da nova versão do backend e adaptar o PWA
de forma compatível. Registrar a política para tokens revogados antes da migração;
não prometer recuperar a denylist que já foi perdida em reinícios anteriores.

## 7. TEC-04: concluir contratos já consumidos pelo frontend

### Evidência e impacto

Existem `RefreshTokenService`, `PasswordResetTokenService`, DTOs e migrations,
mas o `AuthController` atual expõe somente login, troca de loja e logout.
O `MerchantController` não expõe todas as listagens/edições utilizadas no PWA.

### Contratos a implementar ou reconciliar

| Método e rota | Acesso proposto | Comportamento esperado |
|---|---|---|
| `POST /api/auth/refresh` | Refresh token válido | Renovar access token e rotacionar refresh token |
| `POST /api/auth/forgot-password` | Público, com limites de abuso | Receber pedido e responder genericamente com 204 |
| `POST /api/auth/reset-password` | Token de recuperação válido | Trocar senha uma única vez e invalidar sessões anteriores |
| `GET /api/merchants/{id}/admin/programs` | ADMIN da loja ativa | Listar visão administrativa, incluindo inativos |
| `GET /api/merchants/{id}/locations` | ADMIN da loja ativa | Listar unidades da loja |
| `PUT /api/merchants/{id}/locations/{locationId}` | ADMIN da loja ativa | Atualizar somente unidade pertencente à loja |
| `GET /api/merchants/{id}/staff-users` | ADMIN da loja ativa | Listar equipe com DTO sem hashes/credenciais |
| `PATCH /api/merchants/{id}/staff-users/{staffId}` | ADMIN da loja ativa | Alterar apenas o vínculo do staff naquela loja |

Os nomes finais devem ser conferidos com `../carimbai-app/src/services/api.ts`.
Não abrir uma rota em `SecurityConfig` antes de implementar sua autorização.

### Entrega A: refresh

1. Fazer login e troca de loja retornarem os campos que o PWA efetivamente usa.
2. Antes de renovar, verificar o usuário, a loja e o vínculo conforme TEC-02.
3. Tornar a rotação atômica com bloqueio/atualização condicional no banco.
   Uma transação sem proteção concorrente não garante uso único.
4. Definir família de sessão e resposta a reutilização de token rotacionado;
   garantir que a revogação não seja desfeita por rollback da exceção de rejeição.
5. Manter somente hashes de refresh tokens no banco e não expor os tokens em logs.
6. Definir o tratamento de refresh tokens ao trocar a loja ativa.

### Entrega B: recuperação de senha

1. Conectar os endpoints aos serviços existentes e ao provedor de e-mail.
2. Usar resposta genérica para pedidos de recuperação, sem revelar a existência
   da conta. Limitar abuso por origem e por conta, com cuidado com bloqueios indevidos.
3. Montar o link com URL-base confiável configurada; não confiar no header Host.
4. Validar e consumir o token e trocar a senha em uma operação atômica protegida
   contra concorrência. Aplicar a política de senha e BCrypt existente.
5. Invalidar tokens de recuperação anteriores, refresh e access tokens conforme
   a política de sessão do TEC-03.
6. Em produção, não substituir envio por impressão do token no console.
   Monitorar falha de entrega sem confirmar entrega que não aconteceu.

### Entrega C: gestão administrativa

1. Completar controllers, serviços, consultas e mappers usando o padrão existente.
2. Validar loja do path, do token e do recurso antes da alteração.
3. Ao desativar um funcionário de uma loja, alterar o vínculo correspondente,
   preservando vínculos legítimos em outras lojas.
4. Não aceitar `platformAdmin`, hashes ou atributos globais em DTO de edição local.
5. Definir paginação/limites para listagens e proteger alterações de flags de unidade.
6. Registrar alterações administrativas com ator, alvo, loja e resultado.

### Testes e aceite

- [x] Todas as chamadas listadas têm rota, contrato e autorização compatíveis.
- [x] Requisições simultâneas não renovam nem consomem duas vezes o mesmo token.
- [x] Token reutilizado e vínculo desativado não renovam sessão; expiracao e verificada no servico.
- [ ] Recuperação não revela se um e-mail existe e não aceita link/redirect arbitrário.
- [x] Senha anterior deixa de funcionar e as sessões anteriores são invalidadas.
- [x] ADMIN da loja A não consulta nem edita recursos administrativos de B.
- [x] Desativar vínculo em A não desativa indevidamente vínculo em B.
- [ ] O fluxo completo é exercitado pelo PWA em homologação.

Implantação: três entregas separadas. Não anunciar recuperação ou refresh como
concluídos somente porque existem serviços e tabelas. Verificar credenciais de
e-mail, domínio remetente, migrations e compatibilidade do PWA antes de habilitar.

## 8. TEC-05: corrigir regras das rotas de painel e push

### Evidência e impacto

As rotas do `StaffDashboardController` e do `NotificationsController` não estão
autorizadas em `SecurityConfig` e caem em `anyRequest().denyAll()`.
O subscribe ainda exige `hasAuthority('CUSTOMER')`, enquanto o filtro concede
`ROLE_CUSTOMER`. O erro impede o uso legítimo dessas funções.

### Implementação proposta

1. Preservar a negação por padrão e cadastrar as combinações exatas de método/rota.
2. Permitir `GET /api/notifications/vapid-public-key` publicamente; retornar
   somente a chave pública, nunca material privado.
3. Exigir `hasRole('CUSTOMER')` ou a autoridade equivalente com prefixo correto
   para `POST /api/notifications/subscribe`, tanto na URL quanto no método.
4. Derivar `customerId` do contexto autenticado; ignorar/rejeitar divergência no corpo.
5. Liberar os GETs de métricas, selos recentes e resgates recentes somente para
   CASHIER/ADMIN, com vínculo e loja atuais e escopo aplicado nas consultas.
6. Antes de habilitar push, validar endpoint/chaves e restringir os destinos de
   envio, impedindo uso da inscrição para alcançar serviços internos.
7. Conferir os limites das consultas do painel e as respostas de configuração
   indisponível de push, sem derrubar o restante da aplicação.

### Testes e aceite

- [x] Cliente autenticado consegue inscrever o próprio dispositivo em teste HTTP.
- [x] Anônimo e staff não conseguem se inscrever como cliente.
- [x] Alterar o `customerId` no corpo não vincula a inscrição a outro titular.
- [x] Métricas e listas respeitam a loja ativa e não aceitam um token de cliente.
- [x] Rotas não previstas continuam bloqueadas e o preflight CORS funciona.
- [x] Os testes passam pela `SecurityFilterChain` e pelas anotações de método.

Arquivos principais: `SecurityConfig`, `NotificationsController`,
`StaffDashboardController`, serviços de painel/push e testes HTTP de segurança.

## 9. TEC-06: atualizar dependência vulnerável

### Evidência e impacto

`pom.xml` fixa `com.nimbusds:nimbus-jose-jwt:9.40`, usado em
`AppleTokenVerifier`. Essa versão está na faixa afetada pela CVE-2025-53864,
de indisponibilidade no processamento de JSON profundamente aninhado.
O enquadramento vem da versão declarada; não foi executada exploração ativa.

Referência: [aviso GHSA-xwmg-2g98-w7v9](https://github.com/advisories/GHSA-xwmg-2g98-w7v9).

### Implementação proposta

1. Levantar a árvore de dependências resolvida, inclusive versões transitivas.
2. Selecionar versão mantida e compatível com Java/Spring que não esteja nas
   faixas vulneráveis conhecidas. `10.0.2` é uma correção histórica deste CVE,
   não garantia de ausência de outros problemas na data da implementação.
3. Atualizar a dependência e adaptar apenas as APIs afetadas.
4. Testar o verificador Apple e garantir rejeição de tokens inválidos,
   configuração incompleta e audience incorreta antes de autenticar alguém.
5. Adicionar análise de dependências ao processo de CI e registrar resultados
   com versão, data e justificativa para eventuais exceções.

### Testes e aceite

- [x] Árvore resolvida não contém Nimbus 9.40 nem outra versão afetada pelo CVE.
- [x] Tokens Apple válidos continuam aceitos; inválidos/expirados são recusados.
- [ ] Entrada malformada não provoca estouro de pilha nem resposta com detalhes internos.
- [x] Build/testes completos e análise de dependências têm evidências registradas; o scan permanece bloqueante.

Risco: mudança de API ou comportamento de parsing. Usar fixtures locais e chaves
de teste; não depender de credenciais reais ou da disponibilidade de provedores.

## 10. TEC-07: limitar efetivamente o corpo JSON

### Evidência e impacto

`application.yaml` configura limites de formulário e multipart. O
`max-http-form-post-size` não estabelece um limite geral para `application/json`;
`max-swallow-size` controla descarte de corpos abortados, não resolve esse limite.

Referência: [conector HTTP do Tomcat](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html),
atributos `maxPostSize` e `maxSwallowSize`.

### Implementação proposta

1. Inventariar os maiores corpos legítimos e definir um limite único em bytes;
   sugestão inicial: 262144 bytes para o JSON da API, sujeita à medição.
2. Configurar o limite no proxy e implementar leitura limitada na aplicação,
   antes de desserialização ou processamento custoso.
3. Validar bytes realmente lidos; não confiar somente em `Content-Length`.
   Cobrir requisições sem tamanho declarado e com transferência chunked.
4. Retornar 413 com corpo de erro previsível, sem guardar o payload rejeitado.
5. Preservar CORS/OPTIONS e requisições normais. Manter regras específicas para
   multipart se futuramente houver upload legítimo.
6. Documentar a configuração equivalente em homologação e produção.

### Testes e aceite

- [x] Corpo abaixo do limite e no limite segue o fluxo normal de validação.
- [x] Corpo acima do limite retorna 413 antes de atingir o controller/serviço.
- [x] Sem `Content-Length` ou em chunked, exceder o limite também retorna 413.
- [x] JSON inválido pequeno continua retornando erro de entrada, não erro 500.
- [ ] As mesmas verificações funcionam diretamente na aplicação e via proxy.
- [x] Rejeições do filtro nao registram o corpo recebido, tokens ou senhas.

## 11. Validação, implantação e encerramento

### Evidências obrigatórias de cada entrega

| Verificação | Evidência a guardar |
|---|---|
| Testes unitários e de integração | Resultado ligado ao commit e ao cenário corrigido |
| Banco | Migration nova e teste de atualização de uma base com a versão anterior |
| Backend | `./mvnw verify`, sem ignorar testes |
| Frontend alterado | `npm run build`, `npm run lint` e validação do fluxo afetado |
| Segurança HTTP | Casos anônimo/cliente/CASHIER/ADMIN e loja A contra B |
| Reinício e concorrência | Testes de revogação persistente e uso único de tokens |
| Produção | Profile, HTTPS, CORS, TLS do banco e logs conferidos sem copiar segredos |

O workflow `.github/workflows/deploy.yml` agora depende de `verify.yml`, que roda
`clean verify` sem ignorar testes e analisa o SBOM com OSV antes de publicar o
artefato consumido pelo deploy. Falha nos testes ou no scanner bloqueia o envio
a VPS. O JAR local nao e autorizacao para contornar esse bloqueio.

Sequência de liberação: migrations compatíveis, backend compatível, PWA adaptado
e, por último, remoção de contratos antigos. Cada entrega deve ter rollback que
preserve isolamento, revogações e dados; reabrir acesso inseguro não é rollback aceitável.

### Checklist final

- [ ] TEC-01 e TEC-02 comprovados antes da ampliação do uso com dados reais.
- [ ] TEC-03 a TEC-07 concluídos ou com risco residual explicitamente registrado.
- [x] Testes HTTP cobrem a SecurityFilterChain real em ambiente isolado, além de serviços isolados.
- [x] Contratos frontend/backend adaptados e compilados; fluxo publicado ainda exige homologacao.
- [x] Relatórios anteriores reconciliados com referencia explicita ao reteste atual.
- [ ] Responsável, commit, data, testes e resultado de homologação registrados por item.

Modelo de acompanhamento: `ID | responsável | status | commit/PR | evidência |
risco residual | data da validação`. Estados: pendente, em execução, em validação,
concluído ou bloqueado com motivo e responsável pela dependência.
