# Implementacao Do Plano LGPD

Atualizacao tecnica em 2026-10-27: o backend migrou para Boot 4.0.9 / Spring
7.0.10, com 151 testes aprovados e OSV sem alertas conhecidos em 153 dependencias.
As evidencias abaixo preservam a entrega LGPD de 2026-10-26 e seu bloqueio de
dependencias daquela data. Aviso rascunho, conservacao, retencao e demais
pendencias juridicas/operacionais nao foram liberados pela migracao.
Ver [relatorio Boot 4](../IMPLEMENTACAO_MIGRACAO_BOOT_4.md).

Referencia: 2026-10-26. Backend C:/pessoal/carimbai e PWA
C:/pessoal/carimbai-app. Alteracoes locais, sem commit/push/deploy, sem acesso
a banco/VPS/contratos reais. Nao e certificado de conformidade ou liberacao
para comercializacao.

## Resumo Do Que Foi Entregue

- LGP-01/02: canal fornecido, inventario atualizado e modelos de governanca,
  papeis, bases e fornecedores, explicitamente nao aprovados.
- LGP-03: aviso classpath versionado e API publica sem cache. Publicacao exige
  recurso/texto/versao aprovados, referencia, identificacao nao ficticia e
  documento com digitos validos, inclusive formato alfanumerico de CNPJ.
  Validar digitos nao comprova titularidade juridica.
- LGP-04: correcao de nome/telefone pelo proprio cliente sem sobrescrita por
  login social posterior; exportacao por secao/cursor; protocolos autenticados,
  escopo conta/loja, fila PRIVACY_OFFICER, responsavel/prazo/evidencia, simulacao,
  conservacao/revisao/liberacao, restricao/eliminacao locais protegidas.
  Erros de escopo ou terceiro nao mudam cadastro/historico alheio.
- LGP-05: politica de expurgo por categoria, lotes, simulacao, SKIP LOCKED,
  protecao de FK e holds, aprovacao separada e metricas sem conteudo pessoal.
- LGP-06/07: modelos de fornecedores/transferencias, incidentes e exercicios,
  sem fingir contratos assinados ou equipe treinada.
- LGP-08: quarentena e diario pseudonimo assinado para reconciliar efeitos de
  privacidade/sessao, replay offline transacional/idempotente, verificacao de
  UUID/ID, origem, assinatura, ordem e manifesto independente de completude.
- LGP-09: providerId removido dos DTOs de cliente e e-mail removido de JWT;
  versao de sessao de cliente, TTL padrao 8h, ativos revalidados, logs por
  allowlist e metadados adicionais de rede desabilitados. JWT antigo de cliente
  sem versao e recusado, exigindo novo login, sem manter a antiga sessao longa.
- LGP-10: menores continuam pendentes. Nao foram inventados aceite etario,
  verificacao documental ou base legal.
- LGP-11: escolha de push LOYALTY_PUSH especifica, com versao/data/historico;
  retirada simples, exclusao de inscricoes proprias, verificacao antes de envio
  e mensagem generica. Permissao antiga do navegador nao vale como escolha.
- LGP-12: evidencias tecnicas e roteiros de risco/revisao; governanca real ainda
  precisa ser executada pelos responsaveis.

## PWA

Central /privacidade para publico/cliente e /staff/privacy para staff. Inclui
perfil, correcao, download paginado com indicacao de proxima pagina, pedidos e
acompanhamento; equipe designada tem fila, preview, prazo e analise protegida.
Nao ha sessao autenticada? O aviso e o canal permanecem acessiveis.

Somente ID/JWT de cliente vao para sessionStorage; perfil fica em memoria.
Legado em localStorage e removido. Staff mantem contexto/tokens em localStorage
para coordenacao entre abas, com allowlist e limite local de 8h nao prorrogado
por refresh; sessoes locais antigas sem validade sao descartadas.
**Isso nao equivale a cookie HttpOnly e nao elimina XSS.** Renovar/limpar
armazenamento local nao substitui revogacao e controles do servidor.

Google/Facebook sao montados somente apos escolha do provedor. Fontes remotas
Google foram removidas. Push informa finalidade opcional, exige aviso publicado,
permite retirada e nao reutiliza endpoint de conta anterior na nova inscricao.
Logout revoga inscricao no navegador e tenta remover o dispositivo no backend;
falha de rede/provedor deve ser tratada operacionalmente. Nao ha promessa de
revogacao instantanea de mensagem ja entregue ao provedor.

## Padroes Seguros Mantidos

