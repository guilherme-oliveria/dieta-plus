# Backlog da release 1.4.0: melhorias vindas da comparação com o A Nutri

> 16/11/2026. Origem: [`analise-de-lacunas.md`](analise-de-lacunas.md) (IDs `L-xx`). Para o PO transformar em tarefas `BE-NN`, `MOB-NN` e `DES-NN` nos `plano-de-acao.md` de cada projeto.
> Marco sugerido: **M11 = 1.4.0** (a 1.3.0 já levou o M9 e o M10 para a `main`). O `CHANGELOG.md` do backend na `develop` ainda chama o M10 de "[1.4.0] em andamento"; acerte isso ao abrir a seção da 1.4.0.

## Como ler

- **Valor** para a nutri (ou para o paciente, que é o que faz a nutri renovar): Alto, Médio, Baixo.
- **Esforço** somando backend, mobile e design: **P** (um PR por projeto, poucos dias), **M** (contrato + 2 a 4 PRs), **G** (fase própria).
- **Prioridade** (MoSCoW): **Must** entra na 1.4.0; **Should** entra se couber; **Could** fica para a 1.5.0 ou depois.
- Cada história segue o fluxo de sempre: design no canvas antes da tela (DES), contrato primeiro (BE-Cn com JSON em `src/test/resources/contrato/`), backend uma onda à frente, mobile contra `MockEngine` até o endpoint chegar. Campos novos são opcionais (regra de contrato do `backend/AGENTS.md`).
- Regras novas sugeridas aparecem como `RN-XXX-nn (nova)`; o número final é dado no `regras-de-negocio.md` (cópia oficial no backend, espelho no mobile, no mesmo PR).

## Resumo priorizado

| # | História | Lacunas | Valor | Esforço | Prioridade | Onda |
| --- | --- | --- | --- | --- | --- | --- |
| H-01 | Alergias, intolerâncias e condições na ficha, com alerta no montador | L-06 | Alto | M | Must | 1 |
| H-02 | Consulta com status, modalidade, link e orientação ao paciente; lembrete configurável | L-16 | Alto | M | Must | 1 |
| H-03 | Diário da refeição: humor e "comi fora do plano" | L-19 | Alto | P | Must | 2 |
| H-04 | Metas de hábito do paciente com progresso diário e sequência | L-20 | Alto | M | Must | 2 |
| H-05 | Suplementos prescritos e orientações avulsas no app do paciente | L-14 | Alto | M | Must | 3 |
| H-06 | Documentos da nutri em PDF: atestado e declaração de comparecimento | L-15 | Médio | M | Must | 3 |
| H-07 | Planos: segundo nível, anual, comparar planos e contagem do teste | L-24 | Alto (negócio) | P/M | Must | 4 |
| H-08 | Relatórios da nutri por período | L-18 | Médio | M | Should | 4 |
| H-09 | Pedido de consulta pelo paciente | L-17 | Alto | G | Should | 5 |
| H-10 | Nota de evolução por consulta | L-09 | Médio | P | Should | 5 |
| H-11 | Tags, meta de peso e status "alta" no paciente | L-05 | Médio | P | Should | 5 |
| H-12 | Anexos do paciente (PDF de exame do laboratório) | L-08 | Médio | M | Could | — |
| H-13 | Importar pacientes de planilha (CSV) | L-04 | Médio | M | Could | — |
| H-14 | Central de ajuda (FAQ) no app | L-23 | Baixo | P | Could | — |
| H-15 | Módulos visíveis por paciente | L-03 | Baixo | P | Could | — |
| — | **MOB-29b** escrita offline de dietas, listas e modelos (já adiada para a 1.4.0 em 06/11) | — | Médio | M | Must | paralelo |

Ordem dentro da release: segurança e agenda primeiro (usadas em toda consulta), depois engajamento do paciente, depois documentos e prescrição, e o comercial por último para sair junto com a landing quando ela existir.

## Ondas sugeridas (continuação do `plano-de-execucao.md`)

