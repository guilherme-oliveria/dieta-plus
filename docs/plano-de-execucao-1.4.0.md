# Dieta+: plano de execução da release 1.4.0

> Criado em 16/11/2026 pelo PO, a pedido do Guilherme. Documento comum aos dois projetos, por isso fica na raiz (`docs/`).
> Origem: [`benchmark-anutri/backlog-1.4.0.md`](benchmark-anutri/backlog-1.4.0.md) (histórias `H-xx`) e [`benchmark-anutri/analise-de-lacunas.md`](benchmark-anutri/analise-de-lacunas.md) (lacunas `L-xx`), cruzados com o código da `develop` em 16/11/2026 (versão 1.3.1, backend até BE-67 e changeset 056, app até MOB-79).
> **Status: aprovado e liberado pelo Guilherme em 16/11/2026.** As decisões D-03 a D-12 foram aprovadas como recomendadas, com duas mudanças: a água vira um tipo de meta (D-07) e a assinatura digitalizada fica marcada para a 1.5.0 (D-05). Ver a seção 7.2. Os valores dos planos (G-21) também foram decididos: o Básico continua e entram dois níveis novos, todos com anual (seção 7.1). As tarefas estão nos `plano-de-acao.md` de `backend/` (Fase 9) e de `mobile/` (Fase 10).
> Continua o [`backend/docs/arquitetura/plano-de-execucao.md`](../backend/docs/arquitetura/plano-de-execucao.md) (ondas 0 a 23). Aqui estão as **ondas 24 a 27** e o **marco M11 = 1.4.0**.

O benchmark é referência de funcionalidade e de fluxo. Textos, nomes de plano, marca, cores, ícones e layout do concorrente não são copiados: telas novas seguem o canvas e `mobile/docs/design/`.

## 1. Escopo

### Entra na 1.4.0

| # | História | Lacuna | Esforço | Seção |
| --- | --- | --- | --- | --- |
| H-01 | Alergias e intolerâncias na ficha, com alerta no montador | L-06 | M | 3.1 |
| H-02 | Consulta com status, modalidade e link online; lembrete configurável | L-16 | M/G | 3.2 |
| H-03 | Humor e "comi fora do plano" no registro da refeição | L-19 | P/M | 3.3 |
| H-04 | Metas de hábito para o paciente | L-20 | M/G | 3.4 |
| H-05 | Suplementos e orientações no app do paciente | L-14 | M | 3.5 |
| H-06 | Atestado e declaração de comparecimento em PDF | L-15 | M | 3.6 |
| H-07 | Plano anual e segundo nível de plano | L-24 | M/G | 3.7 |
| MOB-29b | Escrita offline de dietas, listas e modelos (adiada para a 1.4.0 em 06/11) | — | G | 3.8 |

### Fica fora

- **Assistente de IA, teleconsulta com vídeo próprio, site da clínica e diretório, equipe e recepção.** Motivos em `analise-de-lacunas.md`, seção 6.
- **Landing page.** Não entra. Fica registrado só o que já foi decidido:
  - **G-22 (decidido):** a landing vai morar na pasta `site/` deste monorepo (estática, mesmo Gitflow e QA, filtro `site/**` no `_mudancas.yml`, tag `site-vX.Y.Z`), com preços lidos de `GET /api/v1/planos`. A tarefa `SITE-01` é futura.
  - **Pré-requisito:** finalizar os **termos de uso** (`backend/docs/legal/terms-of-use.md`, hoje 1.0.0 RASCUNHO) e a **política de privacidade**. Sem os dois aprovados e publicados não há landing, e o cadastro continua bloqueado (BE-36, RN-CON-09). Também dependem do Guilherme o domínio e a VPS (BE-32b) e a conta real do Asaas.
  - A única entrega da 1.4.0 que serve à landing é o endpoint público de planos (BE-80).
- **Histórias Should e Could do backlog (H-08 a H-15)**: relatórios, pedido de consulta, nota de evolução, tags e alta, anexos, importar CSV, ajuda e módulos por paciente. Candidatas à 1.5.0. A 1.4.0 já deixa prontos os dados de que H-08 e H-10 dependem (status da consulta e metas).
- **Pendências antigas que não são desta release:** MOB-18b, MOB-27b, MOB-31b, MOB-33b, MOB-35a e BE-32b seguem como estão nos planos de ação.

## 2. Como o trabalho anda nesta release

Vale tudo que já está no `AGENTS.md` da raiz e nos de `backend/` e `mobile/`. O que importa aqui:

| Assunto | Regra |
| --- | --- |
| Branches | `feature/BE-NN-descricao`, `feature/MOB-NN-descricao` e `feature/DES-NN-descricao`, saindo da `develop` e voltando para a `develop` por PR. Correção depois do corte: `bugfix/<desc>` saindo de `release/1.4.0`. Nunca `claude/...` como branch de entrega. |
| Um projeto por PR | Backend e app em PRs separados. Contrato: o backend vai primeiro; o app cita o PR do backend. |
| Contrato primeiro | BE-C9 e BE-C10 publicam records, controllers `501`, `CodigoErro` novos e os JSONs em `backend/src/test/resources/contrato/`. O app começa contra `MockEngine` assim que o contrato está na `develop`. |
| Design antes da tela | Nenhuma tela nova sem artboard aprovado no canvas (`DES-NN`), copiado para `mobile/docs/design/artboards/` e registrado em `telas.md`. |
| QA | O agente de código abre o PR e nunca aprova. O QA do monorepo revisa contra este plano, publica `QA: APROVADO` ou `QA: CORRIGIR` e faz o merge na `develop`. Próxima tarefa só depois do veredito (pode adiantar o que não depende do PR aberto). |
| Changesets | Próximo número livre **no momento do PR** (o último na `develop` é o 056). Dois PRs abertos com o mesmo número: quem entrar depois renumera. Changeset já mesclado nunca é editado. |
| Docs comuns | `regras-de-negocio.md`, `integracao.md` e `funcionalidades-mvp.md` mudam em `backend/docs/arquitetura/` e na cópia de `mobile/docs/arquitetura/` **no mesmo PR** (exceção de raiz já prevista no `AGENTS.md`). |
| Release | `develop` → `release/1.4.0` → `main`. O merge na `main` e a tag `v1.4.0` são só do Guilherme. O back-merge é da pipeline. |

Numeração usada neste plano (continua a última de cada série): backend `BE-68` a `BE-86` e contratos `BE-C9` e `BE-C10`; app `MOB-81` a `MOB-99`, mais `MOB-29b`, `MOB-29c` e `MOB-29d`; design `DES-16` a `DES-22`; tarefas do Guilherme `G-21` a `G-28`. O `MOB-80` foi pulado de propósito, porque já foi usado num PR de estabilização de teste. Os IDs de regra (`RN-…`) são propostos; o número final é dado no PR do BE-C9.

## 3. Histórias em detalhe

Cada história traz objetivo, critérios de aceite, impacto no backend e no app, mudanças de dados e de API, e dependências. Os conflitos com a arquitetura atual estão resumidos na seção 6 (`C-xx`) e as decisões em aberto na seção 7 (`D-xx`).

### 3.1 H-01 Alergias e intolerâncias na ficha, com alerta no montador

**Objetivo.** Dar segurança alimentar: a nutri registra as restrições de saúde do paciente uma vez, na ficha, e o montador de dieta avisa (intolerância) ou exige confirmação (alergia) ao incluir um alimento incompatível. Hoje a restrição só existe no alimento (marcadores do BE-51) e no modelo de dieta (RN-MOD-03).

**Critérios de aceite.**
- [ ] A ficha tem a seção "Saúde e restrições", com itens de quatro tipos: alergia, intolerância, condição e medicamento. Cada item tem nome, gravidade opcional (leve, moderada, grave) e observação.
- [ ] Alergia e intolerância podem ser ligadas a um ou mais marcadores de alimento. A lista de marcadores cresce (amendoim, frutos do mar, ovo, soja, por exemplo), com sugestão inicial por nome no catálogo.
- [ ] O cabeçalho da ficha mostra um selo com as restrições ativas.
- [ ] No montador, incluir alimento ou produto com marcador ligado a uma **intolerância** mostra o alerta e deixa seguir. Ligado a uma **alergia**, pede confirmação explícita, e o item fica marcado como confirmado.
- [ ] O backend recalcula os alertas ao salvar e **recusa publicar** dieta com item de alergia sem confirmação (código novo). O app nunca é a garantia.
- [ ] Ao criar uma dieta nova, as restrições da ficha entram como `restricoes` da dieta, e a lista de substituição publicada sai sem os alimentos marcados (reuso da RN-MOD-03).
- [ ] Funciona offline no desktop e no tablet: a ficha já é sincronizada com escrita offline (MOB-29) e a regra de alerta roda no `dominio/` do app, com vetores de teste iguais aos do backend.
- [ ] O paciente não vê a seção (dado clínico da nutri). Gravidade, condições e medicamentos nunca saem na projeção do paciente.
- [ ] O texto do alerta deixa claro que a conduta é da nutri (o marcador é sugestão).

