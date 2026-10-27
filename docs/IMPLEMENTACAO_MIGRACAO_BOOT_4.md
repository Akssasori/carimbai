# Implementacao Da Migracao Para Spring Boot 4

Data: 2026-10-27. Autorizacao: seguir o plano de migracao neste chat.
Status: codigo migrado e validado localmente; homologacao externa, liberacao
de producao e rollback operacional ainda pendentes.
Nao houve commit, push, deploy, acesso a VPS ou alteracao de banco real.

## Linha De Base

Branch merged-lgpd, HEAD 8e4de9efde09c15124259bd5bece45976efbad85, com alteracoes
locais preexistentes de seguranca/LGPD preservadas. Esse commit nao representa
um snapshot completo das mudancas locais usadas como ponto de partida.
Backend local anterior: Boot 3.5.16, Framework 6.2.19, 144 testes aprovados.
Evidencias preservadas em build/boot4-baseline: bom.json, osv-report-lgpd.json
e surefire-reports. Esse diretorio e ignorado pelo Git e nao e backup operacional.

O SBOM anterior tinha 133 componentes e dois alertas:
CVE-2026-47884 / GHSA-pc63-qcmh-9cmg e
CVE-2026-47890 / GHSA-j9f9-w8pj-32f8.
Nao foram criadas excecoes para esses ou outros avisos.

## Versoes Resolvidas

| Componente | Depois da migracao |
|---|---|
| Spring Boot | 4.0.9 |
| Spring Framework | 7.0.10, todos os modulos alinhados |
| Spring Security | 7.0.8 |
| Springdoc | 3.0.3, linha compativel com Boot 4.0 |
| Tomcat embarcado | 11.0.27 |
| Hibernate ORM | 7.2.25.Final |
| Jackson da aplicacao | tools.jackson 3.1.7 |
| Jackson 2 para bibliotecas que ainda o utilizam | core/databind/modulos 2.21.8; annotations compartilhadas 2.21 |
| PostgreSQL JDBC | 42.7.14 |
| Java | 25, mantido; ambiente local 25.0.2 |
| PostgreSQL | Linha 16 mantida; testes nativos descartaveis em 16.15 |
| MapStruct | 1.6.3, mantido |