| Onda | Backend | Mobile | Design | Integração ao fim |
| --- | --- | --- | --- | --- |
| 24 | BE-C9 contrato da 1.4.0 (H-01 a H-06) · H-01 · H-02 | MOB-29b · H-01 (mock) | DES ficha de saúde, consulta e lembretes | Alerta de alergia no montador |
| 25 | H-03 · H-04 | H-01 integra · H-02 · H-03 | DES diário da refeição e metas | Paciente registra humor e meta; nutri vê na adesão |
| 26 | H-05 · H-06 | H-04 · H-05 | DES suplementos, orientações e documentos | Paciente vê suplemento e orientação offline |
| 27 | H-07 · H-08 | H-06 · H-07 · H-08 | DES assinatura e relatórios | **Marco M11 = 1.4.0** (`release/1.4.0`) |
| 28 (se couber) | H-09 · H-10 · H-11 | H-09 · H-10 · H-11 | DES pedido de consulta | — |

Um contrato único (BE-C9) para H-01 a H-06 evita que o mobile espere a cada história. H-07 e H-08 podem ter contrato próprio, por serem independentes.

---

## H-01 Alergias, intolerâncias e condições na ficha, com alerta no montador

**Valor:** Alto (segurança do paciente). **Esforço:** M. **Prioridade:** Must.

**Problema.** Hoje a restrição só existe no alimento (marcadores do BE-51) e no modelo de dieta (RN-MOD-03). Se a nutri monta a dieta do zero para um paciente com alergia a glúten, nada avisa.

**História.** Como nutri, quero registrar na ficha as alergias, intolerâncias, condições e medicamentos do paciente, para que o montador me avise ou me impeça de incluir um alimento incompatível.

**Critérios de aceite.**
- [ ] A ficha tem a seção "Saúde e restrições" com itens de quatro tipos: alergia, intolerância, condição e medicamento. Cada item tem nome, gravidade opcional (leve, moderada, grave) e observação.
- [ ] Alergias e intolerâncias podem ser ligadas a um marcador de alimento (`CONTEM_LACTOSE`, `CONTEM_GLUTEN` etc.); a lista de marcadores pode crescer (ex.: `CONTEM_AMENDOIM`, `CONTEM_FRUTOS_DO_MAR`).
- [ ] O cabeçalho da ficha mostra um selo com as restrições ativas.
- [ ] No montador, adicionar um alimento ou produto com marcador ligado a uma **intolerância** mostra alerta e deixa seguir; ligado a uma **alergia** pede confirmação explícita da nutri e fica marcado no item.
- [ ] Ao criar uma dieta nova, as restrições da ficha entram como `restricoes` da dieta (reaproveita RN-MOD-03), e a lista de substituição publicada sai sem os alimentos marcados.
- [ ] Funciona offline no desktop/tablet (a ficha já é sincronizada).
- [ ] O paciente não vê essa seção no MVP (é dado clínico da nutri).

**Regras.** RN-PAC-nn (nova) restrições na ficha; RN-DIE-nn (nova) alerta e confirmação no montador; RN-MOD-03 (reuso).
**Contrato.** `restricoesSaude[]` opcional na ficha (`POST/PATCH /pacientes` e sync); `alertas[]` opcional no item da dieta.
**Divisão.** DES (seção da ficha e alerta no montador) · BE (changeset da tabela, validação, sync, teste de isolamento) · MOB (ficha, selo, alerta no montador, `dominio/` com a regra de alerta e teste).
**Risco.** A sugestão de marcadores por nome (G-15) erra; o texto do alerta deixa claro que a conduta é da nutri.

## H-02 Consulta com status, modalidade, link e orientação ao paciente; lembrete configurável

**Valor:** Alto (usada em toda consulta). **Esforço:** M. **Prioridade:** Must.

**Problema.** `TipoConsulta` mistura tipo e modalidade (`PRIMEIRA`, `RETORNO`, `ONLINE`), o status só tem `AGENDADA` e `CANCELADA`, e o comparecimento vive no financeiro privado. Não há onde colocar o link da consulta online nem um recado para o paciente.

**História.** Como nutri, quero marcar a consulta como realizada ou falta, separar tipo de modalidade, colocar o link da chamada e um recado para o paciente, e escolher quando o lembrete sai.