**Regras.** `RN-SAU-01` (itens de saúde na ficha), `RN-SAU-02` (ligação com marcadores), `RN-DIE-14` (alerta e confirmação no montador, publicação recusada sem confirmação), RN-MOD-03 (reuso).

| | Mudança |
| --- | --- |
| Backend | Módulo `pacientes`: itens de saúde na ficha, validação, sync da ficha, isolamento. Módulo `alimentos`: marcadores novos e sugestão por nome. Módulo `dietas`: cálculo de `alertas[]`, confirmação por item, pré-preenchimento de `restricoes`, recusa na publicação. |
| App | `ui/nutri/paciente` (seção e selo), `ui/nutri/dieta` (alerta e confirmação), `dominio/dietas` (regra pura com teste). |
| Dados | Tabela `restricao_saude` (ficha, tipo, nome, gravidade, observação, marcadores) ou coluna JSON na ficha, a decidir no BE-C9 pelo padrão do `cadastro_ampliado`. Marcadores novos no catálogo e nas cópias das nutris. Coluna de confirmação no item da refeição. |
| API | `restricoesSaude[]` opcional em `POST/PATCH /pacientes` e na coleção `pacientes` do sync. `alertas[]` e `alergiaConfirmada` opcionais no item da dieta. `CodigoErro` novo `ALERGIA_NAO_CONFIRMADA` (422, RN-DIE-14). Vetores em `contrato/calculos/alertas-restricao.json`. |
| Dependências | BE-51 (marcadores) e BE-52b (restrições na dieta), já entregues. No app, o alerta no montador entra **depois** da MOB-29b, porque as duas mexem no mesmo repositório de dietas. |

**Pontos de atenção.** C-03 (o bloco "alergias" da anamnese continua existindo em texto livre) e D-10 (quem valida o conteúdo dos marcadores novos).

### 3.2 H-02 Consulta com status, modalidade e link online

**Objetivo.** Fazer a agenda refletir o atendimento de verdade: a nutri marca a consulta como realizada ou falta, separa tipo de modalidade, informa o link da chamada e um recado ao paciente, e escolhe quando o lembrete sai. Não há vídeo próprio: o link é de fora (Meet, Zoom, WhatsApp).

**Critérios de aceite.**
- [ ] Status da consulta: Agendada, Realizada, Falta e Cancelada. Marcar Realizada ou Falta atualiza o comparecimento do financeiro, com **uma fonte só** (ver C-01).
- [ ] Modalidade Presencial ou Online, separada do tipo (Primeira, Retorno, Avaliação). O valor antigo `ONLINE` do tipo continua aceito e é gravado como Retorno + Online (ver C-02).
- [ ] Consulta online aceita um link `https`. O paciente vê "Entrar na consulta" na próxima consulta; o lembrete por e-mail leva o link.
- [ ] "Orientação para o paciente" (até 2.000 caracteres) aparece para o paciente. "Observação interna" nunca sai na projeção do paciente (teste dedicado).
- [ ] Na conta da nutri: antecedência do lembrete (2 h, 1 dia ou 2 dias; padrão 1 dia) e lembrete extra 1 h antes, ligado ou não. E-mail de confirmação ao agendar.
- [ ] Visão do mês na agenda, além de dia e semana. Tocar num horário vazio abre a nova consulta com data e hora preenchidas.
- [ ] Push continua sem dado clínico (RN-NOT-02). O feed `.ics` mostra a modalidade no título genérico e não leva o link nem a orientação.
- [ ] O app 1.3.x continua funcionando contra o backend 1.4.0 (campos novos opcionais, enum desconhecido tratado).

**Regras.** RN-AGE-01 e RN-AGE-02 (atualizar), `RN-AGE-03` (status e transições), `RN-AGE-04` (modalidade, link e textos), `RN-AGE-05` (preferência de lembrete), RN-FIN-01 (atualizar: comparecimento derivado do status), RN-NOT-01 (evento de confirmação).

| | Mudança |
| --- | --- |
| Backend | Módulo `agenda`: campos novos, transições de status, projeção do paciente separada da projeção da nutri, unificação com o financeiro, `.ics`, sync de escrita das consultas. Módulo `notificacoes`: job de lembrete por preferência e e-mail de confirmação. |
| App | `ui/nutri/agenda` (formulário, ações de status, visão do mês), conta da nutri (preferência de lembrete), `ui/paciente` (próxima consulta com link e orientação). |
| Dados | `consulta`: `modalidade`, `link_online`, `orientacao_paciente`, `observacao_interna`; `status` ganha `REALIZADA` e `FALTA`; `tipo` ganha `AVALIACAO`; migração das linhas `ONLINE`. `consulta_financeiro.comparecimento` passa a ser derivado. Preferência de lembrete na nutri. |
| API | `POST/PATCH /consultas` e coleção `consultas` do sync com os campos novos, todos opcionais. `PATCH` passa a aceitar `REALIZADA` e `FALTA` (hoje só aceita `CANCELADA`). `GET/PUT /eu/nutri/preferencias-agenda`. `CodigoErro` novos para transição inválida e link inválido. |
| Dependências | BE-23, BE-24 e BE-65, já entregues. H-06 usa o status Realizada na declaração de comparecimento. |

**Pontos de atenção.** C-01, C-02 e as decisões D-03 (o paciente vê "Falta"?) e D-08 (de quem é a antecedência do lembrete).

### 3.3 H-03 Humor e "comi fora do plano" no registro da refeição

**Objetivo.** A nutri entender por que a adesão caiu: o paciente diz como estava antes e depois da refeição e registra o que comeu fora do plano.

**Critérios de aceite.**
- [ ] No registro da refeição, duas escalas opcionais de 5 níveis: "fome antes" e "como ficou depois", com rótulos e ícones próprios do Dieta+.
- [ ] Botão "Comi algo fora do plano" no dia: texto curto (até 280 caracteres), refeição opcional e horário.
- [ ] Funciona offline e segue a regra de inclusão (RN-ROT-02 e RN-SYN-03): nunca gera conflito. O "Desfazer" de 5 segundos (MOB-UX-04) continua valendo para o registro inteiro.
- [ ] A nutri vê, na adesão do paciente, a média de fome e de sensação da semana e a lista dos registros fora do plano.
- [ ] O paciente pode desligar a pergunta de humor no perfil (preferência guardada no aparelho).
- [ ] Humor e registro fora do plano entram na exportação de dados do paciente e na auditoria (RN-LGPD-01 e 02).

**Regras.** RN-ROT-01 (ampliar com as escalas), `RN-ROT-06` (registro fora do plano, só inclusão, mesmas janelas de data do check-in).

| | Mudança |
| --- | --- |
| Backend | Módulo `rotina`: colunas no check-in, entidade nova, fonte e escrita de sync, resumo na adesão. |
| App | `ui/paciente/hoje` (escalas e botão), perfil do paciente (preferência), `ui/nutri/paciente` (cartão na adesão). |
| Dados | `checkin`: `fome_antes` e `sensacao_depois` (1 a 5, nulos). Tabela `registro_fora_do_plano`. O gatilho que alimenta o diário do sync dos check-ins precisa ser recriado em changeset novo para levar as colunas. |
| API | `fomeAntes` e `sensacaoDepois` opcionais em `POST /eu/checkins` e na coleção `checkins`. Coleção nova `registrosForaDoPlano` (puxar e enviar com `CRIAR`), `POST /eu/registros-fora-do-plano` e inclusão em `GET /eu/rotina` (web). `GET /pacientes/{id}/adesao` ganha `humor` e `foraDoPlano`, opcionais. |
| Dependências | BE-15, BE-16 e BE-38, já entregues. Independente das outras histórias. |

### 3.4 H-04 Metas de hábito para o paciente

**Objetivo.** Dar ao paciente um motivo para abrir o app todo dia, e à nutri uma leitura de hábitos além da dieta: metas com alvo, progresso e sequência de dias.

**Critérios de aceite.**
- [ ] Tipos de meta: água, minutos de atividade ou passos (digitados), horas de sono, porções de frutas e verduras, sim/não livre e contador livre com unidade.
- [ ] **A água vira um tipo de meta** (decisão do Guilherme, D-07): todo paciente tem a meta de água, com o alvo padrão de 35 ml por kg, que a nutri pode ajustar na própria meta; ela ganha frequência, sequência, cumprimento e modelo como as outras. Os registros de água que já existem são migrados, e o app 1.3.x continua registrando água pelo caminho antigo, que o backend lê como registro da meta (ver C-04). "Desfazer último" e "Outro valor" (MOB-UX-05) continuam.
- [ ] Cada meta tem alvo, frequência (diária ou N vezes por semana) e início e fim opcionais.
- [ ] Modelos de metas da nutri, aplicáveis a mais de um paciente.
- [ ] O paciente marca no Hoje e vê a sequência de dias e o progresso da semana. Funciona offline (registro só de inclusão).
- [ ] Lembrete **local** opcional por meta, respeitando o horário de silêncio (RN-NOT-03 e RN-ROT-05).
- [ ] A nutri vê o cumprimento das metas na ficha. O indicador "metas abaixo de 50% na semana" no painel entra se couber na tarefa, calculado no backend.
- [ ] Sem pontos nem níveis. Sequência e progresso bastam nesta release.
- [ ] Sequência e progresso são calculados por função pura no app, com vetores de teste compartilhados com o backend.

