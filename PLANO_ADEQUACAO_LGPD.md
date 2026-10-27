# Plano de pendências de adequação à LGPD

Data inicial: 2026-10-25. Atualizacao de implementacao: 2026-10-26.
Status: controles tecnicos implementados localmente; aprovacao juridica,
configuracao operacional e evidencias de producao continuam pendentes.
Escopo: dados de clientes, funcionários e pessoas físicas ligadas aos lojistas,
tratados pelo backend, pelo PWA e por fornecedores utilizados pelo Carimbai.

## Atualizacao De Implementacao

O usuario informou o canal **diniz3003@gmail.com**, autorizou CNPJ ficticio
apenas provisoriamente e declarou nao ter prazos de retencao nem decisao sobre
menores. Por isso, **00.000.000/0000-00 esta marcado FICTICIO**; aviso e rascunho,
push novo depende de aviso publicado e medidas destrutivas/expurgo seguem
desabilitados. Nao houve deploy ou alteracao do banco de producao.

| ID | Entrega desta implementacao | O que impede encerrar o item |
|---|---|---|
| LGP-01 | Canal configuravel e matriz de decisoes/responsabilidades | Identidade real, nomeacoes, teste do canal, papeis e contratos aprovados |
| LGP-02 | Inventario atualizado de backend/PWA/tokens/push/direitos/infra | Validacao de bases, necessidade, prazos, fornecedores e operacao real |
| LGP-03 | Aviso versionado, endpoint publico e acesso no PWA antes/depois do login; bloqueio de publicacao ficticia | Texto/identidade/aprovacao reais; segue rascunho |
| LGP-04 | Correcao/exportacao proprias, protocolos, escopo conta/loja, equipe separada, evidencia, holds e medidas locais protegidas | Nomear/treinar equipe, prazos por direito e comprovacao externa; medidas destrutivas nao habilitadas |
| LGP-05 | Expurgo por categoria em lotes, simulacao, preservacao e metricas | Politica e prazos aprovados; categorias fora do expurgo automatico e operacao monitorada |
| LGP-06 | Registro de fornecedores e roteiro contratual/transferencias | Identificar servicos/regioes reais e apresentar contratos/mecanismos validos |
| LGP-07 | Runbook, modelos e cenario de exercicio | Equipe designada, exercicio real, integracao operacional e provas |
| LGP-08 | Quarentena, diario assinado externo exportavel, replay offline/idempotente e verificacao de manifesto | Captura externa duravel, backup criptografado, RPO/RTO e exercicio real, revisao pos-snapshot |
| LGP-09 | Minimização JWT/DTO/logs, perfis proprios, metadados adicionais desligados e menos persistencia no PWA | Tokens ainda acessiveis a JavaScript; protecao/retencao de infra a comprovar |
| LGP-10 | Pendencia explicita e roteiro de avaliacao, sem coleta etaria improvisada | Decisao de publico, melhor interesse, bases e medidas juridicamente adequadas |
| LGP-11 | Escolha/retirada de push registradas, aviso publicado obrigatorio, texto generico, SDK social sob escolha e fontes remotas removidas | Aviso final, avaliacao de terceiros e base/finalidades aprovadas |
| LGP-12 | Testes tecnicos e modelos de risco, evidencia e revisao | Responsaveis, avaliacoes/treinamentos/exercicios executados e calendario aprovado |

Nenhum item juridico/operacional foi marcado concluido apenas por existir codigo.
Detalhes e evidencias: [IMPLEMENTACAO_LGPD.md](docs/privacy/IMPLEMENTACAO_LGPD.md).
Verificacao local final: 144 testes backend e 8 frontend aprovados; 3 cenarios
de navegador com API simulada aprovados; builds aprovados. Nessa rodada, o scanner
apontava dois alertas Spring. Atualizacao 2026-10-27: migracao Boot 4 validada com
151 testes e OSV sem alertas conhecidos, sem excecoes; homologacao externa
continua pendente. Ver [relatorio](docs/IMPLEMENTACAO_MIGRACAO_BOOT_4.md).
O conteudo a seguir preserva os criterios de encerramento do plano inicial.
As secoes "Situacao encontrada" e "Status inicial" sao a linha de base, nao
afirmacoes de que as novas funcionalidades ainda inexistem.