Alvo conservador na linha 4.0, estavel e mantida na consulta desta data:
[release oficial 4.0.9](https://spring.io/blog/2026/10/23/spring-boot-4-0-9-available-now/).
Compatibilidade de springdoc conforme [matriz oficial](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).
Nao foram usados milestones, release candidates ou repositorios comerciais.

## Alteracoes De Implementacao

### MIG-01: Dependencias

Parent atualizado para Boot 4.0.9; starter-web substituido por starter-webmvc,
starter-flyway adicionado para auto-configuracao modular e testes adaptados
a starter-webmvc-test/starter-security-test. LocalServerPort permanece no
pacote spring-boot-test; AutoConfigureMockMvc utiliza o novo pacote webmvc.

Springdoc atualizado para 3.0.3. Retirados overrides antigos de Tomcat e
PostgreSQL quando o BOM novo ofereceu versao apropriada/equivalente.
Mantidos os patches de Commons Lang 3.21.0, Log4j 2.26.1 e Jackson 2.21.8,
pois nao se deve voltar a versoes inferiores apenas para remover propriedades.
Nimbus, JJWT, BCrypt, BouncyCastle e jose4j foram preservados.

O transporte alternativo async-http-client/Netty de push continua excluido.
O SBOM final nao contem io.netty nem hibernate-types-60.
Arvore estruturada: build/dependency-tree-boot4.json.

### MIG-02: JSON E Contratos

Imports da aplicacao/testes migrados de com.fasterxml.jackson.databind/core
para tools.jackson; JsonMapper.builder() substitui construcao direta de mapper.
JsonProcessingException foi adaptada para JacksonException. Anotacoes como
JsonAnySetter continuam em com.fasterxml.jackson.annotation, compartilhado
com Jackson 3.

spring.jackson.use-jackson2-defaults=true preserva os defaults de contrato
durante esta migracao. Isso nao ativa a ponte depreciada spring-boot-jackson2.
JJWT e bibliotecas que ainda precisam de Jackson 2 mantem sua familia
separadamente gerenciada; nao ha bean Jackson 2 substituindo o mapper da API.
Orientacao: [guia oficial de migracao](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).

Contratos verificados explicitamente: login, refresh, cartoes, QR, selo,
resgate, protocolo LGPD e erros. Numeros/booleans, nomes de campos, nulls e
datas ISO foram preservados. JSON invalido, aninhamento excessivo, UTF-8
invalido e limite efetivo de bytes continuam recusados sem ecoar o payload.
O teste de correcao com nome acentuado confirmou UTF-8 valido.

Na adaptacao do verificador Facebook, mensagens de falha passaram a registrar
somente classe/status genericos, sem corpo de erro ou mensagem potencialmente
contendo URL/token/dados pessoais.

### MIG-03: Persistencia

hibernate-types-60 removido. Location.flags e AuditLog.details usam o suporte
nativo @JdbcTypeCode(SqlTypes.JSON). Testes confirmam jsonb_typeof='object',
sem dupla codificacao, leitura das flags e escrita dos detalhes de auditoria.

Nenhum script Flyway existente foi alterado e nenhuma migration nova foi
necessaria para esta troca de mapeamento. A suite recria o schema desde vazio,
monta a versao anterior e verifica a atualizacao ate V2026.32.
UUID, datas, versoes, holds, diarios, preferencias e estruturas de revogacao
continuam exercitados nos testes de PostgreSQL.

### MIG-04: Seguranca E Concorrencia

Preservados denyAll, stateless, separacao cliente/staff, papel/vinculo atual,
isolamento por loja/titular, PRIVACY_OFFICER, revogacao, rotacao de refresh,
reset de uso unico, assinatura/expiracao de QR e idempotencia.
Nao foram comentados testes, removidos controles ou relaxadas validacoes.

Novos testes cobrem QR -> selo -> resgate, concorrencia de consumo do mesmo
QR, contrato de dados e revogacao recusada por uma segunda instancia real.
Corridas de refresh/reset e demais suites anteriores continuam passando.
Dois consumos concorrentes do mesmo QR nao geram dois selos.

O profile prod foi inicializado em contexto isolado contra PostgreSQL temporario:
OpenAPI/Swagger nao expostos, logger SQL efetivamente desligado, origem do
PWA aceita e localhost recusado em preflight. sslmode=disable foi usado
somente no argumento do teste para esse banco descartavel em loopback;
application-prod.yaml continua exigindo TLS por padrao.
Isso nao comprova configuracoes da VPS ou TLS da infraestrutura real.
O profile privacy-restore tambem inicializou e encerrou em modo offline/quarentena.

## Verificacao Final

| Verificacao | Resultado |
|---|---|
| ./mvnw -B -ntp -Psecurity-scan clean verify | BUILD SUCCESS |
| Testes backend | 151, zero falhas, erros ou ignorados |
| Integracao PostgreSQL/HTTP | 45 testes, incluidos no total |
| SBOM CycloneDX 1.6 | 153 componentes, sem dependencia de teste no escopo |
| OSV Scanner 2.7.0 sobre target/bom.json | Exit 0; nenhum alerta conhecido; sem supressoes |
| npm run build no PWA | Aprovado, sem mudanca de fonte nesta migracao |
| node --test tests/*.test.mjs no PWA | 8 aprovados |
| npm run lint no PWA | Zero erros, 5 avisos preexistentes de hooks |
| Diff/seguranca | Mudancas restritas a migracao, sem segredos reais adicionados |

Persistem avisos de build ja conhecidos de Lombok/MapStruct, de APIs internas
do Maven no Java 25 e de bundle PWA acima de 500 KB. Nenhum foi silenciado
para produzir sucesso.

Evidencias geradas localmente: target/surefire-reports, target/bom.json,
target/osv-report.json, target/carimbai-0.0.1-SNAPSHOT.jar e
build/dependency-tree-boot4.json. Os dois avisos antigos foram removidos
por atualizacao efetiva do componente, nao por excecao ou deteccao de uso.
Ausencia de alertas conhecidos no SBOM nao certifica ausencia de falhas,
adequacao juridica ou seguranca da operacao em producao.

Integridade dos artefatos locais validados (SHA-256):

| Arquivo | SHA-256 |
|---|---|
| target/carimbai-0.0.1-SNAPSHOT.jar | D6C3C6D0914436086144814F8928319719A932D45CD851C3D2761DF338179E9F |
| target/bom.json | B0AE1054E3705CF9A1AC452BE51F5E9325B36C87415A7904B86CC67CC3B6FDD7 |
| target/osv-report.json | 54345D5194D238B6017D0F3181F392109CE35023CC6B4BDFA8C134943DCB578E |
| build/dependency-tree-boot4.json | 05E7DD1CA68BEBF7FC0E365F7999DE71C7E0E3BF5FBC9A5E8EF9098C06064BEC |

## MIG-05: Limites E Liberacao

Concluidos: verificacao local limpa, contratos, banco descartavel, duas
instancias, profile prod isolado, SBOM/OSV e build/lint/testes do PWA.
Os workflows de verificacao/deploy nao foram enfraquecidos nem contornados.
O deploy continua dependendo do artefato verificado pelo pipeline;
esta sessao nao executou GitHub Actions nem publicou o JAR.

Ainda sem evidencia operacional:

- Homologacao real de Google/Apple/Facebook, SMTP e entrega push.
- HTTPS/proxy, TLS efetivo do banco, segredos e limites da VPS.
- Aprovacao de liberacao e procedimento de retorno exercitado.

Nao foi informado um ambiente externo autorizado para executar esses fluxos.
Nao usar producao ou enviar mensagens reais para concluir uma caixa de checklist.
As definicoes LGPD continuam inalteradas: aviso rascunho/CNPJ ficticio,
politica de menores/retencao pendente e acoes destrutivas desabilitadas.

## Rollback Operacional Pendente

Antes de liberar, preservar o artefato de producao aprovado com hash e
configuracao, testar backup/restauracao e reconciliacao de efeitos, e verificar
compatibilidade do artefato de retorno. As copias locais de relatorios nao
substituem isso; nao foi exercitado rollback na VPS.

Nao apagar migrations, token_version, revogacoes, preferencias, holds ou
diario para voltar uma versao. Se o retorno reintroduzir dependencias
vulneraveis ou enfraquecer controles, manter a liberacao restrita em vez de
ignorar o scanner. Nenhum comando destrutivo de rollback foi executado.
