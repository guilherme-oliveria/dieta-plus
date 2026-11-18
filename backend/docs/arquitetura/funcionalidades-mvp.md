# Dieta+ — funcionalidades do MVP

> Documento comum aos 3 repositórios. Cópia oficial em `dieta-backend/docs/arquitetura/`.
> MVP = backend + app (Android, iOS, Desktop). O site Angular é pós-MVP.
> Cada funcionalidade tem ID `F-xx`, fase (ver `visao-geral.md`), regras que aplica e critérios de aceite.
> As telas de cada funcionalidade estão em `dieta-mobile/docs/design/telas.md`.

## F-01 Cadastro e login da nutri — fase 1

Regras: RN-CON-01 a 05, RN-CON-07, RN-ASS-01, RN-ASS-02.

- [ ] Nutri se cadastra com nome, e-mail, senha, CRN e aceite dos termos; a conta nasce no plano Teste.
- [ ] Recebe e-mail de confirmação; sem confirmar, consegue entrar mas não convida paciente.
- [ ] Login devolve tokens; o app mantém a sessão por 30 dias com refresh automático.
- [ ] 5 senhas erradas bloqueiam o e-mail por 15 min, sem revelar se o e-mail existe.
- [ ] "Esqueci a senha" funciona por e-mail.

## F-02 Pacientes e convite — fase 1

Regras: RN-VIN-01 a 05, RN-VIN-07, RN-ASS-03.

- [ ] Nutri vê a lista dos seus pacientes com busca por nome e status (convidado, ativo, encerrado).
- [ ] Nutri cria a ficha (nome, e-mail, nascimento, sexo, estatura, objetivo) e gera convite (código + link para compartilhar por WhatsApp).
- [ ] No Teste, o 6º convite é recusado com aviso de limite; no Básico, o 21º.
- [ ] Nutri reenviar convite invalida o anterior.
- [ ] Nutri encerra o vínculo; a vaga é liberada.

## F-03 Aceite do convite pelo paciente — fase 1

Regras: RN-VIN-02 a 04, RN-CON-06.

- [ ] Paciente abre o link ou digita o código no app e vê o nome e o CRN da nutri.
- [ ] Cria a senha (ou entra, se já tem conta) e aceita o termo de consentimento.
- [ ] Após o aceite, o vínculo fica ativo e a nutri vê "Ativo" na lista.
- [ ] Código expirado ou já usado mostra mensagem clara e opção de pedir novo convite à nutri.

## F-04 Lista de alimentos da nutri — fase 2

Regras: RN-ALI-01 a 03.

- [ ] Catálogo base carregado com os 12 grupos e os alimentos do PDF de substituição.
- [ ] Nutri vê sua lista por grupo, busca, adiciona, edita e desativa alimentos.
- [ ] Nutri ajusta a lista de um paciente (remover/adicionar alimentos) sem afetar os outros.

## F-05 Montador de dieta — fase 2

Regras: RN-DIE-01 a 06, RN-DIE-09.

- [ ] Nutri cria um rascunho com refeições (nome + horário) e itens (alimento + porções), vendo kcal e macros por refeição e no dia em tempo real.
- [ ] Pode duplicar uma dieta anterior ou partir de um modelo.
- [ ] Publicar exige internet, congela a dieta e a lista de substituição, e substitui a dieta anterior.
- [ ] Dieta publicada não é editável; "Editar" cria novo rascunho.
- [ ] Dieta sem itens não publica.

## F-06 Dieta do dia (paciente) — fase 2

Regras: RN-DIE-03, RN-DIE-07, RN-ASS-05.

- [ ] Tela "Hoje" mostra as refeições do dia em ordem de horário, com a próxima em destaque, medidas caseiras e kcal.
- [ ] Funciona sem internet depois do primeiro sync.
- [ ] Quando chega dieta nova, o paciente é avisado e a tela atualiza.
- [ ] Paciente com mais de uma nutri escolhe a dieta em uso.

## F-07 Trocar alimento — fase 2

Regras: RN-SUB-01 a 03.

- [ ] Ao tocar num item, o paciente vê só os substitutos do mesmo grupo da lista dele, já na quantidade equivalente.
- [ ] Escolher um substituto registra check-in `TROCADO`.
- [ ] Funciona offline.

## F-08 Lembretes de refeição e água — fase 3

Regras: RN-ROT-03, RN-ROT-05, RN-NOT-03.

- [ ] Ao receber/trocar a dieta em uso, o app agenda notificações locais nos horários das refeições (Android, iOS e desktop).
- [ ] A notificação tem ações "Comi", "Troquei" e "Pulei" (Android e iOS).
- [ ] Lembrete de água ao longo do dia até a meta (35 ml/kg), respeitando horário de silêncio.
- [ ] Usuário liga/desliga cada tipo.

## F-09 Check-in e água — fase 3

Regras: RN-ROT-01 a 03.