**Regras.** `RN-MET-01` (tipos, alvo e frequência), `RN-MET-02` (registro só de inclusão, janelas de data do check-in), `RN-MET-03` (cálculo de cumprimento e sequência), `RN-MET-04` (modelos da nutri, isolados por nutri), `RN-MET-05` (água como meta: alvo padrão, precedência do alvo e compatibilidade com o registro antigo). RN-ROT-03 é atualizada: a meta de água passa a ser uma meta de hábito, com o mesmo padrão de 35 ml por kg.

| | Mudança |
| --- | --- |
| Backend | Módulo novo `metas` (`api/`, `aplicacao/`, `dominio/`, `infra/`): metas, modelos, registros, fontes de sync, cumprimento. |
| App | `ui/paciente/hoje` (cartões de meta), tela de metas do paciente, `ui/nutri/paciente` (cadastro, modelos, cumprimento), `dominio/metas`, `AgendadorLembretes`. |
| Dados | Tabelas `meta_habito`, `modelo_meta` e `registro_meta`, no padrão das tabelas sincronizáveis. Migração dos registros de `registro_agua` para registros da meta de água, sem apagar a tabela antiga. |
| API | `GET/POST /pacientes/{id}/metas`, `PATCH /metas/{id}`, `GET/POST/PUT/DELETE /eu/nutri/modelos-meta`, `POST /pacientes/{id}/metas/de-modelo/{modeloId}`, `POST /eu/registros-meta`, `GET /pacientes/{id}/metas/cumprimento`. Coleções de sync `metas` (nutri escreve online; paciente só lê) e `registrosMeta` (paciente envia com `CRIAR`). `POST /eu/agua` e a coleção `agua` continuam aceitos, como caminho antigo, por pelo menos 6 meses (regra de versionamento do contrato). |
| Dependências | Nenhuma de outra história. O cartão no Hoje mexe na mesma tela que H-03: no app, H-03 entra antes. |

### 3.5 H-05 Suplementos e orientações no app do paciente

**Objetivo.** Tirar do WhatsApp e do PDF o que a nutri prescreve fora da dieta: suplementos com dose e horário, e orientações avulsas (hidratação, como montar o prato).

**Critérios de aceite.**
- [ ] Prescrição de suplemento a partir dos produtos da nutri ou por texto livre: dose, forma, frequência, horário, início e fim.
- [ ] A prescrição guarda uma cópia do nome e da dose (o paciente não recebe o catálogo de produtos da nutri).
- [ ] Orientação avulsa com título e texto formatado simples (negrito e lista), com modelos reutilizáveis da nutri. O texto é guardado em Markdown restrito e nunca como HTML.
- [ ] O paciente vê "Suplementos" e "Orientações" no app, offline. Lembrete local do suplemento no horário, que ele pode desligar.
- [ ] Push "Você tem uma nova orientação" sem dado clínico (o nome do suplemento nunca vai no push).
- [ ] O PDF da dieta pode incluir os suplementos ativos (opção da nutri ao gerar).
- [ ] O paciente nunca é punido pela assinatura da nutri: continua vendo o que já recebeu (RN-ASS-05).

**Regras.** `RN-SUP-01` (prescrição e cópia congelada), `RN-SUP-02` (vigência e encerramento), `RN-ORI-01` (orientação avulsa e modelos), RN-NOT-01 (evento novo "nova orientação").

| | Mudança |
| --- | --- |
| Backend | Módulo novo `prescricoes` (suplementos e orientações) ou dentro de `dietas`, a decidir no BE-C9 pelo `ArquiteturaTest`. `notificacoes`: evento novo. `relatorios`: bloco de suplementos no PDF da dieta. |
| App | `ui/nutri/paciente` (prescrever, orientar, modelos), `ui/paciente` (duas telas novas), `AgendadorLembretes`. |
| Dados | Tabelas `prescricao_suplemento`, `orientacao` e `modelo_orientacao`. |
| API | `GET/POST /pacientes/{id}/suplementos`, `PATCH /suplementos/{id}`, `GET/POST /pacientes/{id}/orientacoes`, `PATCH /orientacoes/{id}`, `GET/POST/PUT/DELETE /eu/nutri/modelos-orientacao`. Coleções de sync `prescricoesSuplemento` e `orientacoes` (nutri e paciente puxam). `incluirSuplementos` opcional em `GET /dietas/{id}/pdf`. |
| Dependências | BE-48b (produtos da nutri), BE-24 e BE-28, já entregues. |

### 3.6 H-06 Atestado e declaração de comparecimento em PDF

**Objetivo.** A nutri emitir, a partir de modelos, atestado e declaração de comparecimento com nome, CRN e logo, e o paciente achar esses documentos no app.

**Critérios de aceite.**
- [ ] Modelos de documento com campos de preenchimento (nome do paciente, data, horário da consulta, CRN). Dois modelos do sistema, somente leitura: atestado e declaração de comparecimento. A nutri copia para editar (como na RN-MOD-01).
- [ ] O documento emitido é **imutável**: guarda o texto final e o PDF. Corrigir é emitir outro; o anterior pode ser anulado, com motivo.
- [ ] PDF pelo gerador que já existe (BE-28), com link assinado de curta duração.
- [ ] Cada documento tem um código de conferência impresso. Uma rota pública, só de leitura e com limite de requisições, confirma que o documento existe, o tipo, quem emitiu, o CRN, a data e se está anulado. Nunca mostra conteúdo clínico (ver C-06 sobre as iniciais do paciente).
- [ ] A declaração de comparecimento pode ser emitida a partir de uma consulta com status Realizada, já preenchida com data e horário.
- [ ] O paciente vê os documentos emitidos para ele em "Documentos" (lista offline; abrir o PDF exige internet).
- [ ] Emissão, anulação e cada conferência pública ficam na auditoria.

**Regras.** `RN-DOC-01` (modelos do sistema e da nutri), `RN-DOC-02` (emissão imutável e anulação), `RN-DOC-03` (conferência pública sem dado clínico).

| | Mudança |
| --- | --- |
| Backend | Módulo novo `documentos` (modelos, emissão, conferência) usando `relatorios` (PDF e armazenamento). Segurança: rota pública com o limite do BE-31. |
| App | `ui/nutri/paciente` (emitir, lista, anular, compartilhar), conta da nutri (modelos), `ui/paciente` (Documentos). |
| Dados | Tabelas `modelo_documento` e `documento_emitido` (código de conferência único, texto final, chave do PDF, anulado em, motivo). Changeset com os dois modelos do sistema. |
| API | `GET/POST/PUT/DELETE /eu/nutri/modelos-documento`, `GET/POST /pacientes/{id}/documentos`, `POST /documentos/{id}/anular`, `GET /documentos/{id}/pdf`, `GET /eu/documentos`, `GET /eu/documentos/{id}/pdf`, `GET /api/v1/documentos/conferir/{codigo}` (pública). Coleção de sync `documentos` (metadados). "Gerar PDF" já está na lista de ações que exigem internet. |
| Dependências | H-02 (status Realizada) para a declaração a partir da consulta. A logo já sai no PDF quando existe. A assinatura digitalizada fica para a release 1.5.0 (D-05). |

**Pontos de atenção.** D-06: o texto dos dois modelos do sistema precisa ser validado por uma nutricionista antes de ir para produção.

### 3.7 H-07 Plano anual e segundo nível de plano

**Objetivo.** Dar à nutri para onde crescer (mais pacientes) e uma opção anual, e mostrar isso com clareza na tela de assinatura. **Os valores foram decididos pelo Guilherme (G-21, seção 7.1): o Básico continua e entram dois níveis novos, todos com anual.** A engenharia não depende deles: tudo é entregue com os níveis novos **inativos**, e os valores entram pelo admin, sem deploy (RN-ASS-01).