## 1. Objetivo e interpretação dos status

Organizar exclusivamente as pendências de privacidade, seus responsáveis,
entregáveis e critérios de encerramento. A correção dos problemas gerais de
software está em [PLANO_CORRECOES_TECNICAS.md](PLANO_CORRECOES_TECNICAS.md).

Este plano parte do código e dos documentos disponíveis. Uma política, contrato
ou procedimento pode existir fora dos repositórios; nesse caso, deve ser
apresentado e confrontado com a operação real antes de encerrar a pendência.
Não houve inspeção da VPS, dos contratos ou de processos internos da empresa.

Estados de acompanhamento:

- **Pendente:** falta definir ou entregar a solução.
- **Parcial:** há controles relacionados, mas o requisito ainda não foi coberto.
- **Não comprovado:** depende de evidência operacional ou documental externa.
- **Condicional:** é necessário avaliar e registrar a aplicabilidade.
- **Concluído:** responsável identificado, entrega validada e evidência registrada.

As prioridades abaixo são de execução do projeto. Não representam classificação
jurídica automática de gravidade, infração ou tratamento de alto risco.

## 2. Cuidados para usar este plano

1. APIs específicas, cookies HttpOnly e criptografia por coluna são escolhas de
   implementação; sua necessidade depende do risco e da solução adotada.
2. O atendimento ao titular pode começar com processo manual seguro e rastreável;
   não depende da criação prévia de uma API. Ver [direitos do titular na ANPD](https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados/direito-dos-titulares).