- [ ] Paciente marca cada refeição como feita, trocada ou pulada, até 3 dias para trás.
- [ ] Contador de água com meta do dia.
- [ ] Registros feitos offline são enviados quando a conexão volta.
- [ ] Nutri vê a adesão (% de refeições feitas nos últimos 7 dias) na ficha e na lista de pacientes.

## F-10 Lista de compras — fase 3

Regras: RN-ROT-04.

- [ ] Paciente gera a lista para N dias (padrão 7) a partir da dieta em uso, agrupada por grupo de alimentos, com quantidades somadas em gramas e medida caseira.
- [ ] Marca itens comprados; funciona offline.
- [ ] Pode compartilhar a lista como texto (WhatsApp).

## F-11 Agenda do celular (.ics) — fase 3

Regras: integracao.md, seção Agenda.

- [ ] Paciente toca em "Adicionar ao Google Agenda" ou "Calendário da Apple" e as refeições aparecem como eventos diários.
- [ ] Quando a dieta muda, a agenda atualiza sozinha (feed assinado).
- [ ] Paciente pode revogar o link.

## F-12 Avaliação antropométrica — fase 4

Regras: RN-AVA-01 a 05.

- [ ] Nutri registra peso, estatura, 9 perímetros, 7 dobras, composição digitada e parecer.
- [ ] O backend calcula IMC e classificação.
- [ ] Funciona offline no tablet/desktop (fase 5) e online no celular (fase 4).
- [ ] Paciente vê as avaliações compartilhadas.

## F-13 Evolução — fase 4

- [ ] Nutri e paciente veem gráfico de peso, % gordura e perímetros ao longo das avaliações, mais o peso registrado em casa pelo paciente.

## F-14 Anamnese — fase 4

Regras: RN-ANA-01 a 05.

- [ ] Nutri tem o modelo padrão e pode editar perguntas.
- [ ] Nutri envia o questionário; o paciente recebe push e responde no app em passos, podendo salvar e continuar.
- [ ] Ao enviar, a nutri é notificada e vê as respostas na ficha.

## F-15 Agenda de consultas — fase 4

Regras: RN-AGE-01, RN-AGE-02.

- [ ] Nutri agenda consulta para paciente vinculado e vê a agenda do dia/semana.
- [ ] Paciente vê a próxima consulta; ambos recebem lembrete 24 h antes.

## F-16 Push e central de avisos — fase 4

Regras: RN-NOT-01 a 03.

- [ ] Push nos eventos de RN-NOT-01, sem dado clínico no texto.
- [ ] Central de avisos no app com o histórico.

## F-17 Painel da nutri — fase 4

- [ ] Tela inicial com consultas do dia, pacientes sem check-in há 3 dias, questionários respondidos e status da assinatura.

## F-18 Offline da nutri e conflitos — fase 5

Regras: RN-SYN-01 a 05.

- [ ] Nutri usa o desktop/tablet sem internet: consulta pacientes, registra avaliação, edita rascunho de dieta.
- [ ] Ao reconectar, tudo é enviado; conflitos mostram as duas versões e a nutri escolhe.
- [ ] Indicador visível de "offline" e de "N alterações pendentes".

## F-19 Assinatura — fase 6

Regras: RN-ASS-01 a 08.

- [ ] Nutri vê plano, status, limite e uso (ex.: 14/20 pacientes).
- [ ] Botão "Assinar" abre o checkout do gateway no navegador; ao pagar, o app reflete o novo status.
- [ ] Atraso, carência e bloqueio de edição funcionam como nas regras; paciente não é afetado.

## F-20 PDF da dieta e da avaliação — fase 6

- [ ] Nutri gera o PDF da dieta publicada e da avaliação, com nome, CRN e logo, sem as páginas vazias do modelo atual.
- [ ] Compartilha pelo sistema (WhatsApp, e-mail).

## F-21 Privacidade (LGPD) — fase 6

Regras: RN-LGPD-01 a 05.

- [ ] Paciente exporta os próprios dados e pede exclusão da conta.
- [ ] Log de acesso a dados clínicos gravado e consultável pelo admin (sem ver o conteúdo).

## F-22 Alergias e intolerâncias na ficha, com alerta no montador — release 1.4.0 (H-01)

Regras: RN-SAU-01 e 02, RN-DIE-14, RN-MOD-03.

- [ ] A ficha tem a seção "Saúde e restrições" com itens de quatro tipos (alergia, intolerância, condição e medicamento), cada um com nome, gravidade opcional e observação; o cabeçalho mostra um selo com as restrições ativas.
- [ ] Alergia e intolerância se ligam a marcadores de alimento (lista ampliada com amendoim, frutos do mar, ovo, soja e outros, com sugestão inicial por nome no catálogo).
- [ ] No montador, item com marcador de intolerância mostra o alerta e deixa seguir; de alergia, pede confirmação explícita. O backend recalcula os alertas ao salvar e recusa publicar dieta com alergia não confirmada (`ALERGIA_NAO_CONFIRMADA`).
- [ ] Dieta nova nasce com as restrições da ficha, e a lista de substituição publicada sai sem os alimentos marcados.
- [ ] Funciona offline no desktop e no tablet; o paciente nunca vê a seção.