**Critérios de aceite.**
- [ ] Status da consulta: Agendada, Realizada, Falta, Cancelada. Marcar Realizada ou Falta atualiza o comparecimento do financeiro (uma fonte só; RN-FIN-01 continua valendo).
- [ ] Modalidade: Presencial ou Online, separada do tipo (Primeira, Retorno, Avaliação). O valor `ONLINE` antigo vira tipo Retorno + modalidade Online na migração, sem quebrar o app antigo (enum desconhecido tratado).
- [ ] Consulta online aceita um link (Meet, Zoom, WhatsApp); o paciente vê o botão "Entrar na consulta" na próxima consulta e no lembrete.
- [ ] Campo "orientação para o paciente" (até 2.000 caracteres) aparece para o paciente; "observação interna" não aparece.
- [ ] Na conta da nutri: antecedência do lembrete (2 h, 1 dia ou 2 dias; padrão 1 dia) e lembrete extra 1 h antes, ligado ou não. O e-mail de confirmação sai ao agendar.
- [ ] Agenda do mês (visão de calendário) além de dia e semana; tocar num horário vazio abre a nova consulta com data e hora preenchidas.
- [ ] Push continua sem dado clínico (RN-NOT-02).

**Regras.** RN-AGE-01 e RN-AGE-02 (atualizar); RN-AGE-nn (nova) status e modalidade.
**Contrato.** `status` ganha `REALIZADA` e `FALTA`; `modalidade`, `linkOnline`, `orientacaoPaciente` e `observacaoInterna` opcionais; preferência de lembrete na conta da nutri.
**Divisão.** DES (formulário da consulta e visão do mês) · BE (changeset, migração do enum, jobs de lembrete) · MOB (agenda, formulário, próxima consulta do paciente).

## H-03 Diário da refeição: humor e "comi fora do plano"

**Valor:** Alto (a nutri entende por que a adesão caiu). **Esforço:** P. **Prioridade:** Must.

**Problema.** O check-in tem só Comi/Troquei/Pulei. A nutri não sabe como o paciente se sentiu nem o que ele comeu fora do plano.

**História.** Como paciente, quero dizer como me senti antes e depois da refeição e registrar o que comi fora do plano, para minha nutri ajustar a dieta comigo.

**Critérios de aceite.**
- [ ] No registro da refeição, escala opcional de 5 níveis para "fome antes" e "como ficou depois" (rótulos e ícones próprios do Dieta+).
- [ ] Botão "Comi algo fora do plano" no dia: texto curto, refeição opcional e horário.
- [ ] Funciona offline; entra pela mesma regra de inclusão dos check-ins (RN-ROT-02, sem conflito).
- [ ] A nutri vê, na adesão do paciente, a média de humor da semana e a lista de registros fora do plano.
- [ ] O paciente pode desligar a pergunta de humor no perfil.

**Regras.** RN-ROT-01 (ampliar); RN-ROT-nn (nova) registro fora do plano.
**Contrato.** `fomeAntes` e `sensacaoDepois` (1 a 5) opcionais no check-in; coleção de sync `registrosForaDoPlano`.
**Divisão.** DES · BE · MOB.

## H-04 Metas de hábito do paciente com progresso diário e sequência

**Valor:** Alto (motivo para o paciente abrir o app todo dia). **Esforço:** M. **Prioridade:** Must.

**Problema.** A única meta é água. O concorrente usa metas de hábito com sequência e níveis para reter o paciente.

**História.** Como nutri, quero dar metas de hábito ao paciente (caminhar, dormir, comer frutas e verduras, um contador livre), com alvo diário ou semanal, e ver o progresso; como paciente, quero marcar a meta do dia e ver minha sequência.

**Critérios de aceite.**
- [ ] Tipos de meta: água (a atual), passos ou minutos de atividade, horas de sono, porções de frutas e verduras, sim/não livre ("não tomei refrigerante") e contador livre com unidade.
- [ ] Cada meta tem alvo, frequência (diária ou N vezes por semana), início e fim opcionais.
- [ ] Modelos de metas da nutri, aplicáveis a vários pacientes.
- [ ] Paciente marca no Hoje; vê sequência de dias e o progresso da semana. Funciona offline (regra de inclusão).
- [ ] Lembrete local opcional por meta, respeitando o horário de silêncio (RN-NOT-03).
- [ ] Nutri vê o cumprimento das metas na ficha e no painel ("metas abaixo de 50% na semana").
- [ ] Sem pontos nem níveis na 1.4.0: sequência e progresso bastam. Gamificação maior fica para depois, se o uso mostrar valor.

