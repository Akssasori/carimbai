# Implementacao do plano tecnico

Atualizacao de 2026-10-27: migracao Boot 4 implementada e validada localmente,
151 testes aprovados e OSV sem alertas conhecidos em 153 dependencias.
Homologacao externa e liberacao permanecem pendentes. O restante deste documento
preserva a entrega de 2026-10-25, inclusive o antigo bloqueio do Spring.
Estado vigente: [IMPLEMENTACAO_MIGRACAO_BOOT_4.md](IMPLEMENTACAO_MIGRACAO_BOOT_4.md).

Data: 2026-10-25. Escopo: `PLANO_CORRECOES_TECNICAS.md`.
Responsavel pela implementacao: Codex, mediante autorizacao do usuario neste chat.
Base: branch `merged-lgpd`, commit `8e4de9efde09c15124259bd5bece45976efbad85`.
As mudancas permanecem locais, sem commit, push ou deploy.

## 1. Resultado e limite da entrega

As correcoes de codigo dos sete itens foram implementadas no backend e o PWA
foi adaptado aos contratos de inscricao e sessao. Isso nao significa liberacao
para producao: o scan ainda encontra dois avisos do Spring Framework 6.2.19.
O usuario decidiu manter o bloqueio do deploy e planejar a migracao ao Boot 4,
sem excecoes no scanner. Veja [plano da migracao](PLANO_MIGRACAO_SPRING_BOOT_4.md).

Nenhum banco real foi consultado ou alterado. Foram usados dados ficticios em
PostgreSQL 16.15 descartavel. As alteracoes preexistentes no frontend foram
preservadas, inclusive configuracoes e ajustes visuais fora desta entrega.

## 2. Mudancas por item

| ID | Implementacao | Evidencia principal | Residual |
|---|---|---|---|
| TEC-01 | DTO de balcao sem providerId, rejeicao de campos extras, criacao sem consulta/alteracao de cadastro existente; retorno minimo; vinculo por e-mail somente com verificacao do provedor e cadastro ainda desvinculado | CustomerRegistrationSecurityTest e HTTP em PostgresSecurityIntegrationTest | Preflight de duplicidades em ambiente real e decisao de produto sobre cadastro sem contato comprovado |
| TEC-01 | QR de inscricao emitido ao cliente autenticado, HMAC com tipo proprio, TTL configurado de 90s e nonce de uso unico; POST /api/cards exige enrollmentProof | StampTokenServiceTest e teste HTTP de assinatura, titular, loja, uso e replay | Homologar camera e fluxo completo no PWA publicado |
| TEC-02 | Assinatura, issuer, audience e tipo validados; permissoes derivadas do vinculo atual; usuario, loja e vinculo ativos; PLATFORM_ADMIN vem do cadastro global atual | JwtServiceTest, JwtAuthenticationFilterTest e testes HTTP de rebaixamento/desativacao/multiloja | Validar topologia e custo das consultas em carga real |
| TEC-03 | Logout unificado recebe Bearer e refresh opcional; jti revogado persiste no PostgreSQL; limpeza de expirados; revogacao idempotente com ON CONFLICT | TokenRevocationServiceTest e teste HTTP/JPA; novo objeto de revogacao le o estado persistido | Reinicio de processo completo e varias instancias em homologacao ainda nao exercitados |
| TEC-03 | JWT de staff inclui versao e familia; reset invalida access e refresh; PWA limpa estado mesmo sem rede e nao restaura sessao por refresh atrasado | HTTP/JPA, concorrencia e testes Node do frontend | Falha de rede no logout impede garantir revogacao remota; limpeza local nao substitui confirmacao do servidor |
| TEC-04 | Login/troca de loja emitem refresh; /auth/refresh rotaciona sob lock, preserva familia e revoga toda a familia ao detectar replay, sem rollback da revogacao | Rotacao/replay e duas requisicoes simultaneas em PostgreSQL | Refresh concorrente indevido pode encerrar a familia por seguranca; PWA serializa renovacoes entre abas com Web Locks quando disponivel |
| TEC-04 | /forgot-password e /reset-password ligados; solicitacao generica assincrona, limite por IP/conta, URL-base confiavel, consumo atomico e hashes dos tokens; nenhum link no console | Testes HTTP, reset concorrente e PasswordRecoveryLimiterTest | Credenciais SMTP, dominio remetente e entrega real nao foram exercitados |
| TEC-04 | Listagem/edicao de programas, unidades e vinculos de staff com escopo da loja; listas paginadas com tamanho maximo 100; protecao do ultimo ADMIN | MerchantScopeAuthzTest e testes HTTP/JPA | PWA/homologacao administrativa completa pendente |
| TEC-05 | Rotas exatas do painel e push liberadas conforme papel; ROLE_CUSTOMER consistente; customerId divergente rejeitado; destinos HTTPS de provedores conhecidos e chaves validadas; redirects desativados e timeouts no envio | HTTP com SecurityFilterChain, PushEndpointPolicyTest e criptografia offline em RestrictedPushServiceTest | VAPID e entrega a provedores reais pendentes; chave publica retorna 503 sem configuracao valida |
| TEC-06 | Nimbus 10.10 e patches compativeis de dependencias; SBOM resolvido e gate OSV no CI | AppleTokenVerifierSecurityTest, SBOM, conteudo do JAR e OSV | Dois alertas Spring permanecem; liberacao bloqueada por decisao do usuario |
| TEC-07 | Filtro limita bytes efetivamente lidos de JSON a 262144 por padrao, inclusive sem tamanho declarado; retorna 413; configuracao exemplo de Nginx | JsonRequestSizeFilterTest e HTTP chunked contra Tomcat real | Exemplo de proxy nao foi aplicado nem testado na VPS |