**Critérios de aceite.**
- [ ] A tabela `plano` aceita preço anual, a marcação "à venda" e limite de pacientes "sem limite". Dois níveis novos existem, inativos até a ativação pelo admin com os valores da seção 7.1.
- [ ] `GET /api/v1/planos`, público, devolve só os planos à venda, sem dado de usuário, com limite de requisições.
- [ ] O checkout aceita a periodicidade (mensal ou anual) e continua fora do app (RN-ASS-06). O webhook trata a renovação anual (RN-ASS-07).
- [ ] A nutri com assinatura ativa consegue **trocar de plano** (hoje o checkout responde `ASSINATURA_JA_ATIVA`). O comportamento da troca segue a decisão D-11.
- [ ] Tela de assinatura: comparar planos com alternância mensal/anual, uso de cada limite, "faltam N dias de teste" e botão que abre o navegador.
- [ ] Aviso no painel e por e-mail 7 dias e 1 dia antes do fim do teste.
- [ ] Downgrade segue a RN-ASS-08 (ninguém perde paciente; novos convites ficam bloqueados).
- [ ] O admin (BE-30) cadastra e edita os campos novos do plano, com log.

**Regras.** RN-ASS-01, 02, 06, 07 e 08 (atualizar), `RN-ASS-09` (periodicidade e renovação anual), `RN-ASS-10` (troca de plano), `RN-ASS-11` (avisos do fim do teste). Suposições S1, S3 e S4 de `visao-geral.md`.

| | Mudança |
| --- | --- |
| Backend | Módulo `assinaturas`: modelo do plano, endpoint público, admin, gateway (hoje `cycle` fixo em `MONTHLY` e só `valorMensal`), webhook, job do ciclo, troca de plano. `notificacoes`: avisos do fim do teste. |
| App | `ui/nutri/assinatura` (comparar planos, uso, contagem do teste). |
| Dados | `plano`: `preco_anual`, `a_venda` e `limite_pacientes` aceitando nulo (sem limite). `assinatura`: `periodicidade`. Linhas dos dois níveis novos inativas, sem valor de preço no changeset. |
| API | `GET /api/v1/planos` (pública). `POST /assinatura/checkout` ganha `planoCodigo` e `periodicidade`, opcionais. `GET /assinatura` ganha `periodicidade`, `diasRestantesTeste` e `planosDisponiveis`, opcionais. Contrato próprio: BE-C10. |
| Dependências | BE-26, BE-27 e BE-30, já entregues. Valores decididos na **G-21** (seção 7.1). **G-08** (conta do Asaas) para validar no sandbox. Independente das outras histórias. |

**Pontos de atenção.** C-05 (preço por changeset contra RN-ASS-01), C-07 (troca de plano não existe hoje), D-09 (preço e botão de assinar no iOS) e D-11.

### 3.8 MOB-29b Escrita offline de dietas, listas e modelos

**Objetivo.** Fechar o que faltou do marco M5: a nutri monta e edita dieta, lista de substituição e modelos sem internet, no desktop e no tablet, com resolução de conflito.

**Estado hoje.** No app, `DietasRepositorio`, `ListaPacienteRepositorio` e `ModelosDietaRepositorio` só funcionam online. No backend, o `POST /sync` já aceita `dietas`, `listas` (só atualização) e `modelos` (de questionário), mas **não aceita `modelosDieta`** (só leitura). Por isso a tarefa é dividida em três e ganha uma tarefa de backend.

**Critérios de aceite.**
- [ ] Rascunho de dieta: criar, editar e excluir gravando no banco local e na fila na mesma transação, com `versaoBase`. Os totais mostram a prévia local (`PlanejadorPorcoes`) e são trocados pelos do backend depois do envio (RN-DIE-05).
- [ ] Lista de substituição do paciente e modelo de questionário gravam local e fila.
- [ ] Modelo de dieta: criar, editar, duplicar e excluir offline. O modelo excluído sai do cache local (pendência registrada na MOB-29b).
- [ ] Conflito de versão abre o diálogo que já existe (MOB-30); mutação rejeitada desfaz localmente e mostra a mensagem do `codigo`.
- [ ] Continuam exigindo internet, com o botão desabilitado sem conexão: publicar, aplicar modelo a um paciente, salvar dieta como modelo, copiar a lista de outro paciente, convidar, enviar questionário e gerar PDF.
- [ ] Teste de ponta a ponta com duas edições concorrentes do mesmo rascunho (critério do M5).
- [ ] Nada muda na web (`wasmJs` é só online).

**Regras.** RN-SYN-01 a RN-SYN-04, RN-DIE-02, 04 e 05, RN-MOD-01, RN-ALI-06.

| | Mudança |
| --- | --- |
| Backend | BE-83: `modelosDieta` no `POST /sync` (`CRIAR`, `ATUALIZAR`, `EXCLUIR`), com as mesmas regras do REST (modelo do sistema é `403`, de outra nutri é `404`). |
| App | `data/repositorio` (três repositórios), `data/sync` (`EscritaOffline`, `FilaMutacoes`), banco local (migração nova do SQLDelight se faltar tabela), telas do montador, da lista e dos modelos (estado "pendente de envio"). |
| Dados | Nenhuma tabela nova no backend. No app, migração nova, sem alterar as publicadas. |
| API | Entidade `modelosDieta` no `POST /sync`, documentada em `contrato-fase-5.md` e `integracao.md`. |
| Dependências | MOB-29 e BE-25, já entregues. BE-83 para a MOB-29d. A MOB-29b (dietas) vem **antes** da MOB-83 (alerta no montador). |

## 4. Tarefas

Esforço: **P** = 1 PR pequeno (1 ponto), **M** = 1 PR médio com contrato, testes e docs (3 pontos), **G** = PR grande ou tarefa que provavelmente vira 2 PRs `a` e `b` (5 pontos). O ponto mede tamanho relativo, já contando uma rodada de correção do QA. Não é prazo: o ritmo depende de quantas pistas rodam em paralelo e do tempo de resposta do QA e do CI.

### 4.1 Design (canvas, branch `feature/DES-NN-…` em `mobile/`)

| Tarefa | História | Telas | Precisa estar pronta para | Esforço |
| --- | --- | --- | --- | --- |
| DES-16 Saúde e restrições | H-01 | Seção da ficha, selo no cabeçalho, alerta de intolerância e confirmação de alergia no montador | MOB-82, MOB-83 | M |
| DES-17 Consulta e agenda | H-02 | Formulário da consulta (tipo, modalidade, link, orientação, observação), ações de status, visão do mês, preferência de lembrete, próxima consulta do paciente com "Entrar na consulta" | MOB-84 a MOB-87 | M |
| DES-18 Diário da refeição | H-03 | Escalas no registro, "comi fora do plano", cartão de humor e de registros na adesão da nutri | MOB-88, MOB-89 | P |
| DES-19 Metas de hábito | H-04 | Hoje com as metas (a água é uma delas, mantendo desfazer e valor livre), registro, sequência, cadastro e modelos da nutri, cumprimento na ficha | MOB-90, MOB-91 | M |
| DES-20 Suplementos e orientações | H-05 | Prescrever e orientar (nutri), modelos, "Suplementos" e "Orientações" (paciente) | MOB-92, MOB-93 | M |
| DES-21 Documentos | H-06 | Modelos, emitir e anular, lista na ficha, "Documentos" do paciente, layout do PDF e da página de conferência | MOB-94, MOB-95, BE-78, BE-79 | M |
| DES-22 Assinatura | H-07 | Comparar planos com mensal/anual, uso dos limites, contagem do teste, aviso de fim do teste no painel | MOB-96 | M |

A MOB-29b não tem tela nova: usa `IndicadorSync`, `DialogoConflito` e `FaixaOffline`, que já têm artboard. Se o montador precisar de um estado que não existe, a tarefa pede ao Design pelo coordenador. Estados de carregando, vazio, erro e sem internet seguem `componentes.md`.

### 4.2 Backend