## F-23 Consulta com status, modalidade e link online — release 1.4.0 (H-02)

Regras: RN-AGE-03 a 05, RN-NOT-02.

- [ ] Status Agendada, Realizada, Falta e Cancelada, com transições validadas no backend; para o paciente, Realizada e Falta aparecem como encerrada.
- [ ] Modalidade Presencial ou Online, separada do tipo; o tipo antigo `ONLINE` continua aceito (vira Retorno + Online). Consulta online aceita link `https`, e o paciente vê "Entrar na consulta".
- [ ] "Orientação para o paciente" aparece para ele; "Observação interna" nunca sai na projeção do paciente.
- [ ] A nutri escolhe a antecedência do lembrete (2 h, 1 dia ou 2 dias) e o lembrete extra 1 h antes; e-mail de confirmação ao agendar. Agenda com visão do mês.
- [ ] Push e `.ics` sem dado clínico, sem link e sem orientação.

## F-24 Humor e "comi fora do plano" — release 1.4.0 (H-03)

Regras: RN-ROT-02, RN-ROT-06, RN-SYN-03.

- [ ] No registro da refeição, as escalas opcionais "fome antes" e "como ficou depois" (1 a 5); o paciente pode desligar a pergunta no perfil.
- [ ] Botão "Comi algo fora do plano": texto de até 280 caracteres, refeição opcional e horário; só inclusão, offline, sem conflito.
- [ ] A nutri vê na adesão as médias da semana e a lista dos registros fora do plano.
- [ ] Humor e registros fora do plano entram na exportação e na auditoria.

## F-25 Metas de hábito — release 1.4.0 (H-04)

Regras: RN-MET-01 a 05.

- [ ] A nutri cria metas (água, atividade, passos, sono, frutas e verduras, sim/não e contador) com alvo, frequência diária ou N vezes por semana e início e fim opcionais, e mantém modelos aplicáveis a vários pacientes.
- [ ] A água é uma meta: todo paciente tem a meta de água, com alvo padrão de 35 ml × peso mais recente que a nutri ajusta; os registros antigos foram migrados e o caminho antigo (`POST /eu/agua`) continua aceito.
- [ ] O paciente marca no Hoje, offline, e vê a sequência e o progresso da semana, calculados por função pura com os vetores de `contrato/calculos/metas.json`.
- [ ] A nutri vê o cumprimento das metas na ficha. Sem pontos nem níveis.

## F-26 Suplementos e orientações — release 1.4.0 (H-05)

Regras: RN-SUP-01 e 02, RN-ORI-01, RN-ASS-05, RN-NOT-02.

- [ ] A nutri prescreve suplemento por produto ou texto livre (dose, forma, frequência, horários, início e fim); a prescrição guarda cópia do nome e da dose.
- [ ] Orientação avulsa com título e Markdown restrito (negrito, itálico e listas, nunca HTML), com modelos da nutri; nova orientação dispara push genérico.
- [ ] O paciente vê "Suplementos" e "Orientações" offline, mesmo com a assinatura da nutri inativa.
- [ ] O PDF da dieta pode incluir os suplementos ativos (`incluirSuplementos`).

## F-27 Atestado e declaração de comparecimento — release 1.4.0 (H-06)

Regras: RN-DOC-01 a 03.

- [ ] Dois modelos do sistema somente leitura (atestado e declaração), com campos de preenchimento; a nutri copia para editar.
- [ ] O documento emitido é imutável (texto final e PDF); corrigir é emitir outro, e anular exige motivo. A declaração pode partir de consulta Realizada.
- [ ] Cada documento tem código de conferência; a rota pública confirma tipo, emissor, CRN, data e situação, sem nenhum dado do paciente, com limite de requisições.
- [ ] O paciente vê os documentos dele em "Documentos" (lista offline; o PDF abre online, por link assinado de 10 min). Emissão, anulação e conferência ficam na auditoria.

## F-28 Plano anual e troca de plano — release 1.4.0 (H-07)

Regras: RN-ASS-06 a 11.

- [ ] `GET /api/v1/planos` (público) lista os planos à venda, com preço mensal e anual e limite de pacientes (ou sem limite); o admin edita os campos novos, com log.
- [ ] O checkout aceita plano e periodicidade (mensal ou anual), continua fora do app e aceita PIX, cartão e boleto; o webhook trata a renovação anual.
- [ ] Com assinatura ativa, subir de plano vale na hora e descer vale no próximo vencimento (RN-ASS-08: ninguém perde paciente).
- [ ] Tela de assinatura compara planos com alternância mensal/anual, uso dos limites e "faltam N dias de teste"; aviso no painel e por e-mail 7 dias e 1 dia antes do fim do teste.

## Fora do MVP

Site Angular (P1). Troca de nutri com cópia de histórico, clínicas com várias nutris e secretária, vitrine de nutris, consultoria online paga com split, anúncios, foto do prato, chat, fórmulas da planilha (7 dobras), importação da planilha, receitas, Google Fit/Apple Saúde, RabbitMQ (P2).