O QR estatico com apenas customerId e a inscricao manual apenas por ID nao sao
mais aceitos. O PWA agora busca o QR temporario no servidor, renova sua exibicao,
oculta codigos expirados e permite inscricao em outro programa mesmo a clientes
que ja possuem cartoes. Depois do uso, o atendente precisa de um novo QR.

## 3. Evidencias executadas

Ambiente: Windows, Java 25.0.2, Maven wrapper 3.9.11 e Node 24.22.1.

| Comando/verificacao | Resultado |
|---|---|
| `./mvnw -B -ntp -Psecurity-scan clean verify` | BUILD SUCCESS na rodada limpa; suite ampliada novamente em seguida |
| `./mvnw -B -ntp -Psecurity-scan verify` | 108 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS |
| PostgresSecurityIntegrationTest | 16 testes HTTP/JPA, alem da verificacao de backfill antes dos testes; usa servidor Tomcat para o caso chunked |
| Migracao do schema anterior | 19 migrations ate V2026.30, dados ficticios antigos, depois V2026.31; versao e familia dos registros anteriores verificadas |
| `npm run build` no PWA | Sucesso; aviso de bundle acima de 500 kB permanece |
| `npm run lint` no PWA | 0 erros; 5 avisos de dependencias de hooks nas telas existentes; aviso da base baseline-browser-mapping |
| `node --test tests/security-contracts.test.mjs` no PWA | 4 testes aprovados |
| OSV Scanner 2.7.0 sobre target/bom.json | 133 componentes; exit code 1 pelos dois avisos Spring, sem supressoes |
| Inspecao do JAR | Nimbus/pacotes corrigidos presentes; transporte async-http-client/Netty excluido nao aparece no JAR |
| `git diff --check` backend e frontend | Sem erros de whitespace nas alteracoes; Git avisa conversao LF/CRLF em arquivo preexistente do frontend |

Os testes do frontend verificam sessao/QR em Node, nao substituem teste visual,
camera, OAuth com credenciais reais ou homologacao completa no navegador.

Artefatos gerados, ignorados pelo Git: `target/surefire-reports/`, `target/bom.json`,
`target/osv-report.json` e `target/carimbai-0.0.1-SNAPSHOT.jar`. O pipeline
preserva testes, SBOM e scan mesmo se a verificacao de seguranca falhar.

SHA-256 dos artefatos da rodada final:

- SBOM: `83D033AE37D0065B51581C1E6EA23A60B276D63DC78B1935F140FBFBE65DBA3D`.
- JAR: `B6D8DFC1065A25626E04EFE546774EB5323A75EDA245B488F9FE0BAA64BD1E84`.

Novos builds podem alterar esses hashes mesmo sem mudar as dependencias.

## 4. Dependencias e bloqueio de seguranca

