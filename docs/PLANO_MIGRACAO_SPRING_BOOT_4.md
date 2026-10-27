# Plano de migracao para Spring Boot 4

Data inicial: 2026-10-25. Atualizacao: 2026-10-27.
Status: migracao implementada e validada localmente; homologacao externa,
liberacao de producao e rollback operacional ainda pendentes.
Decisao preservada: nenhuma excecao no scanner nem deploy para contornar bloqueios.

## Resultado Local Em 2026-10-27

Spring Boot 4.0.9 / Framework 7.0.10 / Security 7.0.8 / Tomcat 11.0.27 /
Hibernate 7.2.25.Final / Jackson 3.1.7 / springdoc 3.0.3.
Java 25 e PostgreSQL 16 foram mantidos. Nenhuma migration existente foi alterada.

- clean verify com security-scan: 151 testes aprovados, 45 de integracao.
- OSV 2.7.0: 153 dependencias, nenhum alerta conhecido, exit code 0, sem supressoes.
- Build e 8 testes Node do PWA aprovados; lint sem erros, 5 avisos preexistentes.
- Testados contratos JSON, JSONB nativo, QR/selo/resgate, concorrencia, revogacao
  entre instancias, restauracao offline e profile prod isolado sem Swagger/SQL
  e com CORS restrito.

Relatorio completo: [IMPLEMENTACAO_MIGRACAO_BOOT_4.md](IMPLEMENTACAO_MIGRACAO_BOOT_4.md).
As secoes de motivacao abaixo preservam a linha de base anterior a migracao;
os dois alertas de Spring 6.2.19 nao persistem no novo SBOM.

## 1. Motivo e criterio de desbloqueio

O backend foi atualizado dentro da linha Spring Boot 3.5, incluindo patches de
dependencias. O Spring Framework 6.2.19 ainda aparece nos avisos abaixo:

| Aviso | Recurso afetado | Situacao neste repositorio |
|---|---|---|
| CVE-2026-47884 / GHSA-pc63-qcmh-9cmg | Resolucao de XSLT com mapeamento abrangente | Nao encontrado uso de XsltView/XsltViewResolver |
| CVE-2026-47890 / GHSA-j9f9-w8pj-32f8 | Fragmentos de views enviados por SSE | Nao encontrados SseEmitter, FragmentsRendering ou text/event-stream |

