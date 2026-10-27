# Inventario Atual De Dados

Revisao tecnica: 2026-10-26. Substitui o levantamento antigo da raiz.
Escopo: codigo local do backend e ../carimbai-app, nao a infraestrutura real.
Responsavel juridico: PENDENTE. **00.000.000/0000-00 e FICTICIO, sem validade**.
Canal informado: **diniz3003@gmail.com**, ainda sem atendimento comprovado.
Papeis da plataforma/lojista, bases e prazos: **NAO APROVADOS**.

| Operacao | Dados/origem | Armazenamento/saida | Finalidade/acesso observado |
|---|---|---|---|
| Cliente social | Nome, e-mail, subject verificado; telefone opcional | fidelity.customers | Login/vinculo global; providerId nao sai no DTO de login |
| Balcao | Nome/e-mail/telefone, sem provider arbitrario | fidelity.customers | Staff autenticado; nao recupera/altera cadastro global existente por e-mail |
| Adesao e fidelidade | Prova QR, cartoes, selos, recompensas, datas, IDs de caixa/unidade | fidelity.cards/stamps/rewards, ops.stamp_tokens | Cliente dono e staff com papel/vinculo atual por loja |
| Funcionario | E-mail, hashes BCrypt de senha/PIN, papeis e flags | core.staff_users/staff_user_merchants | Autenticacao, autorizacao e operacao |
| Lojista/unidade | Nome, documento, contatos/endereco conforme cadastro | core.merchants/locations | Gestao autorizada; pode identificar pessoa natural |
| Sessao/recuperacao | JWT de IDs/papel/versao/familia sem e-mail; hashes refresh/reset, datas | Navegador, ops.refresh_tokens/password_reset_tokens/revoked_tokens | Sessao, rotacao, revogacao, recuperacao por SMTP configurado |
| QR/idempotencia | Nonce, assinatura, referencia, validade/uso, chave idempotente | ops.stamp_tokens/idempotency_keys | Anti-replay, integridade e repeticao segura |
| Auditoria | IDs de ator/alvo/loja, evento, datas, resultado e detalhes por allowlist | ops.audit_log e arquivos | Seguranca/prestacao de contas; acesso de infraestrutura a comprovar |
| Rede | IP literal de conexao e User-Agent limitado quando habilitados | Tokens/auditoria; User-Agent de selos | Coleta adicional desabilitada por padrao; proxy/hosting podem registrar independentemente |
| Push | Endpoint, p256dh/auth, datas, escolha e versao do aviso | ops.push_subscriptions/privacy_preferences | Avisos opcionais de cartao, sem publicidade; dono escolhe/retira |
| Direitos | UUID de protocolo, tipo/escopo, descricao/resposta limitada, responsavel, prazo e evidencia | ops.privacy_requests/events | Titular proprio e equipe explicitamente designada PRIVACY_OFFICER |
| Conservacao | Chave pseudonima, motivo, evidencia, revisao/liberacao | ops.privacy_holds | Excecoes fundamentadas; bloqueio de medidas conflitantes |
| Reconciliacao | Tipo/ID/chave pseudonima, acao/escopo, data e assinatura | ops.privacy_effects/replayed_effects e copia externa a providenciar | Reaplicar eliminacoes, restricoes e revogacoes apos restaurar |
| Navegador cliente | Somente ID e JWT em sessionStorage; perfil em memoria | Aba atual; localStorage legado removido | Sessao/exibicao propria; limpa ao sair |
| Navegador staff | Tokens, IDs, papel, lojas e expiracao local de 8h | localStorage entre abas | Coordenacao de refresh; refresh nao estende limite local |
| Infra/fornecedores | Logs, IP, destinatario SMTP, backups e suporte | VPS, hosting PWA, SMTP, OAuth, push e CI | Regioes, contratos, acessos e retencao nao inspecionados |

## Pontos De Validacao

Para cada linha registrar: decisor, papel, finalidade especifica, dados minimos,
origem/destino, base e justificativa, prazo/gatilho/excecoes, acesso autorizado,
fornecedor/contrato/regiao, evidencia, dono e data de revisao. Nenhuma linha
recebe base universal de consentimento ou prazo arbitrario.

Nao ha campo dedicado de dado sensivel identificado; CPF e credenciais nao sao
automaticamente sensiveis no art. 11. Avaliar texto livre e inferencias pelo
contexto dos programas. Ausencia de campo de idade nao comprova ausencia de menores.

SDKs Google/Facebook so sao montados apos escolha do provedor. Foram removidas
fontes remotas Google. Nao foi encontrado codigo proprio de analytics na busca
em src/index/public; comportamento e cookies de terceiros ainda exigem verificacao.
Tokens sociais sao verificados, nao persistidos pelo fluxo da aplicacao.

Exportacao e por secao/cursor, ate 100 itens por pagina. Credenciais, chaves push,
dados de terceiros, arquivos de log e fornecedores ficam fora da entrega automatica.
Identificadores sociais e informacoes complementares devem ser avaliados no
atendimento verificado; nao se deve afirmar que o download contem absolutamente tudo.

Eliminacao local mantem tombstone inativo, chave pseudonima, protocolos, escolhas
e diario. **Nao e anonimizacao irreversivel.** Essas sobras continuam governadas
como dados pessoais. Exclusao por loja preserva perfil global e outras lojas.
Exclusao de conta invalida sessoes e remove dados locais cobertos, nao arquivos,
backups externos ou copias de fornecedores automaticamente.

Expiracao tecnica de token nao equivale a prazo legal de retencao. Nao existem
prazos aprovados nem descarte automatico de clientes/historico por inatividade.
Ver [governanca](GOVERNANCA_E_FORNECEDORES.md),
[retencao](RETENCAO_E_DIREITOS.md) e
[LGPD compilada](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm).