| Componente | Versao resolvida |
|---|---|
| Spring Boot / Framework MVC | 3.5.16 / 6.2.19 |
| Nimbus JOSE + JWT | 10.10 |
| Jackson BOM | 2.21.8 |
| Tomcat | 10.1.61 |
| PostgreSQL JDBC | 42.7.14 |
| Commons Lang | 3.21.0 |
| Log4j API | 2.26.1 |
| Bouncy Castle | 1.86 |
| Web Push / jose4j | 5.1.2 / 0.9.7 |

A primeira analise encontrou alertas em varias dependencias alem do Nimbus.
Os patches e a remocao do transporte nao utilizado reduziram o resultado aos
seguintes avisos, ambos em spring-webmvc:6.2.19:

- [CVE-2026-47884 / GHSA-pc63-qcmh-9cmg](https://spring.io/security/cve-2026-47884/): XSLT e resolucao de views.
- [CVE-2026-47890 / GHSA-j9f9-w8pj-32f8](https://spring.io/security/cve-2026-47890/): fragmentos em streams SSE.

Nao foi encontrado uso dos recursos afetados no codigo atual. Isso e uma
analise de aplicabilidade, nao uma correcao da biblioteca nem motivo para
remover os alertas. O usuario escolheu manter o bloqueio e planejar a migracao.
Nenhuma excecao OSV foi criada. Build verde nao significa scan verde.

O workflow de deploy agora depende da verificacao e baixa somente o JAR
produzido por ela. O envio para VPS nao ocorre se testes ou scan falharem.
O workflow remoto ainda precisa ser exercitado no GitHub; nao houve push.

## 5. Banco, transicao e implantacao

Migration nova: `V2026.31.10.25.00.00__technical_security_hardening.sql`.
O numero segue a ordenacao Flyway existente, inclusive V2026.30; nao corresponde
a um mes valido de calendario e nao altera migrations anteriores.

A migration adiciona token_version, family_id, denylist e indices. Nao apaga
nem mescla clientes, sessoes ou vinculos. Antes de qualquer liberacao autorizada:

1. Resolver o bloqueio de dependencias conforme o plano de migracao aprovado.
2. Conferir o historico Flyway e executar as consultas somente leitura em
   `deploy/preflight_technical_hardening.sql` com acesso autorizado.
3. Revisar duplicidades de providerId/e-mail e lojas sem ADMIN ativo. Nao
   adicionar constraints, fundir ou apagar cadastros sem tratar os casos existentes.
4. Validar backup/restauracao e aplicar a migration compativel antes da nova aplicacao.
5. Publicar backend/PWA de forma coordenada: o backend exige enrollmentProof,
   enquanto o PWA novo busca uma rota que a versao antiga nao oferece. Usar
   janela de manutencao/feature flag de lancamento, sem reabrir inscricao por ID.
6. Confirmar CARIMBAI_APP_BASE_URL, SMTP/remetente, VAPID, OAuth e origens CORS
   efetivos, sem registrar segredos. A URL de recuperacao nao usa Host da requisicao.
7. Revisar `deploy/nginx-api-security.conf.example`, testar `nginx -t`, manter
   a porta do backend privada e sobrescrever forwarding headers no proxy confiavel.
8. Homologar todo o fluxo, limites via proxy, reinicio de processo e topologia real.

Tokens de staff legados com issuer/audience/jti validos e sem sid sao aceitos
com versao zero; logout por Bearer continua disponivel sem refresh. Reset de
senha incrementa a versao e invalida esses tokens tambem. Tokens sem claims
minimas validas exigem novo login. Nao e possivel recuperar revogacoes antigas
que ja tenham sido perdidas pela denylist em memoria da versao anterior.

## 6. Pendencias que nao sao certificacao legal

- Entrega real de e-mail e push, publicacao coordenada do PWA, proxy/HTTPS,
  TLS do banco, segredos, backups, perfil prod e carga nao foram verificados na VPS.
- Limites de abuso permanecem locais a cada processo; varias instancias exigem
  limites compartilhados. Revogacoes, familias e versao ja usam o banco compartilhado.
- Reinicio completo, recuperacao de backup e duas instancias simultaneas ficam
  como aceite operacional; reconstrucao do servico de revogacao foi testada.
- A migracao Boot 4 esta planejada, nao executada, e continua bloqueando a liberacao.
- O plano de LGPD continua separado. Base legal, transparencia, direitos dos
  titulares, retencao, contratos e governanca nao foram resolvidos por estes patches.

Os relatorios de seguranca anteriores sao historicos; seus totais antigos de
testes e rotulos de fechamento nao certificam o codigo atual nem a producao.