**Regras.** RN-ROT-03 (água vira um tipo de meta, com a mesma meta padrão); RN-MET-nn (nova) metas de hábito.
**Contrato.** Coleções de sync `metas` (da nutri) e `registrosMeta` (do paciente, só inclusão).
**Divisão.** DES (Hoje com metas, cadastro de meta) · BE · MOB.

## H-05 Suplementos prescritos e orientações avulsas no app do paciente

**Valor:** Alto. **Esforço:** M. **Prioridade:** Must.

**Problema.** A nutri prescreve suplemento e dá orientações fora da dieta (hidratação, como montar o prato) e hoje faz isso fora do app, no PDF ou no WhatsApp.

**História.** Como nutri, quero prescrever suplementos (nome, dose, forma, frequência, horário, duração) e enviar orientações avulsas; como paciente, quero ver isso no app, offline, e receber lembrete do suplemento.

**Critérios de aceite.**
- [ ] Prescrição de suplemento a partir dos produtos da nutri (BE-48b) ou por texto livre; dose, forma, frequência, horário, data de início e fim.
- [ ] Orientação avulsa: título e texto formatado simples (negrito, lista), com modelos reutilizáveis da nutri.
- [ ] O paciente vê "Suplementos" e "Orientações" no app, offline; lembrete local do suplemento no horário (pode desligar).
- [ ] Push "Você tem uma nova orientação" sem dado clínico (RN-NOT-01 ganha o evento).
- [ ] O PDF da dieta pode incluir os suplementos ativos.

**Regras.** RN-SUP-nn (nova); RN-NOT-01 (novo evento).
**Contrato.** Coleções de sync `prescricoesSuplemento` e `orientacoes`; modelos de orientação da nutri.
**Divisão.** DES · BE · MOB.

## H-06 Documentos da nutri em PDF: atestado e declaração de comparecimento

**Valor:** Médio (a nutri faz isso toda semana, hoje no Word). **Esforço:** M. **Prioridade:** Must.

**História.** Como nutri, quero emitir atestado e declaração de comparecimento em PDF com meu nome, CRN e logo, a partir de modelos, e compartilhar com o paciente.

**Critérios de aceite.**
- [ ] Modelos de documento da nutri com campos de preenchimento (nome do paciente, data, horário da consulta, CRN). Dois modelos do sistema: atestado e declaração de comparecimento.
- [ ] PDF pelo mesmo gerador e link assinado de 10 minutos dos outros PDFs (BE-28).
- [ ] Cada documento tem um código de conferência impresso; uma rota pública só de leitura confirma que o documento existe, quem emitiu, o CRN, a data e as **iniciais** do paciente, nunca o conteúdo clínico.
- [ ] O paciente vê os documentos emitidos para ele em "Documentos".
- [ ] Assinatura digitalizada da nutri (imagem) opcional; depende da MOB-33b (envio da logo) usar o mesmo caminho de upload.

**Regras.** RN-DOC-nn (nova); auditoria (BE-05) registra emissão e conferência.
**Divisão.** DES · BE (modelos, PDF, rota pública com limite de requisições do BE-31) · MOB.
**Depende de:** MOB-33b para logo e assinatura.

## H-07 Planos: segundo nível, anual, comparar planos e contagem do teste

**Valor:** Alto para o negócio. **Esforço:** P/M. **Prioridade:** Must. **Decisão do Guilherme:** preços, limites e o limite do Teste (ver `analise-de-lacunas.md`, 4.3).

**Critérios de aceite.**
- [ ] A tabela `plano` ganha periodicidade (mensal ou anual) e preço anual; um segundo nível pago é cadastrado por changeset com os valores aprovados.
- [ ] Endpoint público `GET /api/v1/planos` (só os planos à venda, sem dado de usuário) para o app e para a futura landing.
- [ ] Checkout do Asaas para o anual, continuando fora do app (RN-ASS-06); webhook trata a renovação anual (RN-ASS-07).
- [ ] Tela de assinatura: comparar planos com alternância mensal/anual, uso de cada limite, "faltam N dias de teste" e botão de assinar que abre o navegador.
- [ ] Aviso no painel e por e-mail 7 dias e 1 dia antes do fim do teste.
- [ ] Downgrade segue RN-ASS-08.