A ausencia desses recursos reduz a exposicao identificada, mas nao remove o
componente afetado. Nao e comprovacao de ausencia de outras vulnerabilidades.
O fornecedor informa correcao publica no Framework 7.0.9; 6.2.20 pertence ao
suporte comercial. Nao misturar isoladamente Spring 7 com Boot 3.5.
Fontes: [aviso XSLT](https://spring.io/security/cve-2026-47884/) e
[aviso SSE](https://spring.io/security/cve-2026-47890/).

O workflow `verify.yml` executa o OSV antes de publicar o artefato para deploy.
Ele deve continuar falhando se esses ou novos avisos forem encontrados, sem
`continue-on-error`, exclusoes de avisos ou desativacao dos testes.

## 2. Preparacao e alvo

Responsavel: mantenedor do backend; homologacao conjunta com o responsavel pelo PWA.

1. Partir da implementacao tecnica atual, preservando as alteracoes locais do usuario.
2. Registrar commit/branch, relatorios de testes, SBOM e resultado OSV antes da migracao.
3. Selecionar um release estavel e mantido do Boot 4 cujo BOM resolva Spring
   Framework 7.0.9 ou superior e nao esteja nas faixas dos avisos atuais.
4. Manter Java 25 e PostgreSQL 16 inicialmente. Nao combinar a migracao do
   framework com mudanca de banco, autenticador, dominio ou frontend visual.
5. Usar somente o PostgreSQL descartavel dos testes e dados ficticios.

Seguir o [guia oficial de migracao](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
para modularizacao, starters, configuracao e bibliotecas gerenciadas. As versoes
exatas devem ser reconfirmadas no inicio da execucao.

## 3. Etapas por prioridade

### MIG-01: dependencias e compilacao

- Atualizar parent/BOM em `pom.xml` e reconciliar os starters de MVC, JPA,
  validacao, seguranca, e-mail, actuator e testes com os modulos do Boot 4.
- Remover overrides de patches somente quando o novo BOM oferecer versoes
  corrigidas equivalentes ou superiores; conferir a arvore resolvida.
- Atualizar springdoc para uma linha compativel com Boot 4 conforme a
  [matriz oficial](https://springdoc.org/). A versao 2.8.9 atual nao deve ser
  presumida compativel com o novo framework.
- Preservar Nimbus, JJWT, BCrypt, HMAC, restricao de destinos push e transporte
  Apache. Nao reintroduzir o transporte Netty excluido e nao usado pelo projeto.
- Compilar `src/main` e `src/test`; registrar cada incompatibilidade, sem
  comentar testes ou remover regras de seguranca para obter build verde.

Aceite: compilacao completa e nenhuma familia de dependencias com versoes misturadas.

### MIG-02: serializacao e contratos JSON

Arquivos de maior risco: `LocationService`, `PushNotificationService`, DTOs,
`CounterCustomerRequest`, mappers e testes que injetam `ObjectMapper`.

- Adaptar o uso de Jackson ao caminho recomendado pelo Boot 4, conferindo
  imports, excecoes, modulos de datas e configuracao efetiva do mapper.
- Preservar nomes de campos, formatos de data, nulabilidade e erros esperados
  pelo `carimbai-app/src/services/api.ts`.
- Manter a rejeicao de `providerId` e campos extras no cadastro de balcao.
- Testar aninhamento excessivo, JSON invalido, tamanhos limites e UTF-8.
- Garantir que flags JSONB continuem sendo objetos validos, sem dupla codificacao.

Aceite: snapshots de contratos de login, refresh, QR, cartoes, gestao e erros
sem diferencas nao aprovadas; build e testes do PWA continuam passando.

### MIG-03: persistencia e transacoes

`hibernate-types-60:2.21.1` e ligado a uma geracao anterior do Hibernate.
Inventariar os usos de `JsonType` e avaliar mapeamentos JPA/Hibernate suportados
ou uma biblioteca compativel com a versao efetivamente gerenciada pelo Boot 4.

- Verificar JSONB, INET, UUID, Instant/OffsetDateTime e enums em PostgreSQL real.
- Reexecutar todas as migrations desde uma base vazia e a atualizacao de uma
  base na versao anterior, preservando scripts ja aplicados.
- Confirmar os locks de staff antes de refresh/reset, rotacao de familias,
  `noRollbackFor` na deteccao de replay e invalidacao atomica de senha/sessoes.
- Repetir as corridas concorrentes de refresh, reset e consumo de nonce.
- Confirmar a protecao do ultimo ADMIN e isolamento entre lojas nas queries.

Aceite: sem alteracoes inesperadas de schema, sem perda de dados e um unico
consumidor bem-sucedido para cada token de uso unico.

### MIG-04: seguranca HTTP

Revisar `SecurityConfig`, filtros JWT/rate limit/JSON, handler e anotacoes de metodo.

- Preservar `denyAll` como padrao, stateless e ausencia de login por formulario.
- Testar novamente 401/403, issuer/audience/tipo, permissoes atuais e loja ativa.
- Garantir revogacao persistente de jti e familia, inclusive em outra instancia.
- Confirmar filtros executados uma unica vez, antes do processamento custoso.
- Cobrir CORS/OPTIONS e limite real de JSON, inclusive chunked e sem tamanho declarado.
- Exercitar perfil prod sem Swagger e sem logs de SQL, tokens ou senhas.

Aceite: matriz anonimo/cliente/CASHIER/ADMIN/PLATFORM_ADMIN e loja A/B aprovada.

### MIG-05: homologacao e liberacao

1. Executar `./mvnw -B -ntp -Psecurity-scan clean verify` e gerar novo SBOM.
2. Executar OSV sobre o SBOM resolvido, sem ignorar os dois avisos nem outros novos.
3. Executar build, lint e testes de sessao/QR do PWA.
4. Em homologacao, testar Google/Apple/Facebook, inscricao, selo, resgate,
   troca de loja, logout, refresh, recuperacao por SMTP e entrega push real.
5. Reiniciar o backend e verificar a recusa de credenciais revogadas; repetir
   a verificacao em duas instancias, se essa topologia for utilizada.
6. Conferir HTTPS, proxy, tamanho do corpo, CORS, TLS do banco e configuracoes efetivas.
7. Liberar somente o JAR produzido pelo pipeline aprovado. Nao usar build local
   com testes ignorados para contornar o bloqueio.

Aceite: os dois alertas removidos por atualizacao efetiva, nenhum novo alerta
bloqueante e homologacao registrada. A ausencia de alertas OSV nao certifica LGPD.

## 4. Rollback e encerramento

- Preservar migrations e estruturas de revogacao; nunca apagar a denylist,
  zerar token_version ou reabrir inscricao por ID como rollback.
- Registrar backup/restauracao testados e compatibilidade do artefato de retorno.
- Se o retorno reintroduzir falha bloqueante, restringir a exposicao e manter
  a liberacao suspensa ate uma versao aprovada; nao aceitar o risco silenciosamente.
- Atualizar `PLANO_CORRECOES_TECNICAS.md`, `RETEST_REPORT.md` e a documentacao
  da stack somente depois de registrar as evidencias finais da migracao.

Checklist da migracao:
- [x] MIG-01 dependencias e compilacao.
- [x] MIG-02 contratos JSON e validacao local do PWA.
- [x] MIG-03 JPA/PostgreSQL, migrations e concorrencia em banco descartavel.
- [x] MIG-04 autenticacao, autorizacao, filtros e profile prod isolado.
- [x] MIG-05 verificacao local limpa e OSV sem alertas conhecidos.
- [ ] MIG-05 homologacao externa de OAuth, SMTP, push e infraestrutura real.
- [ ] Liberacao autorizada e rollback verificado.