| Tarefa | História | O que faz | Depende de | Esforço | Onda |
| --- | --- | --- | --- | --- | --- |
| **BE-C9** Contrato da 1.4.0 | H-01 a H-06 | `contrato-fase-9.md`, records, controllers `501`, `CodigoErro` novos, JSONs de exemplo, vetores (`alertas-restricao.json`, `metas.json`), regras novas em `regras-de-negocio.md` e mapa em `integracao.md` (com as cópias do app) | — | M | 24 |
| **BE-68** Restrições de saúde na ficha | H-01 | Itens de saúde na ficha, REST e sync, validação, isolamento, auditoria | BE-C9 | M | 24 |
| **BE-69** Marcadores novos | H-01 | Marcadores novos e sugestão por nome no catálogo e nas cópias das nutris | BE-C9 | P | 24 |
| **BE-70** Alerta e confirmação na dieta | H-01 | `alertas[]` por item, `alergiaConfirmada`, `restricoes` da ficha na dieta nova, recusa na publicação, vetores | BE-68, BE-69 | M | 25 |
| **BE-71** Consulta: status, modalidade, link e textos | H-02 | Campos e migração, transições, projeção separada do paciente, comparecimento derivado, `.ics`, sync | BE-C9 | G | 24 |
| **BE-72** Lembretes configuráveis e confirmação | H-02 | Preferência da nutri, job por antecedência, lembrete extra, e-mail de confirmação | BE-71 | M | 25 |
| **BE-73** Diário: humor e fora do plano | H-03 | Colunas no check-in, `registro_fora_do_plano`, sync, `GET /eu/rotina`, resumo na adesão | BE-C9 | M | 25 |
| **BE-74** Metas de hábito | H-04 | Módulo `metas`: metas, modelos, registros, sync, cumprimento | BE-C9 | G | 25 |
| **BE-86** Água como tipo de meta | H-04 | Meta de água para todo paciente (alvo padrão de 35 ml por kg, ajustável pela nutri), migração dos registros, caminho antigo `agua` lido como registro da meta, adesão e evolução sem regressão | BE-74 | M | 25 |
| **BE-75** Suplementos prescritos | H-05 | Prescrição com cópia congelada, vigência, sync | BE-C9 | M | 26 |
| **BE-76** Orientações avulsas | H-05 | Orientações, modelos, sync, evento de push "nova orientação" | BE-C9 | M | 26 |
| **BE-77** Suplementos no PDF da dieta | H-05 | Bloco opcional de suplementos ativos no PDF | BE-75 | P | 26 |
| **BE-78** Documentos: modelos, emissão e PDF | H-06 | Módulo `documentos`, modelos do sistema, emissão imutável, anulação, PDF | BE-C9, BE-71, DES-21 | M | 26 |
| **BE-79** Documentos: conferência e lista do paciente | H-06 | Rota pública com limite e auditoria, `GET /eu/documentos`, sync | BE-78 | M | 26 |
| **BE-C10** Contrato dos planos | H-07 | `contrato-fase-10.md`, records, `501`, JSONs, RN-ASS novas | — | P | 25 |
| **BE-80** Planos: anual, à venda, endpoint público e admin | H-07 | Colunas, limite "sem limite", dois níveis novos **inativos**, `GET /api/v1/planos`, campos no admin, `GET /assinatura` ampliado | BE-C10 | M | 26 |
| **BE-81** Gateway anual, troca de plano e webhook | H-07 | Periodicidade no checkout, renovação anual, troca de plano (D-11), job do ciclo | BE-80, D-11 | G | 27 |
| **BE-82** Avisos do fim do teste | H-07 | Aviso e e-mail 7 dias e 1 dia antes, com relógio controlado | BE-80 | P | 27 |
| **BE-83** `modelosDieta` na escrita do sync | MOB-29b | `CRIAR`, `ATUALIZAR` e `EXCLUIR` de modelo de dieta pelo `POST /sync` | — | M | 24 |
| **BE-84** LGPD e isolamento das entidades novas | todas | Exportação (RN-LGPD-02) e anonimização (RN-LGPD-03) com os dados novos, `log_acesso` nas rotas novas, revisão dos testes de isolamento, compatibilidade com os JSONs de contrato da 1.3 | BE-68 a BE-79 | M | 27 |
| **BE-85** Fechamento do backend | todas | `CHANGELOG` (seção 1.4.0 e acerto do título que chama o M10 de 1.4.0), `funcionalidades-mvp.md` (F-22 a F-28), `integracao.md` (tempo do link assinado: o texto diz 5 min, o PDF da lista usa 10), versão | todas | P | 27 |

### 4.3 App

| Tarefa | História | O que faz | Depende de | Esforço | Onda |
| --- | --- | --- | --- | --- | --- |
| **MOB-81** Contrato da 1.4.0 no app | H-01 a H-06 | DTOs novos e opcionais, JSONs de contrato copiados, `MockEngine`, enum desconhecido tratado, migração do banco local para as coleções novas | BE-C9 | M | 24 |
| **MOB-29b** Rascunho de dieta offline | MOB-29b | Criar, editar e excluir rascunho com fila, prévia local, conflito e rejeição | — | G | 24 |
| **MOB-29c** Lista de substituição e modelo de questionário offline | MOB-29b | Gravação local e fila para os dois | MOB-29b | M | 25 |
| **MOB-29d** Modelo de dieta offline | MOB-29b | Criar, editar, duplicar e excluir offline; excluído sai do cache | BE-83, MOB-29b | M | 25 |
| **MOB-82** Ficha: saúde e restrições | H-01 | Seção, selo, escrita offline pela ficha | DES-16, MOB-81, BE-68 | M | 24 |
| **MOB-83** Montador: alerta e confirmação | H-01 | Regra em `dominio/` com os vetores, alerta, confirmação, erro `ALERGIA_NAO_CONFIRMADA` | DES-16, MOB-29b, BE-70 | M | 25 |
| **MOB-84** Agenda: formulário e status | H-02 | Tipo, modalidade, link, orientação, observação, ações Realizada e Falta | DES-17, MOB-81, BE-71 | M | 25 |
| **MOB-85** Agenda: visão do mês | H-02 | Calendário do mês e horário vazio que abre a nova consulta | DES-17, MOB-84 | M | 25 |
| **MOB-86** Preferência de lembrete da nutri | H-02 | Antecedência e lembrete extra na conta | DES-17, BE-72 | P | 26 |
| **MOB-87** Paciente: próxima consulta | H-02 | "Entrar na consulta", orientação, modalidade | DES-17, BE-71 | P | 25 |
| **MOB-88** Paciente: humor e fora do plano | H-03 | Escalas, botão, preferência local, offline | DES-18, MOB-81, BE-73 | M | 25 |
| **MOB-89** Nutri: humor e fora do plano na adesão | H-03 | Cartão na adesão do paciente | DES-18, BE-73 | P | 26 |
| **MOB-90** Paciente: metas de hábito | H-04 | Hoje com metas, registro, sequência (`dominio/metas`), lembrete local | DES-19, MOB-88, BE-74 | M | 26 |
| **MOB-91** Nutri: metas, modelos e cumprimento | H-04 | Cadastro, modelos, aplicar, cumprimento na ficha | DES-19, BE-74 | M | 26 |
| **MOB-99** Água como meta no app | H-04 | Cartão de água do Hoje passa a ler e registrar pela meta, com desfazer e valor livre; migração do banco local; alvo ajustado pela nutri | DES-19, MOB-90, BE-86 | M | 26 |
| **MOB-92** Nutri: prescrever e orientar | H-05 | Suplementos, orientações e modelos | DES-20, BE-75, BE-76 | M | 26 |
| **MOB-93** Paciente: suplementos e orientações | H-05 | Duas telas offline, lembrete local, push "nova orientação" | DES-20, BE-75, BE-76 | M | 26 |
| **MOB-94** Nutri: documentos | H-06 | Modelos, emitir, anular, compartilhar o PDF | DES-21, BE-78 | M | 27 |
| **MOB-95** Paciente: Documentos | H-06 | Lista offline e abrir o PDF online | DES-21, BE-79 | P | 27 |
| **MOB-96** Assinatura: comparar planos | H-07 | Alternância mensal/anual, uso, contagem do teste, botão que abre o navegador | DES-22, BE-80 | M | 27 |
| **MOB-97** Ponta a ponta da web | todas | Cenários novos no `mobile/e2e` (restrição e alerta, status da consulta, humor, meta, suplemento, documento, planos) | histórias na `develop` | M | 27 |
| **MOB-98** Fechamento do app | todas | `CHANGELOG`, versão 1.4.0, `telas.md` e `divergencias.md` atualizados, conferência nos 3 tamanhos | todas | P | 27 |

### 4.4 Soma do esforço

| Projeto | Tarefas | P | M | G | Pontos |
| --- | --- | --- | --- | --- | --- |
| Design | 7 | 1 | 6 | 0 | 19 |
| Backend | 21 | 5 | 13 | 3 | 59 |
| App | 22 | 5 | 16 | 1 | 58 |
| **Total** | **50** | 11 | 35 | 4 | **136** |

Por história (backend + app + design): H-01 16 · H-02 19 · H-03 8 · H-04 20 · H-05 16 · H-06 13 · H-07 16 · MOB-29b 14 · transversais (contratos, LGPD, ponta a ponta, fechamento) 14.

São cerca de 50 PRs de tarefa, mais os PRs da release. Não há histórico de velocidade medido em dias para converter pontos em prazo com segurança. A proposta é medir a onda 24 e projetar o resto a partir dela.

## 5. Ordem de execução

### 5.1 Ondas

| Onda | Backend | App | Design | Integração ao fim da onda |
| --- | --- | --- | --- | --- |
| **24** Contrato, segurança e offline | BE-C9 · BE-68 · BE-69 · BE-71 · BE-83 | MOB-81 · MOB-29b · MOB-82 | DES-16 · DES-17 · DES-18 | Ficha com restrições sincroniza; rascunho de dieta offline no desktop |
| **25** Alerta, agenda e diário | BE-70 · BE-72 · BE-73 · BE-74 · BE-86 · BE-C10 | MOB-83 · MOB-84 · MOB-85 · MOB-87 · MOB-88 · MOB-29c · MOB-29d | DES-19 · DES-20 · DES-21 | Alerta de alergia no montador; consulta online com link; paciente registra humor |
| **26** Hábitos e prescrição | BE-75 · BE-76 · BE-77 · BE-78 · BE-79 · BE-80 | MOB-86 · MOB-89 · MOB-90 · MOB-99 · MOB-91 · MOB-92 · MOB-93 | DES-22 | Paciente marca meta e vê suplemento e orientação offline |
| **27** Documentos, planos e fechamento | BE-81 · BE-82 · BE-84 · BE-85 | MOB-94 · MOB-95 · MOB-96 · MOB-97 · MOB-98 | — | **Marco M11 = 1.4.0** (`release/1.4.0`) |