**Regras.** RN-ASS-01, 02, 06, 07 e 08 (atualizar); S1 e S3 em `visao-geral.md`.
**Divisão.** BE (changeset, endpoint público, gateway, e-mails) · MOB (tela de assinatura) · DES.

## H-08 Relatórios da nutri por período

**Valor:** Médio. **Esforço:** M. **Prioridade:** Should.

**Critérios de aceite.**
- [ ] Período: 7, 30, 90 dias, 12 meses ou personalizado.
- [ ] Indicadores: pacientes ativos e novos, consultas por status (realizada, falta, cancelada), taxa de falta, receita recebida e a receber por forma de pagamento, adesão média dos pacientes e metas cumpridas.
- [ ] Gráficos com a biblioteca do MOB-47 e `mobile/docs/design/graficos.md`.
- [ ] Exportar a tabela em CSV.
- [ ] Só dados da própria nutri; calculado no backend (o app não soma dado clínico de outros).
**Depende de:** H-02 (status), H-04 (metas). **Divisão.** DES · BE · MOB.

## H-09 Pedido de consulta pelo paciente (Should)

O paciente pede uma consulta pelo app escolhendo dia e período (manhã, tarde, noite); a nutri aceita (vira consulta) ou recusa com recado. Horários de atendimento da nutri, antecedência mínima e prazo máximo configuráveis. A escolha de horário livre direto na agenda fica para depois. **Esforço:** G (horários de atendimento, solicitações, notificações dos dois lados). Precisa de internet (entra na lista de ações online do `integracao.md`).

## H-10 Nota de evolução por consulta (Should)

Nota estruturada por consulta (subjetivo, avaliação, conduta, monitoramento), com rascunho e "finalizar" que a torna só leitura, com adendo depois. Só da nutri. **Esforço:** P. **Depende de:** H-02.

## H-11 Tags, meta de peso e status "alta" (Should)

Tags livres no paciente (filtro na lista), meta de peso com direção (perder, manter, ganhar) mostrada na evolução, e "alta" como motivo de encerramento do vínculo (RN-VIN-05 ganha o motivo, sem mudar o fluxo). Responsável legal opcional para menor de idade. **Esforço:** P.

## H-12 a H-15 (Could)

- **H-12 Anexos do paciente:** a nutri anexa o PDF do laboratório ao exame ou à ficha; armazenamento S3 já existe para PDFs; limite de tamanho por arquivo e total por plano. Sem leitura automática por IA.
- **H-13 Importar pacientes de CSV:** modelo de planilha, prévia com erros por linha, e "desfazer importação" por 24 h. Ajuda a trazer a carteira de outro sistema.
- **H-14 Central de ajuda:** perguntas frequentes estáticas no app e link de contato; mesmo conteúdo serve à landing.
- **H-15 Módulos por paciente:** a nutri esconde do app do paciente seções que ele não usa (lista de compras, metas, exames).

## Fora da 1.4.0

Assistente de IA, teleconsulta com vídeo próprio, equipe e recepção, site da clínica e diretório, fotos de evolução e do prato, receitas, catálogo TBCA e rótulos, verificação profissional. Motivos em `analise-de-lacunas.md`, seção 6.

## Tarefas do Guilherme ligadas a esta release

| ID | O quê | Bloqueia |
| --- | --- | --- |
| G-21 | Preços e limites do segundo nível, do anual e do Teste | H-07 |
| G-22 | Onde fica a landing page (recomendação: `site/` no monorepo) | Landing (futura `SITE-01`) |
| G-23 | Fotos de evolução e do prato entram no roadmap? | L-10 |
| G-24 | Assistente de IA vira plano próprio? | L-21 |

Os números `G-21` a `G-24` seguem o último usado (G-20); confirme ao passar para o `plano-de-execucao.md`.