3. A dispensa de encarregado e os prazos diferenciados dependem de enquadramento
   comprovado no regulamento de pequeno porte. O canal de comunicação permanece
   necessário. Ver [Resolução CD/ANPD nº 2/2022](https://www.gov.br/anpd/pt-br/acesso-a-informacao/institucional/atos-normativos/regulamentacoes_anpd/resolucao-cd-anpd-no-2-de-27-de-janeiro-de-2022).
4. CPF e credenciais exigem proteção, mas não são automaticamente dados pessoais
   sensíveis na definição legal. Basear a classificação no contexto efetivo.
5. Não exigir consentimento genérico para todo o serviço nem RIPD universal:
   avaliar finalidade, base, risco e obrigações aplicáveis com assessoria jurídica.

Fundamentação geral: [LGPD, texto compilado](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm).
Este documento orienta entregas e evidências; a validação jurídica das decisões
continua necessária.

## 3. Fila de pendências

| ID | Pendência | Prioridade | Natureza | Responsável proposto | Status inicial |
|---|---|---|---|---|---|
| LGP-01 | Definir responsabilidades da plataforma, lojistas e canal de privacidade | Alta | Jurídica + operacional | Responsável do negócio + jurídico | Não comprovado |
| LGP-02 | Atualizar inventário e validar finalidade/base de cada tratamento | Alta | Jurídica + técnica | Privacidade + backend/frontend | Parcial |
| LGP-03 | Publicar aviso de privacidade coerente com o serviço | Alta | Jurídica + produto | Jurídico + produto/frontend | Não comprovado |
| LGP-04 | Atender solicitações dos titulares com segurança | Alta | Operacional + técnica | Privacidade + atendimento + backend | Pendente |
| LGP-05 | Definir e executar retenção, descarte e anonimização | Alta | Jurídica + técnica | Privacidade + backend + infraestrutura | Parcial |
| LGP-06 | Formalizar fornecedores, compartilhamentos e transferências | Alta | Jurídica + operacional | Negócio + jurídico + infraestrutura | Não comprovado |
| LGP-07 | Operacionalizar resposta e comunicação de incidentes | Alta | Operacional + jurídica | Responsável de incidentes + jurídico + tecnologia | Não comprovado |
| LGP-08 | Comprovar backup, recuperação e tratamento de dados restaurados | Alta | Operacional + técnica | Infraestrutura + backend | Não comprovado |
| LGP-09 | Comprovar minimização, controle de acesso e proteção de dados | Alta | Técnica + operacional | Backend/frontend + infraestrutura | Parcial |
| LGP-10 | Definir tratamento de crianças e adolescentes | A definir após avaliação | Jurídica + produto | Produto + jurídico | Condicional |
| LGP-11 | Avaliar consentimentos, armazenamento no navegador e push | Média | Jurídica + produto + técnica | Privacidade + frontend/backend | Parcial |
| LGP-12 | Manter avaliações de risco, evidências e revisão periódica | Média | Operacional + jurídica | Responsável do negócio + privacidade | Pendente |

Os responsáveis acima são funções, não pessoas já nomeadas. A primeira entrega
de cada item é atribuir um nome, um prazo de trabalho e quem valida o resultado.

## 4. LGP-01: responsabilidades e canal de privacidade

### Situação encontrada

`PRIVACY_ACTION_ITEMS.md` mantém os papéis controlador/operador e o atendimento
de privacidade como pendências. O cadastro de cliente é compartilhado entre
lojas, o que exige clareza sobre os limites de atuação de cada participante.

### Entregas

1. Mapear quem decide sobre cadastro global, participação em programas,
   antifraude, gestão de funcionários e notificações em cada operação.
2. Registrar a qualificação de plataforma e lojista por operação, sem assumir
   um único papel para todas as atividades somente por conveniência contratual.
3. Definir quem recebe pedidos dos titulares, quem executa providências e como
   a plataforma encaminha pedidos que dependem do lojista, e vice-versa.
4. Criar cláusulas de proteção de dados para a relação B2B: instruções, acessos,
   fornecedores, incidentes, término do contrato e destinação dos dados.
5. Avaliar indicação de encarregado ou registrar a justificativa de dispensa;
   publicar um canal de comunicação acessível e atribuir substituto operacional.

### Critério de encerramento

- [ ] Matriz de responsabilidades aprovada e contratos correspondentes disponíveis.
- [ ] Canal testado por uma solicitação fictícia e com responsável/substituto.
- [ ] Enquadramento e decisão sobre encarregado documentados, quando aplicáveis.

Dependência: orienta LGP-03, LGP-04, LGP-06 e LGP-07.

## 5. LGP-02: inventário e bases por finalidade

### Situação encontrada

`LGPD_DATA_MAPPING.md` contém um levantamento inicial, mas ainda descreve
endpoints como públicos e ausência de e-mail transacional, contrariando o código
atual. Serviços e tabelas novos não comprovam, sozinhos, uso em produção.

### Entregas

1. Atualizar o registro de operações com os seguintes grupos e seus fluxos:

| Grupo | Dados/artefatos a inventariar | Evidência técnica inicial |
|---|---|---|
| Cadastro de cliente | Nome, e-mail, telefone e identificador social | `Customer`, DTOs, `CustomerService` |
| Relacionamento comercial | Programas, cartões, selos e recompensas | Models de fidelidade e serviços |
| Funcionários | E-mail, vínculo, papel, hashes de senha/PIN | Models de core e `StaffService` |
| Lojistas pessoa física | Documento e contato quando identificarem pessoa natural | `Merchant`, DTOs administrativos |
| Sessões e recuperação | JWT, hashes de refresh/reset, IP e User-Agent | Serviços/tabelas de tokens; validar uso real |
| Auditoria | IDs de ator/alvo/loja, IP, User-Agent e detalhes de eventos | `AuditService`, `AuditSecurityService`, logs |
| Push | Endpoint de inscrição, chaves e mensagens enviadas | `PushSubscription` e serviços de push |
| E-mail | Destinatário, mensagens de recuperação e metadados do envio | `MailService` e configuração SMTP |
| Navegador | Dados de sessão e identificadores persistidos | Hooks do PWA e `services/api.ts` |
| Infraestrutura | Logs de proxy, banco, backups e acessos administrativos | Levantamento operacional pendente |

2. Para cada operação registrar finalidade, dados mínimos, origem, destino,
   acesso autorizado, retenção, base proposta e justificativa validada.
3. Distinguir uso necessário ao cartão, segurança, comunicação transacional e
   eventual divulgação promocional. Não inferir uma base única para todos.
4. Registrar o que está desabilitado ou incompleto no produto e revisar antes
   de ativar serviços de recuperação, refresh ou notificações.
5. Identificar compartilhamentos entre lojas: cadastro global não significa
   autorização irrestrita de consulta por qualquer funcionário.

### Critério de encerramento

- [ ] Inventário cobre o fluxo real, inclusive fornecedores e backups.
- [ ] Bases e finalidades têm responsável e justificativa registrada.
- [ ] Nenhum item depende apenas de uma sugestão não validada do relatório antigo.

## 6. LGP-03: aviso de privacidade e transparência

### Entregas

1. Redigir aviso a partir do inventário validado, com linguagem adequada ao
   cliente e informações específicas para funcionários, quando necessário.
2. Informar identidade/contato do responsável, finalidades, categorias de dados,
   compartilhamentos, retenção e caminhos para exercer direitos.
3. Publicar no PWA com acesso antes do cadastro e dentro da sessão; registrar
   versão, data e procedimento de revisão quando o tratamento mudar.
4. Conferir o texto contra o comportamento real. Não prometer exclusão imediata,
   armazenamento só no Brasil ou criptografia específica sem evidência.
5. Reconciliar termos de uso e contratos com o aviso. O documento contratual
   não substitui as informações de privacidade que precisam chegar ao titular.

### Critério de encerramento

- [ ] URL publicada e acessível, texto aprovado e versão arquivada.
- [ ] Dados, fornecedores e retenções descritos correspondem ao inventário.
- [ ] Canal informado funciona e encaminha a um responsável real.

Dependências: LGP-01/02; revisão de LGP-05/06.

## 7. LGP-04: atendimento ao titular

### Situação encontrada

Não foi encontrado fluxo completo de solicitações no backend/PWA nem um
procedimento operacional validado. Alterar cadastro pelo balcão não atende
esse requisito com segurança.

### Processo inicial proposto

1. Receber pedido no canal publicado e gerar protocolo restrito ao atendimento.
2. Verificar identidade proporcionalmente ao pedido. Preferir sessão autenticada
   ou contato já validado; não coletar documento adicional sem necessidade.
3. Identificar o escopo: conta global, dados de um programa/loja ou cadastro de
   funcionário. Encerrar participação em uma loja não significa apagar a conta
   inteira sem solicitação expressa nesse sentido.
4. Localizar os dados e tratar acesso/cópia, correção, bloqueio, eliminação,
   anonimização, informação sobre compartilhamentos e revogação, conforme o caso.
5. Analisar impedimentos de conservação antes de excluir; registrar a justificativa
   e comunicar limitações ao solicitante em linguagem clara.
6. Executar a ação com autorização, registrar resultado e comunicar os
   participantes que precisem refletir a correção/exclusão.
7. Entregar arquivos por meio seguro, com acesso restrito e prazo de disponibilidade;
   nunca misturar dados ou credenciais de outros clientes/funcionários.
8. Definir prazos por tipo de pedido e enquadramento. Não aplicar um único prazo
   genérico a todos os direitos. Usar os arts. 18/19 e as regras aplicáveis.

A versão inicial pode ser manual. Se automatizada, usar a mesma comprovação de
identidade, autorização, rastreabilidade e avaliação de conservação.
Referência: [orientação da ANPD sobre direitos](https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados/direito-dos-titulares).

### Cenários de validação

- [ ] Pedido legítimo de acesso retorna todos os grupos pertinentes de dados.
- [ ] Pessoa tentando agir por outro titular sem representação é rejeitada.
- [ ] Correção é refletida nos sistemas/participantes pertinentes.
- [ ] Exclusão considera cartões, selos, recompensas, push e sessões relacionadas.
- [ ] Exceção de conservação fica registrada com acesso e finalidade limitados.
- [ ] Solicitação feita a uma loja tem encaminhamento e resposta definidos.
- [ ] Há protocolo, responsável e evidência de conclusão sem dados excessivos.

Dependências técnicas: TEC-01/02/03 do plano técnico e política do LGP-05.

## 8. LGP-05: retenção, descarte e anonimização

### Situação encontrada

Há rotação de `audit.log` e `application.log`, mas não foi encontrado expurgo
geral no banco. Limite de tamanho e histórico de arquivos não implementam
retenção para `ops.audit_log`, clientes, selos ou registros de tokens.

### Entregas

1. Aprovar uma tabela de temporalidade por grupo de dados, sem inventar um
   prazo único de LGPD para todas as tabelas.
2. Para cada grupo definir evento inicial da contagem, prazo, justificativa,
   exceções de conservação, destino e responsável. Avaliar obrigações legais
   adicionais antes de escolher os prazos.
3. Cobrir clientes, vínculos com programas, funcionários, IP/User-Agent, auditoria,
   tokens consumidos/expirados, inscrições push, pedidos de direitos e backups.
4. Definir como identificar inatividade com dados mínimos; o cadastro atual não
   oferece, por si só, toda a informação para uma política de inatividade.
5. Implementar expurgo em lotes, idempotente, com simulação prévia, métricas e
   exceções para preservação legal. Validar as chaves estrangeiras existentes.
6. Testar anonimização considerando a possibilidade de religação por cartões,
   recompensas, auditoria e identificadores. Pseudonimização não encerra
   automaticamente a natureza pessoal dos dados.
7. Coordenar a política com fornecedores e com a restauração de backups,
   evitando reativar dados eliminados ou notificações canceladas.

### Critério de encerramento

- [ ] Tabela aprovada, rotinas executadas e amostra de resultado validada.
- [ ] Dados que ainda precisam ser conservados ficam segregados e justificados.
- [ ] Monitoramento identifica falha no descarte e responsável é acionado.
- [ ] Backup/restauração respeita o processo do LGP-08.

## 9. LGP-06: fornecedores e transferências internacionais

### Entregas

1. Inventariar Google, Apple, Facebook, Vercel, VPS/banco, provedor de e-mail
   e provedores de push efetivamente utilizados. Confirmar Resend na operação;
   a configuração padrão não comprova contratação nem envio real.
2. Identificar papel de cada participante, dados enviados, finalidade, regiões de
   processamento, suporte remoto, subcontratados e retenção.
3. Arquivar os instrumentos aplicáveis, responsabilidades de segurança, regras de
   exclusão, assistência ao titular e prazos internos de aviso de incidente.
4. Avaliar se há transferência internacional em cada fluxo e documentar o
   mecanismo jurídico aplicável. Não concluir regularidade apenas pela sede
   brasileira ou estrangeira do fornecedor.
5. Reconciliar o inventário, o aviso e os contratos com os lojistas.

Referência: [ANPD sobre transferências internacionais e Resolução nº 19/2024](https://www.gov.br/anpd/pt-br/assuntos/assuntos-internacionais/transferencia-internacional-de-dados).
Cláusulas-padrão são um dos mecanismos possíveis, não uma resposta automática
para qualquer fornecedor ou fluxo.

### Critério de encerramento

- [ ] Lista de fornecedores/fluxos validada e instrumentos disponíveis.
- [ ] Regiões e mecanismos de transferência têm evidência ou justificativa aplicável.
- [ ] Processo de troca/contratação de fornecedor exige atualizar o inventário.

## 10. LGP-07: resposta e comunicação de incidentes

### Entregas

1. Nomear coordenador, substituto e responsáveis por contenção, análise jurídica,
   preservação de evidências, comunicação e recuperação.
2. Criar roteiro para registrar ciência do incidente, sistemas/dados afetados,
   abrangência, avaliação de impacto, decisões e providências.
3. Prever contenção de conta comprometida, revogação de sessões, rotação de
   segredos e preservação restrita de evidências, sem destruir a trilha relevante.
4. Avaliar a obrigação de comunicar pelo impacto e pelos critérios cumulativos
   do regulamento. Tratar senhas não cria, isoladamente, presunção automática
   de que todo evento precisa ser notificado.
5. Preparar modelos e canais de comunicação, sem aguardar a investigação final
   para decidir sobre a comunicação inicial.
6. Registrar inclusive a decisão fundamentada de não comunicar e realizar
   exercício simulado com dados fictícios.

### Prazos e guarda a incorporar ao procedimento

| Situação | Regra a considerar |
|---|---|
| Comunicação obrigatória | Regra geral de três dias úteis para ANPD e titulares, a partir da ciência pelo controlador de que o incidente afetou dados pessoais |
| Informação inicial incompleta | Complementação fundamentada à ANPD em até vinte dias úteis da comunicação |
| Pequeno porte | Dobro dos prazos previstos nas hipóteses do regulamento, apenas com enquadramento aplicável comprovado |
| Registro de incidentes | Guarda mínima de cinco anos, inclusive incidentes não comunicados, observadas obrigações adicionais |

Responsabilidade e representação devem seguir o regulamento; não atribuir a
comunicação exclusivamente a um DPO inexistente. Ver [Resolução CD/ANPD nº 15/2024, arts. 5º, 6º, 9º e 10](https://bibliotecadigital.mj.gov.br/bitstream/1/12879/2/RES_ANPD_2024_15.html).

### Critério de encerramento

- [ ] Simulado comprova responsáveis, canais e tempo de resposta.
- [ ] Modelos, contatos e local protegido para registro estão disponíveis.
- [ ] O prazo aplicável à empresa foi definido e não depende de interpretação na crise.
- [ ] Fornecedores/lojistas têm fluxo de aviso compatível com a obrigação do controlador.

## 11. LGP-08: backup e recuperação

### Entregas

1. Levantar o que é copiado, frequência, local, criptografia, acesso e retenção.
2. Definir objetivos de perda tolerável de dados e tempo de recuperação com o negócio.
3. Separar acesso ao backup e à chave de proteção; testar recuperação em ambiente
   isolado com acesso restrito e dados adequados ao exercício.
4. Incluir na restauração um procedimento para reaplicar exclusões, cancelamentos
   de push e revogações que ocorreram depois do ponto restaurado.
5. Definir expiração de cópias, preservações excepcionais e descarte no fornecedor.
6. Registrar data do teste, executor, resultado e ações corretivas.

### Critério de encerramento

- [ ] Restauração testada atende aos objetivos aprovados.
- [ ] A recuperação não reativa contas, sessões ou comunicações já canceladas.
- [ ] Retenção e acesso às cópias estão documentados e são verificáveis.

## 12. LGP-09: minimização, acesso e proteção

### Pendências a tratar

1. Vincular o encerramento dos riscos de acesso indevido aos testes de TEC-01/02,
   sem repetir aqui a implementação geral de autenticação.
2. Revisar respostas e telas para evitar dados sem finalidade demonstrada,
   incluindo `providerId` e cadastros globais acessíveis por funcionários.
3. Definir quais dados de cliente precisam persistir no navegador. O PWA guarda
   sessão e dados pessoais em `localStorage`; registrar necessidade, prazo,
   limpeza e controles contra roubo de sessão. Rever o JWT de cliente, cujo
   prazo padrão é trinta dias, em conjunto com TEC-03/04.
4. Comprovar HTTPS, TLS do banco, controle de acesso administrativo e proteção
   de disco/backup em produção. O YAML não comprova a configuração efetiva.
5. Avaliar proteção de documento/contatos em repouso conforme risco, consultas
   necessárias e gestão das chaves; não presumir criptografia por coluna obrigatória.
6. Verificar quem acessa auditoria/logs, se os eventos identificam o ator
   necessário e se não imprimem senhas, tokens ou dados excessivos.
7. Comprovar rotação dos segredos anteriormente expostos. Limpeza de histórico,
   quando indicada, não substitui a revogação dos segredos comprometidos.

### Critério de encerramento

- [ ] Matriz de acessos testada e campos minimizados por público.
- [ ] Controles de produção comprovados com evidência sem segredos.
- [ ] Retenção do navegador/logs e revogação de sessão verificadas.

## 13. LGP-10: crianças e adolescentes

Não há verificação etária no cadastro atual; isso, isoladamente, não prova a
ocorrência de tratamento de dados de menores nem define a solução necessária.

1. Definir público esperado, possibilidade de acesso por menores e campanhas
   dos lojistas que possam alterar esse cenário.
2. Avaliar melhor interesse, bases, participação dos responsáveis e legislação
   infantojuvenil aplicável ao produto com assessoria jurídica.
3. Escolher medidas proporcionais ao cenário confirmado, evitando coleta
   excessiva de documentos ou data de nascimento sem finalidade.
4. Ajustar linguagem, atendimento e funcionalidades para o público definido.

Aceite: avaliação registrada, responsável pela decisão e medidas aplicáveis
validadas no produto. Se não aplicável, guardar a justificativa e os gatilhos
para nova avaliação; não marcar concluído apenas por inserir uma declaração etária.

## 14. LGP-11: consentimentos, navegador e notificações

1. Inventariar cookies, `localStorage`, scripts de login, rastreadores e outros
   serviços realmente ativos no PWA; não implantar banner genérico sem esse levantamento.
2. Avaliar bases e escolhas separadamente para o uso do cartão, comunicações
   transacionais e eventuais mensagens promocionais.
3. Quando consentimento for a base, definir registro da manifestação, finalidade,
   versão e revogação simples. A permissão de push do navegador não comprova,
   sozinha, todas as informações e decisões necessárias ao tratamento.
4. Implementar cancelamento de inscrição e propagação da escolha ao backend,
   limpeza de inscrições inválidas e respeito ao encerramento da conta.
5. Revisar o conteúdo de push para evitar exposição indevida de informações
   na tela bloqueada ou em dispositivos compartilhados.

Aceite: fluxos com escolhas aplicáveis testados, cancelamento efetivo e
inventário/aviso coerentes. Se não houver ferramenta não essencial de rastreamento,
registrar o cenário; não tratar ausência de banner como infração automática.

## 15. LGP-12: evidências, risco e manutenção

1. Atualizar `LGPD_DATA_MAPPING.md` e reconciliar pendências com
   `PRIVACY_ACTION_ITEMS.md`, preservando o histórico das decisões.
2. Avaliar riscos residuais do cadastro compartilhado, do acesso dos lojistas e
   das integrações; registrar necessidade de RIPD conforme cenário e solicitações
   aplicáveis. Registrar uma decisão fundamentada quando ele não for necessário.
3. Manter responsável, versão e evidência de cada política, contrato e controle.
4. Definir revisão periódica e gatilhos: fornecedor novo, campanha com novo
   público, mudança de finalidade, incidente ou expansão de dados coletados.
5. Orientar operadores sobre acesso mínimo, validação de pedidos e resposta
   a incidentes. Registrar participação e materiais usados.

Aceite: calendário, responsáveis e registro de revisões disponíveis; pendências
condicionais possuem justificativa, responsável e gatilho de reavaliação.

## 16. Ordem de execução e comprovação final

| Etapa | Itens | Resultado esperado |
|---|---|---|
| 1 | LGP-01/02 e contenção técnica de acessos | Responsabilidades, canal e inventário confiáveis |
| 2 | LGP-03/04/05 | Informação ao titular, atendimento operacional e retenção definida |
| 3 | LGP-06/07/08/09 | Fornecedores, incidentes, recuperação e controles comprovados |
| 4 | LGP-10/11/12 | Aplicabilidade resolvida, preferências atendidas e revisão instituída |

As etapas podem avançar juntas quando não houver dependência. Não publicar
promessas no aviso antes de a respectiva operação estar pronta para cumpri-las.

- [ ] Todos os itens têm pessoa responsável e prazo de trabalho.
- [ ] Aviso e canal estão disponíveis aos clientes e funcionários pertinentes.
- [ ] Um pedido completo de titular foi atendido em teste seguro.
- [ ] Retenção e descarte têm execução e resultado demonstráveis.
- [ ] Fornecedores, contratos e transferências foram avaliados.
- [ ] Backup/restauração e resposta a incidente foram exercitados.
- [ ] Controles de acesso e sessão relacionados têm evidência técnica atual.
- [ ] Itens condicionais têm avaliação, sem obrigação presumida genericamente.
- [ ] A decisão de comercialização foi revisada pelos responsáveis de negócio,
      tecnologia e jurídico com as evidências disponíveis.

Registro por item: `ID | responsável | status | decisão | evidência |
data de validação | próxima revisão`. Usar evidências sem dados reais quando
possível e manter materiais com dados pessoais em repositório de acesso restrito,
não em commits públicos.