Uma onda começa quando as tarefas de backend da anterior estão na `develop`. O app pode atrasar meia onda sem travar ninguém, e o design fica uma onda à frente do app.

### 5.2 O que pode correr em paralelo

O backend e o app têm duas pistas cada, que mexem em módulos diferentes. Com dois agentes de código por projeto, as pistas andam ao mesmo tempo; com um, intercale por onda.

| Pista | Sequência |
| --- | --- |
| Backend A (clínica) | BE-C9 → BE-68 → BE-69 → BE-70 → BE-73 → BE-74 → BE-86 → BE-75 → BE-76 → BE-77 → BE-84 |
| Backend B (agenda, documentos e comercial) | BE-83 → BE-71 → BE-72 → BE-C10 → BE-80 → BE-78 → BE-79 → BE-81 → BE-82 → BE-85 |
| App A (nutri) | MOB-81 → MOB-29b → MOB-82 → MOB-83 → MOB-29c → MOB-29d → MOB-91 → MOB-92 → MOB-94 → MOB-96 |
| App B (paciente e agenda) | MOB-84 → MOB-85 → MOB-87 → MOB-88 → MOB-86 → MOB-89 → MOB-90 → MOB-99 → MOB-93 → MOB-95 → MOB-97 → MOB-98 |
| Design | DES-16 → DES-17 → DES-18 → DES-19 → DES-20 → DES-21 → DES-22 |

- **Independentes entre si:** H-03, H-05 e H-07 não dependem de outra história. H-07 pode ser adiantada inteira se uma pista ficar livre, porque os valores entram depois.
- **Em paralelo dentro do backend:** BE-68 com BE-71 e BE-83; BE-73 com BE-74; BE-75 com BE-76; BE-78 com BE-80.
- **Em paralelo dentro do app:** MOB-29b com MOB-82; MOB-83 com MOB-84 e MOB-88; MOB-92 com MOB-93.
- **Não paralelizar:**
  - MOB-29b, MOB-83 e MOB-29d mexem no repositório e nas telas de dieta: uma de cada vez, nessa ordem.
  - MOB-88, MOB-90 e MOB-99 mexem no Hoje do paciente: uma de cada vez, nessa ordem.
  - MOB-81 mexe em muitos DTOs do `shared`: sozinha, logo no começo.
  - PRs abertos ao mesmo tempo que editam `regras-de-negocio.md`, `integracao.md` ou a mesma linha do `plano-de-acao.md` precisam mesclar a `develop` antes do QA. Para reduzir isso, o BE-C9 já escreve todas as regras novas de uma vez.

### 5.3 Caminho crítico

1. BE-C9 → BE-68 → BE-70 → MOB-83, que ainda espera a MOB-29b: é a cadeia mais longa e a que entrega o item de segurança.
2. BE-71 → BE-78 → BE-79 → MOB-94 e MOB-95: os documentos dependem do status da consulta.
3. DES-16 e DES-17 precisam sair na primeira metade da onda 24. O design é uma pista só e atende sete conjuntos de telas.
4. Os valores (G-21) já estão decididos. A ativação dos planos novos depende da conta do Asaas validada no sandbox (G-08) ; sem isso até o corte, a 1.4.0 sai com os níveis novos e o anual inativos.

## 6. Conflitos entre o benchmark e a arquitetura atual

Pedido do Guilherme: apontar o conflito em vez de decidir sozinho. Cada item traz o que o backlog pede, o que existe hoje e uma recomendação, que só vale depois da confirmação.

| # | História | O que o backlog pede | O que existe hoje | Recomendação |
| --- | --- | --- | --- | --- |
| **C-01** | H-02 | Status Realizada e Falta na consulta, com "observação interna" e comparecimento em uma fonte só | A consulta sincronizada é **a mesma projeção** para a nutri e para o paciente (`FontesConsultasSync`). O comparecimento vive em `consulta_financeiro`, fora do sync de propósito ("dinheiro é só da nutri", RN-FIN-01). O `PATCH` só aceita mudar o status para `CANCELADA` | Separar a projeção do paciente (sem observação interna e sem "Falta") e tornar o status da consulta a fonte, com `comparecimento` derivado. Decisão D-03 |
| **C-02** | H-02 | Trocar `ONLINE` de tipo para modalidade "sem quebrar o app antigo" | `TipoConsulta` é `PRIMEIRA`, `RETORNO`, `ONLINE`. Remover valor de enum é mudança incompatível (só em `/api/v2`) | Não remover: `ONLINE` continua aceito na entrada, é gravado como Retorno + Online e some das respostas novas. O app 1.3.x vai mostrar essas consultas como "Retorno", sem erro. Confirmar se essa perda de rótulo no app antigo é aceitável |
| **C-03** | H-01 | Alergias estruturadas na ficha | A anamnese padrão já tem o bloco "alergias" em texto livre (RN-ANA-01) | Manter os dois na 1.4.0, sem migração automática de texto livre para item estruturado. "Sugerir a partir da anamnese" fica para depois |
| **C-04** | H-04 | "Água vira um tipo de meta" (RN-ROT-03) | Água é uma coleção própria do sync (`agua`), com meta de 35 ml/kg, cartão no Hoje, desfazer e valor livre (MOB-UX-05). Apps antigos enviam `agua` | A recomendação do PO era não migrar. **O Guilherme decidiu migrar (D-07).** Para não quebrar o app 1.3.x, o caminho antigo (`POST /eu/agua` e a coleção `agua`) continua aceito por pelo menos 6 meses e é lido como registro da meta de água (BE-86, MOB-99) |
| **C-05** | H-07 | Segundo nível "cadastrado por changeset com os valores aprovados" | RN-ASS-01: mudar preço não exige deploy; o admin tem CRUD de planos (BE-30) | Changeset só para as colunas e para a linha inativa, sem valor de preço. Os valores entram pelo admin, com log |
| **C-06** | H-06 | A rota pública de conferência mostra as **iniciais do paciente** | Regra do projeto: dado de saúde não sai sem autenticação. Iniciais + nutri + data permitem inferir que a pessoa é paciente de uma nutricionista | Rota pública sem dado do paciente (tipo, emissor, CRN, data, situação). Decisão D-04 |
| **C-07** | H-07 | Segundo nível de plano | O checkout recusa quem já tem assinatura ativa (`ASSINATURA_JA_ATIVA`), o gateway cria cobrança só mensal e o backlog não descreve a troca de plano | Tratar a troca de plano como parte da história (BE-81). Decisão D-11 |
| **C-08** | H-05 | Prescrição "a partir dos produtos da nutri" visível ao paciente | O paciente não recebe os produtos da nutri no sync, e as cópias do catálogo não entram no sync ao nascer (RN-ALI-05) | A prescrição guarda cópia do nome e da dose, como o snapshot da dieta. Sem mudança de visibilidade dos produtos |
| **C-09** | H-04, H-05, H-06 | Tudo "funciona offline" | Offline da nutri é por entidade, com conflito (RN-SYN-02). Cada entidade nova com escrita offline da nutri custa uma tarefa como a MOB-29b | Na 1.4.0, as entidades novas da nutri (metas, suplementos, orientações, documentos) são **escritas online e lidas offline**. O paciente registra offline (só inclusão). Escrita offline da nutri nessas entidades fica para a 1.5.0. A ficha (H-01) e a consulta (H-02) já têm escrita offline e continuam tendo |
| **C-10** | H-02, H-05 | Push de "nova orientação" e e-mail de confirmação de consulta | RN-NOT-01 é uma lista fechada de eventos de push | Ampliar a RN-NOT-01 com os dois eventos, mantendo o texto genérico (RN-NOT-02). É mudança de regra, registrada no BE-C9 |

## 7. Decisões do Guilherme

### 7.1 G-21 Valores dos planos (decididos pelo Guilherme em 16/11/2026)

Situação de hoje, na tabela `plano`: **Teste**, grátis, 30 dias, 5 pacientes; **Básico**, R$ 29,90 por mês, 20 pacientes. Não há anual nem segundo nível. Os números do concorrente estão em `analise-de-lacunas.md`, seção 4.1, só como referência de mercado.

**Decidido pelo Guilherme em 16/11/2026** ("pode copiar os planos", na forma "manter o Básico e somar dois níveis"):

