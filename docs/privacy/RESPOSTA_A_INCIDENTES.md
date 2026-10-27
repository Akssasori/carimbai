# Resposta A Incidentes De Dados

**Runbook proposto; equipe, contratos e exercicio ainda nao comprovados.**
2026-10-26. Coordenador, substituto, juridico, atendimento e infraestrutura:
A DESIGNAR, com contatos de emergencia em repositorio restrito.

## Acionamento E Contencao

Registrar identificador, descoberta e conhecimento do controlador com data/hora,
fonte, sistemas afetados, categorias/volume estimado e incertezas. Preservar
evidencias com integridade e acesso minimo; nao copiar banco real para Git/chat.
Conter acesso indevido, revogar sessoes/chaves pertinentes, isolar componentes,
acionar fornecedores e preservar continuidade de forma proporcional.
Nunca apagar provas automaticamente para "corrigir" o incidente.

## Avaliacao E Comunicacao

Avaliar risco/dano relevante e criterios do regulamento; registrar a decisao,
inclusive motivo para nao comunicar. Nao esperar conclusao forense para iniciar
o controle de prazo. A regra geral da Res. 15/2024 e comunicar ANPD e titulares
em tres dias uteis do conhecimento de que dados pessoais foram afetados,
ressalvada legislacao especifica. Complementacao fundamentada a ANPD: ate
vinte dias uteis da comunicacao. Pequeno porte/prazos diferenciados exigem
enquadramento; nao dobrar automaticamente. Manter registro de incidentes,
inclusive nao comunicados, por pelo menos cinco anos, salvo obrigacao maior.
Fonte: [Resolucao CD/ANPD 15/2024](https://bibliotecadigital.mj.gov.br/bitstream/1/12879/2/RES_ANPD_2024_15.html).

A equipe juridica deve confirmar regras aplicaveis e preencher calendario de
dias uteis. Usar [canal oficial de comunicacao de incidentes](https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/comunicado-de-incidente-de-seguranca-cis).
O codigo nao envia comunicacao regulatoria automaticamente.

## Modelos Restritos A Preencher

- Registro interno: ID, datas de descoberta/conhecimento, responsavel, fatos,
  sistemas, titulares/categorias, volume/alcance, causa conhecida/incerta,
  risco e danos possiveis, medidas, fornecedores, decisao e aprovador.
- Comunicacao: identificacao real/controlador e canal, natureza/categorias,
  momento/alcance, riscos, medidas e orientacoes protetivas, contato e
  informacoes pendentes. Nao divulgar credenciais, nomes de vitimas ou detalhes
  exploraveis desnecessarios.
- Evidencia de envio: protocolo ANPD, copia/destinatarios/canal/data e comprovacao
  da comunicacao aos titulares, complementacoes e obrigacoes subsequentes.
- Encerramento: causa, medidas corretivas, testes negativos, revisao de risco,
  resposta a titulares, pendencias, dono/prazo e retencao restrita do registro.

## Exercicio De Mesa Pendente

Cenario sintetico: credencial staff comprometida, consulta indevida de um titular,
necessidade de revogar refresh/JWT e restaurar snapshot anterior a uma exclusao.
Executar com dados ficticios e medir quem percebe/aciona/contem, como calcula
prazos, decide comunicacao, preserva provas e impede retorno dos dados excluidos.
Registrar tempos/falhas e repetir apos correcoes. Este cenario nao comprova que
o exercicio ocorreu.