| Configuracao | Padrao entregue |
|---|---|
| controller-document | 00.000.000/0000-00 (FICTICIO) |
| contact-email | diniz3003@gmail.com |
| notice-version | draft-2026-10-26 |
| notice-approved | false |
| destructive-actions-approved | false |
| destructive-approval-ref | Vazio |
| retention-enabled | false |
| retention-dry-run | true |
| retention-days | Nenhum periodo definido |
| collect-network-metadata | false |
| restore-dry-run | true |
| restore-expected-entries / restore-expected-last-id | -1, exige manifesto independente |
| journal-origin / journal-signing-secret | Vazios; exportacao retorna 503 ate configurar |
| privacy_officer | false para todo staff, sem concessao automatica |

restoration-quarantine=false no uso normal; exige true durante restauracao
isolada. Nunca usar flag de configuracao como substituto de aprovacao real.
Os padroes de rotacao de arquivos de log ja existentes nao viraram prazos legais.

## Migracao E Compatibilidade

V2026.32.10.26.00.00__privacy_controls.sql e aditiva: campos de estado/versao/
chave/privacidade, tabelas de pedidos, eventos, holds, preferencias, efeitos e
replays. Nao exclui contas/historico nem concede equipe de atendimento.
Migracoes anteriores foram preservadas. Testes aplicam migracao a schema anterior
em PostgreSQL descartavel; o banco real nao foi migrado nesta sessao.

Clientes e funcionarios precisam iniciar nova sessao do PWA apos a mudanca
do formato local. Atualizar backend e PWA de forma coordenada. Nao reverter
migracao apagando diario/tombstones para contornar incompatibilidade.

Para publicar aviso real: acrescentar recurso privacy/notice-VERSAO.txt revisado,
preencher notice-resource/notice-version correspondentes, controller-name,
controller-document real, contact-email e notice-approval-ref, entao aprovar
notice-approved em configuracao gerenciada. O rascunho nao pode ser publicado
apenas mudando um booleano.

## Evidencias De Verificacao

- Backend: Maven verify com perfil security-scan aprovado: **144 testes**,
  zero falhas/erros/ignorados. Inclui **38 testes de integracao** com PostgreSQL
  16.15 descartavel, todas as categorias de expurgo, eliminacao de cliente/staff,
  protecao de QR referenciado e inicializacao real do profile offline.
- Frontend: npm run build aprovado; node --test tests/*.test.mjs, 8 testes
  aprovados de contratos/armazenamento/sessao.
- Navegador Edge headless: 3 cenarios aprovados de anonimo, cliente e atendimento,
  com API simulada, dados ficticios e acesso externo bloqueado. Captura mobile
  inspecionada. Nao comprova integracao real de OAuth/SMTP/push.
- Lint: sem erros; 5 avisos preexistentes de dependencias de hooks em telas
  CustomerScreen/StaffScreen. Build aponta bundle acima de 500 KB.
- OSV/SBOM: 133 componentes, permanecem dois alertas Spring MVC 6.2.19:
  CVE-2026-47890 e CVE-2026-47884. Scanner retorna falha, sem supressoes.
  A decisao do usuario de manter deploy bloqueado foi preservada; Boot 4 nao
  foi migrado nesta entrega.

Testes relevantes: PostgresSecurityIntegrationTest, PrivacyNoticeServiceTest,
PrivacyMinimizationTest, PrivacyRestoreRunnerTest, PushPrivacyTest e regressao
CustomerRegistrationSecurityTest/JwtServiceTest. SQL de exportacao usa colunas
explicitas e parametros, sem nomes de tabela definidos pelo solicitante.

## O Que Nao Foi Comprovado

Identidade/contratos/bases, politica de menores, prazos aprovados, equipe e canal
operantes, risco/RIPD aplicavel, treinamento, controles de infraestrutura,
criptografia/TLS real, diario externo duravel, restauracao real e RPO/RTO.
As medidas locais mantem tombstones e evidencias pseudonimas: nao ha garantia
de anonimizacao irreversivel. Exportacao automatica nao inclui toda fonte externa.
Procedimentos e configuracoes adicionais sao necessarios para encerrar cada item.

## Documentos De Operacao

- [Inventario atual](INVENTARIO_ATUAL.md)
- [Governanca e fornecedores](GOVERNANCA_E_FORNECEDORES.md)
- [Retencao e direitos](RETENCAO_E_DIREITOS.md)
- [Contrato API](CONTRATO_API.md)
- [Backup/restauracao](BACKUP_E_RESTAURACAO.md)
- [Incidentes](RESPOSTA_A_INCIDENTES.md)
- [Plano de migracao Boot 4](../PLANO_MIGRACAO_SPRING_BOOT_4.md)

Formato alfanumerico de CNPJ: [manual tecnico da Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf).
O documento ficticio desta entrega nao representa inscricao valida da empresa.
