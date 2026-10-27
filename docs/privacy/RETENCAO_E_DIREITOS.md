# Retencao E Atendimento De Direitos

**Procedimento proposto, sem aprovacao juridica/operacional.** 2026-10-26.
O usuario informou nao possuir prazos aprovados. Nao foram inventados periodos
para permitir descarte. Responsavel, substituto e aprovador: A DESIGNAR.

## Atendimento

1. Receber pela central do PWA ou por diniz3003@gmail.com. Pedidos autenticados
   usam a identidade do JWT e chave pseudonima atual, nunca um customerId fornecido
   como suposta prova de identidade. UUID de protocolo permite acompanhamento.
2. Para conta inacessivel, representante, conta comercial ou pedido por e-mail,
   verificar identidade/representacao de forma proporcional por canal restrito.
   Nao solicitar senha/token e nao exigir documento integral por padrao.
3. Classificar direito, finalidade e alcance: conta global ou relacionamento
   com uma loja. Conferir relacao com a loja antes de aceitar escopo especifico.
4. Somente pessoa formalmente designada deve receber privacy_officer=true.
   ADMIN/PLATFORM_ADMIN nao bastam. Concessao deve ter aprovacao registrada
   e ser feita por administrador de banco autorizado; nao ha promocao automatica
   nem rota publica para conceder a flag.
5. Assumir protocolo com CLAIM e informar dueAt conforme analise do direito.
   Registro tem responsavel, status e evidencia; evidencias devem usar referencia
   opaca para repositorio restrito, sem nome/e-mail/documento no identificador.
6. Para correcao, nome/telefone do cliente podem ser atualizados diretamente.
   E-mail, subject social e identidade de staff exigem fluxo assistido que preserve
   seguranca do vinculo. Nao alterar subject por simples declaracao do solicitante.
7. Exportar por secao e todas as paginas. Complementar manualmente logs, dados
   de fornecedores, identificadores sociais e outras informacoes pertinentes,
   protegendo terceiros e credenciais. Portabilidade/anonimizacao sao pedidos
   analisados, nao promessas de conversao automatica do JSON ou anonimato.
8. Para restricao/eliminacao, consultar preview de impacto, conservacao, direitos
   de terceiros e necessidade de manter ultimo ADMIN. Justificar limitacoes,
   escopo, prazo e encaminhamento a loja/fornecedor.
9. A acao local exige politica aprovada, protocolo assumido e confirmacao exata:
   ERASE_ACCOUNT, ERASE_MERCHANT, RESTRICT_ACCOUNT ou RESTRICT_MERCHANT.
   ANONYMIZATION nao dispara exclusao por conveniencia.
10. APPLIED_LOCALLY significa apenas banco ativo coberto. Antes de COMPLETE,
    comprovar providencias em arquivos, fornecedores e backups, limitacoes
    justificadas e resposta ao titular. A API registra declaracao/evidencia do
    atendente, nao verifica automaticamente a verdade de documentos externos.

### Prazos

Nao aplicar prazo unico a todos os direitos. Confirmacao/acesso podem ter resposta
simplificada imediata ou declaracao completa em ate 15 dias, conforme art. 19;
avaliar regimes e regras aplicaveis com assessoria. Registrar vencimento
individual, acompanhamento e escalonamento, inclusive pedidos recebidos fora
da API. O sistema nao nomeia equipe nem envia lembretes de prazo automaticamente.
Ver [direitos na ANPD](https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados/direito-dos-titulares)
e [LGPD compilada](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm).

### Excecoes De Conservacao

Hold exige titular, motivo codificado, evidencia restrita, autor e data futura
de revisao. Liberacao exige nova evidencia e registra autor/data. Vencimento
de revisao nao libera automaticamente o hold: revisar e escalar ao responsavel.
Nao usar hold generico para impedir direitos indefinidamente. A API bloqueia
medidas conflitantes enquanto a conservacao estiver ativa.

## Matriz De Retencao A Aprovar

Preencher para cada categoria: dias, fundamento/necessidade, aprovador, evidencia,
gatilho, destino (eliminar/minimizar/conservar), excecoes e proxima revisao.
**Todos os prazos abaixo estao INDEFINIDOS.**

| Categoria configuravel | Gatilho tecnico do corte | Medida disponivel |
|---|---|---|
| REFRESH_TOKENS | expires_at anterior ao corte | Excluir expirados sem predecessor que ainda os referencia; preservar staff sob hold |
| RESET_TOKENS | expires_at anterior ao corte | Excluir expirados; preservar staff sob hold |
| QR_TOKENS | exp_at anterior ao corte | Excluir nao referenciados por selo; hold misto bloqueia lote |
| IDEMPOTENCY_KEYS | created_at anterior ao corte | Excluir chaves antigas; definir antes janela de repeticao segura |
| AUDIT_NETWORK_METADATA | occurred_at anterior ao corte | Remover IP/User-Agent, manter evento |
| STAMP_NETWORK_METADATA | when_at anterior ao corte | Remover IP/User-Agent, manter selo |
| AUDIT_EVENTS | occurred_at anterior ao corte | Excluir eventos somente conforme politica especifica |
| PUSH_SUBSCRIPTIONS | last_used_at anterior ao corte | Excluir cadastro nao atualizado; last_used_at indica registro/renovacao, nao leitura/entrega |
| CLOSED_REQUEST_CONTENT | completed_at anterior ao corte | Limpar descricao/resposta de COMPLETED/REJECTED; manter metadados de protocolo |

Para dados mistos (QR/idempotencia/auditoria/selos), qualquer hold ativo bloqueia
o lote conservadoramente; acompanhar para evitar acumulo injustificado. Mutacao
serializa com criacao/liberacao de holds, com lotes curtos. FK de refresh e QR
impede apagar evidencias ainda referenciadas.

Outras categorias exigem politica/procedimento adicional: conta inativa,
historico comercial, tombstones, protocolos/metadados, escolhas, holds,
diario/replays, arquivos de log, copias de banco, proxy/hosting/SMTP e fornecedores.
Nao ha expurgo automatico indiscriminado desses dados. Rotacao existente de logs
tem limites tecnicos configuraveis, nao prazos juridicos aprovados.

## Configuracao E Liberacao Controlada

Padroes: notice-approved=false, destructive-actions-approved=false,
retention-enabled=false, retention-dry-run=true e restore-dry-run=true.
Nao alterar para true apenas para fazer um teste em producao.

- Simular: POST /api/privacy/admin/retention/preview, apenas PRIVACY_OFFICER.
- Sem dias definidos: POLICY_UNDEFINED, nenhuma mutacao.
- Depois de aprovacao real, configurar carimbai.privacy.retention-days por
  categoria, batch-size (1..1000), retention-approval-ref, retention-enabled=true
  e retention-dry-run=false em configuracao gerenciada e revisada.
- A medida individual destrutiva exige destructive-actions-approved=true e
  destructive-approval-ref nao vazio, separados da politica de expurgo.
- POST /api/privacy/admin/retention/run e o job usam os mesmos bloqueios.
  Job fica inativo na quarentena de restauracao.
- Comparar simulacao/execucao, contagens, falhas e exemplos sintéticos;
  verificar credenciais ainda validas, terceiros e dados preservados.

Metricas internas Micrometer: carimbai.privacy.retention.runs,
carimbai.privacy.retention.processed e carimbai.privacy.retention.failures.
Nao publicar endpoint de metricas ao publico para observar isso.
Logs de falha nao incluem conteudo de registros. Agendamento nao substitui
monitoramento, responsavel e evidencias de execucao.
