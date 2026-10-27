# API De Privacidade

Referencia: 2026-10-26. Fonte executavel: controllers/PrivacyController,
PrivacyRetentionController e NotificationsController, DTOs PrivacyDtos e
Springdoc em desenvolvimento. OpenAPI/Swagger continuam desligados em producao.

Autenticacao Bearer obrigatoria, salvo GET /api/privacy/notice.
401 indica sessao ausente/invalida; 403, permissao/escopo; protocolo de outro
titular retorna 404. Respostas de dados proprios usam Cache-Control: no-store.
Nao colocar tokens em URLs, capturas, logs ou exemplos reais.

## Titular Autenticado

| Metodo/rota | Entrada | Resultado |
|---|---|---|
| GET /api/privacy/notice | Publico | published/version, identificacao, contato, texto e decisoes pendentes |
| GET /api/privacy/me/profile | Identidade do JWT | Perfil minimo proprio, sem credenciais |
| PATCH /api/privacy/me/profile | name <=120 nao vazio, phone opcional <=30 | Somente cliente, correcao propria; campos extras rejeitados |
| GET /api/privacy/me/export | section, after >=0, limit 1..100 | items, nextAfterId e redactions; cursor nulo encerra secao |
| POST /api/privacy/me/requests | type, scope, merchantId apenas MERCHANT, details <=1000 | 201, protocolo UUID/status/datas; maximo 5 abertos |
| GET /api/privacy/me/requests | page >=0, size 1..100 | Lista propria, pagina padrao 50 |
| GET /api/privacy/me/requests/{id} | UUID | Apenas protocolo proprio |
| GET /api/notifications/preferences | Cliente autenticado | Escolha LOYALTY_PUSH e versao |
| PUT /api/notifications/preferences | enabled, noticeVersion ao ativar | Ativacao exige aviso publicado atual; rascunho retorna 409 |
| DELETE /api/notifications/subscriptions | Cliente autenticado | 204, retira escolha e remove todas as inscricoes proprias |
| POST /api/notifications/unsubscribe-device | endpoint <=2048 | 204 idempotente, remove somente inscricao desse cliente/dispositivo |
| POST /api/notifications/subscribe | endpoint e keys validos, customerId opcional deve coincidir | 204, exige escolha ativa e aviso publicado |

Secoes de exportacao: profile, cards, stamps, rewards, sessions, recovery, push,
preferences, requests, audit, privacy-events. Nao presumir que uma pagina
encerra todas as secoes. Identificadores sociais e dados externos requerem
analise no protocolo; arquivos externos e contatos de terceiros nao sao
entregues indiscriminadamente. Limites de requests/exportacao/correcao podem
retornar 429. O PWA baixa paginas separadas, explicitamente rotuladas.

Exemplo sintetico de pedido, sem identidade escolhida no body:

~~~json
{"type":"ERASURE","scope":"MERCHANT","merchantId":123,"details":"Solicito analise do meu relacionamento com esta loja."}
~~~

Tipos: ACCESS, CORRECTION, RESTRICTION, ERASURE, ANONYMIZATION, PORTABILITY,
SHARING_INFORMATION, CONSENT_WITHDRAWAL, OTHER. Aceitar um protocolo nao
promete atendimento automatico nem substitui a analise do direito.

## Equipe Explicitamente Designada

Exige authority PRIVACY_OFFICER derivada do banco a cada request; ADMIN ou
PLATFORM_ADMIN isolados nao autorizam. Concessao fora da API, com evidencia
da nomeacao. Revogar a flag bloqueia o token existente na proxima request.

| Metodo/rota | Contrato |
|---|---|
| GET /api/privacy/admin/requests | Fila paginada page/size, max100 |
| GET /api/privacy/admin/requests/{id}/preview | Impacto, escopo, subjectType/key pseudonimos, hold e disponibilidade da acao; nenhuma mutacao |
| POST /api/privacy/admin/requests/{id}/review | CLAIM, RESPOND, REJECT, RESTRICT, ERASE, COMPLETE; responsavel e transicoes validados |
| GET/POST /api/privacy/admin/holds | Consulta limitada/criacao: subjectType/key, reasonCode, evidenceRef e reviewAt futura |
| POST /api/privacy/admin/holds/{id}/release | evidenceRef obrigatoria, registra quem/quando liberou |
| POST /api/privacy/admin/retention/preview | Simulacao; sem politica retorna POLICY_UNDEFINED por categoria |
| POST /api/privacy/admin/retention/run | Exige habilitacao e aprovacao separadas, do contrario 503 |
| GET /api/privacy/admin/restoration-journal | Cursor after, limit1..100; entradas assinadas; 503 sem chave/origem externas configuradas |

CLAIM pode informar dueAt futura; omitir os demais campos vazios. Outras
acoes exigem response <=2000 e evidenceRef opaca [A-Za-z0-9._:/-] <=200.
Nao enviar e-mail/documento como evidenceRef. Para acao destrutiva, confirmation
deve coincidir exatamente com operacao+escopo, e politica aprovada e ausencia
de hold continuam obrigatorias. Status final APPLIED_LOCALLY nao e COMPLETE.
Reassumir protocolo nao rebaixa o status para repetir exclusao.

Exemplos sinteticos:

~~~json
{"action":"CLAIM","dueAt":"2030-01-01T12:00:00Z"}
~~~

~~~json
{"action":"ERASE","confirmation":"ERASE_ACCOUNT","response":"Resposta revisada ao titular.","evidenceRef":"DECISAO-TESTE-01"}
~~~

O segundo exemplo retorna 503 na configuracao entregue, intencionalmente.
Nao remover bloqueios para fazer demonstracao com dados reais.

## Verificacao

Testes de backend exercitam filtros, migracao, SQL real e isolamento em
PostgreSQL temporario. Smoke do PWA em tests/privacy-browser-smoke.mjs usa
Playwright/Edge headless, API inteiramente simulada e bloqueia acesso externo.
Isso verifica interacao/contrato da interface, nao OAuth, SMTP, push reais
nem a operacao de producao.