| Plano | Mensal | Anual (10 × o mensal) | Pacientes | Situação |
| --- | --- | --- | --- | --- |
| Teste | grátis | — | 5, por 30 dias | Não muda |
| Básico | R$ 29,90 | R$ 299,00 | 20 | Já existe; ganha o anual |
| **Consultório** (`CONSULTORIO`) | R$ 79,90 | R$ 799,00 | 80 | Novo |
| **Plus** (`PLUS`) | R$ 179,90 | R$ 1.799,00 | Ilimitado | Novo |

- Os valores dos dois níveis novos são os do concorrente (seção 4.1 de `analise-de-lacunas.md`). Os **nomes** são próprios do Dieta+, escolhidos pelo PO a pedido do Guilherme em 17/11/2026: **Consultório** (a nutri com a agenda cheia de um consultório) e **Plus** (o nível sem limite, que reforça a marca Dieta+). Continuam editáveis no admin.
- Nada disso vira changeset de preço: os níveis nascem inativos e os valores entram pelo admin (C-05). A ativação depende da conta do Asaas validada no sandbox (G-08).
- "Ilimitado" exige que o limite de pacientes do plano aceite "sem limite" (hoje a coluna é obrigatória e numérica). Entrou na BE-80.
- **Formas de pagamento (decidido em 17/11/2026): PIX, cartão de crédito e boleto**, em mensal e anual. O boleto fica porque o gateway já oferece: hoje o `GatewayAsaas` cria a assinatura com `billingType` `UNDEFINED`, que deixa a nutri escolher entre as três no checkout, e isso não muda. No cartão a renovação é automática; no PIX e no boleto a nutri paga uma cobrança nova a cada período, então o aviso de renovação é obrigatório (BE-81, R-09).

As perguntas originais ficam abaixo, como registro.

**a) Segundo nível: quantos pacientes?**

| Opção | Como fica | O que pesa |
| --- | --- | --- |
| A1 | Limite fechado entre 60 e 100 pacientes | Simples de explicar; deixa espaço para um terceiro nível depois |
| A2 | Ilimitado | Resolve de vez o teto; tira o motivo para um terceiro nível e não há hoje um recurso de consumo (armazenamento, IA) que limite o uso |
| A3 | Dois níveis novos (um intermediário e um alto) | Mais opções para a nutri; mais complexo de comunicar e de testar |

**b) Segundo nível: quanto por mês?** Valor livre, seu. Referências para pensar: o Básico custa R$ 1,50 por paciente no limite; o nível novo pode manter esse custo por paciente, reduzir (premiar quem cresce) ou seguir uma razão fixa sobre o Básico (2×, 3×).

**c) Anual: qual a regra?**

| Opção | Como fica |
| --- | --- |
| B1 | Anual = 10 × o mensal (dois meses grátis) |
| B2 | Anual = 11 × o mensal (um mês grátis) |
| B3 | Valor anual livre por plano, sem regra fixa |
| B4 | Anual só no segundo nível; o Básico continua só mensal |

Também preciso saber se o anual vale para os dois planos pagos ou só para um (B4).

**d) Teste: muda?**

| Opção | Como fica |
| --- | --- |
| T1 | Fica como está: 30 dias, 5 pacientes |
| T2 | 30 dias, limite maior (10 ou 15 pacientes) |
| T3 | Prazo menor com limite maior |

E dois detalhes ligados a isso: o **nome** do segundo nível (precisa ser próprio do Dieta+) e se o anual poderá ser pago por PIX e boleto, além de cartão. No PIX e no boleto a renovação anual não é automática: depende de a nutri pagar a cobrança nova, então o aviso de renovação precisa existir.

### 7.2 Outras decisões

| # | Decisão | Opções | Recomendação do PO | Trava |
| --- | --- | --- | --- | --- |
| **D-03** | O paciente vê que a consulta ficou como "Falta"? | (a) Não: o paciente só vê Agendada, Cancelada e "encerrada"; (b) Sim, vê o status real | (a) | BE-71 |
| **D-04** | A conferência pública do documento mostra as iniciais do paciente? | (a) Não mostra nada do paciente; (b) mostra as iniciais, como no backlog | (a) | BE-79 |
| **D-05** | Assinatura digitalizada da nutri no documento entra na 1.4.0? | (a) Não: nome, CRN, logo e código de conferência; (b) Sim: exige envio de imagem (mesmo caminho da MOB-33b, que ainda falta na web e no iOS) | (a). **Decidido: fica para a release 1.5.0** | BE-78 |
| **D-06** | Quem valida o texto dos modelos de atestado e de declaração? | (a) A nutri parceira revisa antes da release; (b) sai marcado como suposição, para revisar depois | (a) | BE-78 |
| **D-07** | Água vira um tipo de meta? | (a) Não migra, só aparece junto; (b) migra para o modelo de metas | (a). **Decidido diferente: (b), a água vira um tipo de meta** | BE-74, BE-86 |
| **D-08** | A antecedência do lembrete escolhida pela nutri vale para quem? | (a) Para a nutri e para os pacientes dela; (b) só para o lembrete do paciente; a nutri mantém 24 h | (a) | BE-72 |
| **D-09** | No iOS, a tela de assinatura mostra preço e o botão que abre o navegador? | (a) Igual em todas as plataformas; (b) no iOS mostra o plano e o uso, sem preço nem botão, até conferir as regras da App Store (S4); (c) controlado por uma chave do backend | (c) | MOB-96 |
| **D-10** | Quem valida os marcadores novos sugeridos por nome (amendoim, frutos do mar e outros)? | (a) A nutri parceira; (b) saem como suposição (como a G-15), editáveis por nutri | (b), com o texto do alerta dizendo que é sugestão | BE-69 |
| **D-11** | Como funciona a troca de plano com assinatura ativa? | (a) Só no próximo vencimento; (b) na hora, com o limite novo valendo já e cobrança cheia do plano novo, sem crédito proporcional; (c) na hora, com crédito proporcional calculado | (b) para subir de plano e (a) para descer; crédito proporcional fica como ajuste manual do admin | BE-81 |
| **D-12** | Aprovar os conflitos C-02, C-03, C-05, C-08, C-09 e C-10 como recomendados | Sim, ou dizer qual muda | Sim | BE-C9 |

**Decidido em 16/11/2026 pelo Guilherme:** valem as recomendações desta tabela (D-03 a D-12, o que inclui os conflitos da seção 6), com duas mudanças marcadas nas linhas: na **D-07** a água vira um tipo de meta, e na **D-05** a assinatura digitalizada fica marcada para a release 1.5.0. O texto das tarefas nos planos de ação já reflete isso.

Registro do que valia antes da resposta: nenhuma decisão travava a onda 24, exceto a D-12 (o BE-C9 escreve as regras). As outras podem chegar até o começo da tarefa que elas travam. Sem resposta, a tarefa segue a recomendação, marca `[SUPOSIÇÃO]` e deixa o valor configurável, como o `AGENTS.md` já manda. **A G-21 é a exceção: sem os valores, nada é ativado.**

### 7.3 Tarefas do Guilherme

| ID | O quê | Quando | Trava |
| --- | --- | --- | --- |
| **G-21** | Valores e limites dos planos (seção 7.1) | **Feito em 16/11/2026.** Nomes (Consultório e Plus) e formas de pagamento (PIX, cartão e boleto, as que o gateway já oferece) definidos em 17/11/2026 | Ativar os planos |
| **G-22** | Landing em `site/` no monorepo: **decidido**, só registro | — | `SITE-01` (futura) |
| **G-23** | Fotos de evolução e do prato entram no roadmap? | Depois da 1.4.0 | L-10 |
| **G-24** | Assistente de IA vira plano próprio? | Depois da 1.4.0 | L-21 |
| **G-25** | Finalizar e aprovar os termos de uso e a política de privacidade | Antes de qualquer landing | Landing e abertura do cadastro (BE-36) |
| **G-26** | Responder D-03 a D-12 | **Feito em 16/11/2026**: como recomendado, menos a D-07 (água vira meta); D-05 marcada para a 1.5.0 | — |
| **G-27** | Testar no Android e, com o Mac do amigo, no iPhone o roteiro da seção 9.3 | Na `release/1.4.0` | Corte da release |
| **G-28** | Aprovar o PR `release/1.4.0 → main` e autorizar a tag `v1.4.0` | Fim da onda 27 | Publicação |

A conta do Asaas (G-08, já aberta no plano anterior) continua necessária para validar o checkout anual no sandbox.

## 8. Riscos

