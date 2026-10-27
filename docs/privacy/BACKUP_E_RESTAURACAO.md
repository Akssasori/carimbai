# Backup E Reconciliacao De Privacidade

**Procedimento a implantar e exercitar.** Nao foi acessada VPS, backup real ou
armazenamento externo. Testes locais de banco/assinatura nao comprovam RPO/RTO,
criptografia, completude de copias nem recuperacao real.

## Diario Fora Do Backup

ops.privacy_effects registra eliminacao, restricao, retirada de push e invalidacao
de sessoes. Exportacao exclusiva de PRIVACY_OFFICER:
GET /api/privacy/admin/restoration-journal?after=0&limit=100.
Paginar pelo ultimo id ate obter pagina vazia. O diario contem dados pseudonimos,
nao anonimos. Nao publicar nem commitar JWT, diario, chaves ou dados de titulares.

Configurar CARIMBAI_PRIVACY_JOURNAL_ORIGIN estavel e chave exclusiva
CARIMBAI_PRIVACY_JOURNAL_SECRET de alta entropia (minimo tecnico 32 caracteres).
Nao reutilizar JWT/HMAC QR nem guardar chave de assinatura junto ao backup.
Cada entrada exportada tem HMAC-SHA256. Assinatura protege contra alteracao,
mas nao prova que o operador exportou todas as paginas.

Providenciar captura duravel, copia externa criptografada, acesso restrito,
controle de versoes/imutabilidade, monitoramento de atraso e manifestacao
independente do numero de entradas e ultimo ID. A entrega NAO configura esse
servico externo nem garante janela zero entre transacao e captura. Aprovar RPO
e plano para reconciliar qualquer lacuna antes de liberar atendimento.
Guardar manifesto de contagem/high-water mark em canal confiavel separado do
arquivo; ele nao deve ser gerado a partir de um arquivo possivelmente truncado.

## Restauracao Isolada

1. Manter origem offline quando necessario, preservar evidencia e identificar
   ponto do snapshot, versao de schema e janela de perda/recuperacao.
2. Restaurar em ambiente isolado, sem entrada publica, SMTP, OAuth ou push.
   CARIMBAI_RESTORATION_QUARANTINE=true devolve 503 em rotas da API, inclusive
   login; apenas /actuator/health passa para verificacao de saude.
   Quarentena HTTP nao substitui firewall/isolamento de saida.
3. Validar schema/migracoes e compatibilidade da chave privacy_key. Snapshots
   anteriores a esta migracao nao preservam essas chaves: o replay automatico
   falha fechado e exige reconciliacao/mapeamento verificado, nao troca de UUID
   para contornar a validacao.
4. Obter diario externo atualizado, completo e assinatura/chave correta.
   Converter a lista paginada para NDJSON: um JournalEntry completo por linha,
   IDs estritamente crescentes, sem alterar campos ou assinatura.
5. Informar caminho local, contagem e ultimo ID provenientes do manifesto.
   Executar primeiro em simulacao. A CLI recusa modo web, falta de quarentena,
   chave/origem, manifesto, linha acima de 32768 bytes, identidade/signatura
   invalida, ordem incorreta ou completude divergente.

Exemplo estrutural, nao executar em producao sem revisao:

~~~text
java -jar carimbai.jar --spring.profiles.active=prod,privacy-restore \
  --spring.main.web-application-type=none \
  --carimbai.privacy.restore-journal-path=C:/restore/journal.ndjson \
  --carimbai.privacy.restore-expected-entries=CONTAGEM_VERIFICADA \
  --carimbai.privacy.restore-expected-last-id=ULTIMO_ID_VERIFICADO \
  --carimbai.privacy.restore-dry-run=true
~~~

Segredos e dados de conexao por ambiente/gestor seguro, nunca na linha de comando.
O profile privacy-restore nao cria administrador de bootstrap. Job de retencao
nao executa durante a quarentena.

6. Corrigir inconsistencias e holds antes da execucao real. Aprovacao real exige
   destructive-actions-approved, destructive-approval-ref e restore-dry-run=false.
   Cada efeito e transacional/idempotente por origem+ID+digest; falha intermediaria
   pode deixar prefixo aplicado, com retomada segura, sempre em quarentena.
   Completude divergente nao deve ser interpretada como sucesso parcial liberavel.
7. Replay compara UUID e ID para nao atingir outra pessoa apos reuso de IDs.
   Revogacoes incrementam versao e invalidam refresh/reset de staff; push retirado
   e eliminado sem substituir escolha posterior valida. IDs do diario sao
   rebaseados para evitar reutilizacao do high-water mark restaurado.
8. Conferir novamente eliminacoes, restricoes, holds, preferencias e tokens.
   A remocao de um dispositivo pelo navegador revoga o endpoint no provedor;
   avaliar e purgar/revalidar todas as inscricoes restauradas sem prova atual.
9. Revisar manualmente alteracoes pos-snapshot nao representadas nesse diario:
   papeis/vinculos de staff, nomeacoes, rotacao de senha/segredos, correcoes,
   provedores e conservacoes novas/liberadas. O diario nao cobre qualquer mudanca
   possivel do sistema e NAO autoriza restaurar privilegios antigos.
10. Executar testes negativos com tokens antigos, titulares diferentes e dados
    eliminados, validar resposta/impacto externo e obter liberacao documentada.
    Somente entao retirar quarentena e permitir trafego.

## Evidencia Do Exercicio A Preencher

Data, responsavel/substituto, autorizador, snapshot/schema, fonte do diario,
manifesto verificado, chaves referenciadas sem conteudo, simulacao, contagens,
acoes aplicadas/ignoradas, falhas/retomada, pedidos externos, testes negativos,
RPO/RTO medidos, risco residual e liberacao. Nada disso foi presumido como
executado apenas pela existencia deste documento.