| # | Risco | Efeito | Como reduzir |
| --- | --- | --- | --- |
| R-01 | **Design vira gargalo**: sete conjuntos de telas numa pista só | O app para esperando artboard | DES-16 e DES-17 primeiro; o app adianta MOB-81 e MOB-29b, que não dependem de desenho |
| R-02 | **MOB-29b é grande e mexe no montador**, a tela mais complexa do app | Atrasa a MOB-83 (alerta de alergia), que é o item de segurança | Começar na onda 24, dividir em três, não abrir outro PR no montador ao mesmo tempo |
| R-03 | **Vazamento na projeção do paciente** (observação interna, falta, gravidade, medicamento) | Dado clínico ou administrativo chega a quem não deve | Projeção separada por perfil e teste dedicado em cada entidade nova; item obrigatório da revisão do QA |
| R-04 | **Alerta de alergia passa falsa segurança**: o marcador é sugerido por nome e pode errar | Alimento com alérgeno sem marcador não alerta | Texto do alerta diz que é apoio e que a conduta é da nutri; a nutri edita os marcadores; D-10 |
| R-05 | **Migração das consultas** (`ONLINE`, status e comparecimento) em base com dados | Consulta ou financeiro inconsistente depois do deploy | Changeset com teste sobre massa de dados da 1.3; comparecimento derivado conferido linha a linha no teste |
| R-06 | **Compatibilidade com o app 1.3.x** já instalado | App antigo quebra ou mostra dado errado | Só campos opcionais; teste do backend com os JSONs de contrato da 1.3; sem subir a versão mínima |
| R-07 | **Rota pública de conferência** é alvo de varredura | Enumeração de códigos | Código longo e aleatório, limite de requisições do BE-31, resposta igual para "não existe" e "anulado há muito tempo", auditoria |
| R-08 | **Regras das lojas** para preço e link de pagamento fora do app | Reprovação na revisão da App Store | D-09 com chave do backend; conferir as regras antes de publicar (S4) |
| R-09 | **Anual no gateway**: renovação por PIX e boleto não é automática; troca de plano não tem crédito proporcional nativo | Assinatura anual expira sem a nutri perceber; cobrança errada na troca | Avisos de renovação, testes com relógio controlado, validação no sandbox do Asaas (G-08), D-11 |
| R-10 | **Conteúdo com valor legal ou clínico** (modelos de atestado e declaração) | Documento emitido com texto inadequado | D-06; modelos do sistema marcados como suposição até a revisão |
| R-11 | **Ambiente da nuvem**: sem Docker (Testcontainers) e sem compilar Android e iOS | Erro só aparece no CI | CI verde é obrigatório; `ops/validar-wsl.sh` quando precisar reproduzir; erros de import do alvo web aparecem só no job Web |
| R-12 | **Colisão de PRs paralelos** nos docs comuns e na numeração dos changesets | Retrabalho e fila no QA | Regras da seção 2 e da 5.2; BE-C9 escreve as regras de uma vez |
| R-13 | **Volume**: metas, registros e humor crescem por paciente e por dia | Sync inicial lento para nutri com 1.000 pacientes | A nutri recebe resumo de cumprimento pelo REST, e não cada registro; índices por paciente e data; medir no BE-74 |
| R-15 | **Água como meta** muda um registro que o app 1.3.x já usa todo dia | Água registrada no app antigo some da meta, ou a adesão e a evolução mudam de número | Caminho antigo aceito por 6 meses e lido como registro da meta; migração conferida por teste sobre base da 1.3.1; mesmos totais antes e depois |
| R-14 | **Escopo**: oito frentes numa release | A release atrasa inteira por causa de uma história | Histórias independentes; no corte, o que não estiver completo fica desligado ou vai para a 1.4.1 (ordem de corte: H-07 ativação, H-06, MOB-29d) |

## 9. Critérios para considerar a release pronta

### 9.1 Por tarefa (definição de pronto dos `AGENTS.md`)

- Backend: changeset novo quando mexe no banco; testes de regra, de API e de **isolamento** em todo endpoint novo de dado clínico; OpenAPI anotado; `CodigoErro` com o ID da regra; docs atualizados.
- App: testes de `dominio` e de ViewModel; tela nos 3 tamanhos, conferida com o artboard, com as divergências listadas no PR; funciona offline ou desabilita o que exige rede; strings em `strings.xml`.
- Todo PR: CI verde (`gitflow` e `ci-ok`), veredito `QA: APROVADO` e tarefa marcada `[x]` no `plano-de-acao.md` do projeto.

### 9.2 Da release

- [ ] As 50 tarefas da seção 4 estão `[x]` na `develop`, ou a exceção está escrita no PR da release com o motivo e o destino (1.4.1 ou 1.5.0).
- [ ] Todos os critérios de aceite da seção 3 conferidos pelo QA, história por história, com o resultado no PR da release.
- [ ] `funcionalidades-mvp.md` com F-22 a F-28 (uma por história) e `regras-de-negocio.md` e `integracao.md` iguais nas duas cópias.
- [ ] **Isolamento:** teste entre duas nutris para cada entidade nova (restrições, consulta ampliada, humor e fora do plano, metas, suplementos, orientações, documentos). De outra nutri é sempre `404`.
- [ ] **Projeção do paciente:** teste provando que observação interna, falta (D-03), gravidade, condições e medicamentos não saem em `/eu/...` nem no sync do paciente.
- [ ] **Contrato:** teste de contrato verde nos dois projetos com os JSONs do BE-C9 e do BE-C10; vetores `alertas-restricao.json` e `metas.json` idênticos nos dois lados.
- [ ] **Compatibilidade:** os JSONs de contrato da 1.3 continuam válidos; a migração roda sobre uma base da 1.3.1 com dados (consultas `ONLINE`, financeiro lançado, assinaturas) sem perda.
- [ ] **Sync:** envio idempotente e sem conflito para as coleções só de inclusão; teste de duas edições concorrentes do rascunho de dieta (MOB-29b).
- [ ] **LGPD:** exportação e exclusão de conta cobrem os dados novos; `log_acesso` grava leitura e escrita das rotas novas; nenhum log com dado de saúde.
- [ ] **Push e e-mail:** nenhum texto com dado clínico (teste nos eventos novos).
- [ ] **Segurança:** rotas públicas novas (`/planos` e conferência de documento) com limite de requisições e teste; `seguranca.yml` (CodeQL, dependências, Gitleaks) verde.
- [ ] **Ponta a ponta da web** (`e2e-web.yml`) verde, com os cenários novos da MOB-97.
- [ ] **CI completo no PR da release**, incluindo **iOS**, que só roda em `release/*`.
- [ ] **Acessibilidade:** a verificação com axe-core do e2e continua sem regressão nas telas novas.
- [ ] `CHANGELOG.md` dos dois projetos com a seção 1.4.0; versão 1.4.0 no backend e no app.
- [ ] Planos novos cadastrados com os valores da seção 7.1 e conferidos no sandbox do Asaas; ficam **inativos** se a conta do gateway (G-08) ainda não estiver validada. O checkout oferece PIX, cartão e boleto.

### 9.3 QA e teste manual

1. **QA por PR:** o agente QA do monorepo revisa cada PR contra a tarefa, as regras citadas, o contrato e o design, e faz o merge na `develop`.
2. **QA da release:** com a onda 27 na `develop`, abre-se `release/1.4.0`. O QA roda o roteiro abaixo na web contra o backend da mesma árvore e publica o veredito no PR da release. Correções entram por `bugfix/<desc>` saindo de `release/1.4.0`.
3. **Roteiro manual (G-27), no Android e no iPhone**, com os dados de exemplo (`--dieta.dev.dados-exemplo=true`):
   - Nutri registra alergia a glúten na ficha, monta a dieta com pão, confirma o alerta e publica; sem confirmar, a publicação é recusada.
   - Nutri agenda consulta online com link e orientação; o paciente vê "Entrar na consulta"; a nutri marca Realizada e emite a declaração de comparecimento; o paciente abre o PDF; o código confere na página pública.
   - Paciente em modo avião: registra refeição com humor, "comi fora do plano" e uma meta; ao voltar a rede, a nutri vê tudo na ficha.
   - Nutri prescreve suplemento e envia orientação; o paciente recebe o push genérico, vê os dois offline e recebe o lembrete local.
   - Nutri no desktop sem internet edita um rascunho de dieta e um modelo; ao reconectar, sincroniza; edição concorrente abre o diálogo de conflito.
   - Tela de assinatura mostra a contagem do teste e a comparação de planos.
4. **Publicação:** o Guilherme aprova `release/1.4.0 → main` (G-28). A pipeline cria `v1.4.0`, `backend-v1.4.0` e `mobile-v1.4.0` e abre o back-merge para a `develop`. Nenhuma tag manual sem o ok dele.

## 10. Depois da aprovação deste plano

1. **Feito em 16/11/2026.** O PO abre um PR por projeto copiando as tarefas das seções 4.2 e 4.3 para `backend/docs/arquitetura/plano-de-acao.md` e `mobile/docs/arquitetura/plano-de-acao.md` (fase "1.4.0"), e as tarefas `DES` para `mobile/docs/design/telas.md`, com as decisões D-xx já respondidas refletidas no texto.
2. O agente de design começa por DES-16 e DES-17; o backend, por BE-C9 e BE-83; o app, por MOB-29b.
3. Ao fim da onda 24, o PO mede o ritmo e atualiza este documento com a projeção das ondas seguintes.
