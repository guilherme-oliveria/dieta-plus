# Mapa de telas

Pacotes em `shared/src/commonMain/kotlin/org/br/dietamais/`, conforme o contrato combinado com a arquitetura. Artboard = arquivo no canvas https://claude.ai/artifact/6thej5zGsTo81ZosiigGxC. Fase = ver [plano-de-acao-ui.md](plano-de-acao-ui.md).

Status: **desenhada** (seguir o artboard com as correções de [validacao.md](validacao.md)), **faltando** (seguir a descrição abaixo e os componentes de [componentes.md](componentes.md)).

## Entrada (`ui/auth`)

| Tela | Composable | Artboard | Status | Fase |
| --- | --- | --- | --- | --- |
| Entrar | `EntrarTela` | `Entrar.dc.html` | desenhada; implementada MOB-06 | 1 |
| Cadastro do paciente por convite (código de 8 caracteres ou link, validade 7 dias) | `ConviteTela` | — | artboard faltando; implementada pelos componentes (MOB-08), cadastro condicionado aos termos publicados | 1 |
| Consentimento clínico e termos de uso separados (no aceite do convite) | `ConviteTela` + `TermosUsoParte` | — | artboard faltando; implementada pelos componentes (MOB-08), texto oficial do backend | 1 |
| Cadastro da nutri (nome, CRN, e-mail, senha; começa no teste) | `CadastroNutriTela` | — | artboard faltando; implementada pelos componentes (MOB-06) | 1 |
| Recuperar senha | `RecuperarSenhaTela` | — | artboard faltando; implementada pelos componentes (MOB-06) | 1 |
| Escolher perfil (conta que é nutri e paciente) | `EscolherPerfilTela` | — | artboard faltando; implementada pelos componentes (MOB-06) | 1 |

## Paciente

| Tela | Pacote / composable | Artboard | Status | Fase |
| --- | --- | --- | --- | --- |
| Hoje (dieta do dia, água, próxima refeição, check-in) | `ui/paciente/hoje` · `HojeScreen` | `Main.dc.html` | desenhada | 2 |
| Sem dieta publicada ainda | `ui/paciente/hoje` · estado de `HojeScreen` | — | faltando | 2 |
| Dieta / refeição detalhada | `ui/paciente/dieta` · `RefeicaoScreen` | `P-Refeicao.dc.html` | desenhada | 2 |
| Orientações gerais | `ui/paciente/dieta` · `OrientacoesScreen` | — | faltando | 2 |
| PDF da dieta | `ui/paciente/dieta` · `PdfDietaScreen` | — | faltando | 4 |
| Trocar alimento | `ui/paciente/substituicao` · `TrocaTela` | `P-Troca-Alimento.dc.html` (+ `P-Troca-Alimento-Vazio`) | desenhada | 2 |
| Lista de compras (3, 7, 15 dias) | `ui/paciente/compras` · `ComprasScreen` | `P-Compras.dc.html` | desenhada | 3 |
| Evolução | `ui/paciente/evolucao` · `EvolucaoScreen` | `P-Progresso.dc.html` | desenhada | 3 |
| Registrar peso (folha inferior) | `ui/paciente/evolucao` · `RegistrarPesoSheet` | — | faltando | 3 |
| Água (somar copo, meta 35 ml/kg) | `ui/paciente/hoje` · `AguaSheet` | — | faltando | 3 |
| Anamnese (wizard de 12 passos) | `ui/paciente/anamnese` · `AnamneseScreen` | `P-Anamnese.dc.html` (passo 7) | desenhada em parte: os outros 11 passos seguem o mesmo molde, blocos do doc do MVP | 3 |
| Perfil | `ui/paciente/perfil` · `PerfilScreen` | — | faltando | 2 |
| Lembretes e agenda (.ics) | `ui/paciente/perfil` · `LembretesScreen` | `P-Lembretes.dc.html` | desenhada | 3 |
| Minhas nutris (vínculos, trocar nutri ativa, autorizar cópia do histórico) | `ui/paciente/perfil` · `MinhasNutrisScreen` | — | faltando | 4 |
| Dados e privacidade (exportar, excluir conta) | `ui/paciente/perfil` · `PrivacidadeScreen` | — | faltando | 4 |

**Hoje, aviso de desfazer (MOB-UX-04):** depois de Comi/Pulei aparece, por 5 s, a faixa escura "Refeição registrada · Desfazer" no topo do conteúdo (também no detalhe da refeição). O canvas `Main.dc.html` precisa ganhar esse estado; a cópia local do artboard não existe.

Bottom nav do paciente: Hoje · Dieta · Compras · Evolução · Perfil.

## Nutri

| Tela | Pacote / composable | Artboard | Status | Fase |
| --- | --- | --- | --- | --- |
| Painel / Início (tablet e desktop) | `ui/nutri/painel` · `PainelScreen` | `T-Painel.dc.html` | desenhada | 2 |
| Painel / Início (celular) | `ui/nutri/painel` · `PainelScreen` compacto | — | faltando (coluna do tablet) | 2 |
| Pacientes | `ui/nutri/pacientes` · `PacientesScreen` | `N-Pacientes.dc.html` | desenhada | 2 |
| Convidar paciente | `ui/nutri/pacientes` · `ConvidarSheet` | — | faltando | 2 |
| Limite do plano (`LIMITE_PACIENTES_ATINGIDO`) | `ui/comum` · `FolhaLimitePlano` | — | faltando | 2 |
| Ficha do paciente (celular) | `ui/nutri/paciente` · `FichaScreen` | `N-Paciente.dc.html` | desenhada | 2 |
| Ficha do paciente (tablet/desktop, lista-detalhe) | `ui/nutri/paciente` · `FichaScreen` expandida | — | faltando (compor Pacientes + Ficha) | 2 |
| Nova avaliação | `ui/nutri/avaliacao` · `AvaliacaoScreen` | `T-Avaliacao.dc.html` | desenhada (tablet); celular faltando | 3 |
| Histórico e comparação de avaliações | `ui/nutri/avaliacao` · `HistoricoAvaliacoesScreen` | — | faltando | 4 |
| Montar dieta (estados RASCUNHO, PUBLICADA, SUBSTITUIDA) | `ui/nutri/dieta` · `MontarDietaScreen` | `T-Dieta.dc.html` | desenhada (tablet); celular faltando | 3 |
| Publicar dieta (diálogo com resumo da mudança) | `ui/nutri/dieta` · `PublicarDialog` | — | faltando | 3 |
| Agenda de consultas | `ui/nutri/agenda` · `AgendaScreen` | — | faltando | 4 |
| Alimentos (lista de substituição da nutri) | `ui/nutri/alimentos` · `AlimentosScreen` | — | faltando | 3 |
| Anamnese: modelo e respostas | `ui/nutri/anamnese` · `ModeloAnamneseScreen`, `RespostasAnamneseScreen` | — | faltando | 4 |
| Conta e assinatura (TESTE, ATIVA, EM_ATRASO, CANCELADA, EXPIRADA) | `ui/nutri/assinatura` · `ContaScreen` | — | faltando | 4 |
| Modo leitura (EM_ATRASO depois de 7 dias de carência) | `ui/nutri/assinatura` · `AvisoSomenteLeitura` | — | faltando | 4 |

Navegação da nutri (igual no celular e no trilho): Início · Pacientes · Agenda · Alimentos · Conta.

## Comum

| Tela | Pacote | Status | Fase |
| --- | --- | --- | --- |
| Central de avisos (sino) | `ui/notificacoes` · `AvisosScreen` | faltando | 3 |
| Diálogo de conflito de sincronização | `ui/comum` · `DialogoConflito` | faltando | 4 |
| Primeira sincronização do desktop | `ui/comum` · `PrimeiraSyncScreen` | faltando | 4 |

## Melhorias com as planilhas da nutri (ondas 17 a 23)

Desenhadas em 01/11/2026 (DES-08 a DES-10). Os artboards estão no canvas e em [artboards/](artboards/README.md); o padrão é [padrao-ui.md](padrao-ui.md) e os gráficos, [graficos.md](graficos.md).

| Tela | Pacote / composable | Artboard | Status | MOB |
| --- | --- | --- | --- | --- |
| Avaliação, passo 1 Medidas (celular) | `ui/nutri/avaliacao` · `AvaliacaoTela` | `A-Avaliacao-Medidas.dc.html` (substituído por ART-P1-03 a 10) | desenhada; implementada MOB-50 | 50 |
| Avaliação, passo 2 Composição (celular) | idem | `A-Avaliacao-Composicao.dc.html` (substituído por ART-P1-03 a 10) | desenhada; implementada MOB-50 | 50 |
| Avaliação, passo 3 Necessidades (rosca das metas) | `ui/nutri/avaliacao` · `PassoNecessidades` | `A-Avaliacao-Necessidades.dc.html` | desenhada; implementada MOB-52b | 52 |
| Avaliação, passo 4 Resumo (compartilhar por seção, PDF) | idem | `A-Avaliacao-Resumo.dc.html` (substituído por ART-P1-03 a 10) | desenhada; implementada sem "Gerar PDF" (D-05) | 50 |
| Avaliação em passos com painel de resultados (tablet e desktop) | `AvaliacaoTela` expandida · `PainelResultados` | `T-Avaliacao-Passos.dc.html` (substituído por ART-P1-03 a 10) | desenhada; implementada MOB-50 | 50 |
| Meus resultados (semáforos, composição, meta) | `ui/paciente/evolucao` · `MeusResultadosTela` | `P-Resultados.dc.html` | desenhada; implementada MOB-51 com divergência D-02 | 51 |
| Meus resultados, evolução com faixa | idem | `P-Resultados-Evolucao.dc.html` | desenhada; implementada MOB-51 (D-03) | 51 |
| Meus resultados, nada compartilhado | idem | `P-Resultados-Vazio.dc.html` | desenhada; implementada MOB-51 | 51 |
| Meus protocolos | `ui/nutri/protocolos` · `ProtocolosNutriCard` | `N-Protocolos.dc.html` | desenhada; implementada MOB-53 como cartão | 53 |
| Planejar dieta por porções (celular, uma refeição por vez) | `ui/nutri/dieta` · `PlanejadorPorcoesTela` | `A-Planejador-Porcoes.dc.html` | **desenhada**, falta implementar | 54 |
| Planejar dieta por porções (tablet, grade e metas) | idem expandida | `T-Planejador-Porcoes.dc.html` | **desenhada**, falta implementar | 54 |
| Biblioteca de modelos de dieta | `ui/nutri/dieta` · `ModelosDietaTela` | `A-Modelos-Biblioteca.dc.html` | desenhada; implementada MOB-56a (editar o conteúdo fica na 56b) | 56 |
| Aplicar modelo ao paciente | `ui/nutri/dieta` · `AplicarModeloTela` | `A-Modelo-Aplicar.dc.html` | **implementada** (MOB-57) | 57 |
| Avaliação infantil e adolescente | `ui/nutri/avaliacao` (campos por faixa) | `A-Avaliacao-Infantil.dc.html` | **implementada** (MOB-58; intervalos confirmados pelo servidor) | 58 |
| Curva de crescimento (nutri e responsável) | `ui/comum/graficos` · `CurvaCrescimentoConfirmada` | `P-Curva-Crescimento.dc.html` | **implementada** com snapshots disponíveis (MOB-59; limites em divergencias.md) | 59 |
| Exames: lançar (nutri) | `ui/nutri/avaliacao` · `ExamesTela` | `A-Exames-Nutri.dc.html` | **implementada** (MOB-60b; divergência: a classificação de um valor novo só aparece depois de salvar, vem do servidor) | 60 |
| Meus exames (paciente, barra de faixa) | `ui/paciente/evolucao` · `MeusExamesTela` | `P-Exames.dc.html` | **implementada** (MOB-60a) | 60 |
| Anamnese estruturada (álcool, sal, óleo) | `ui/paciente/anamnese` e `ui/nutri/anamnese` | `A-Anamnese-Estruturada.dc.html`, `P-Anamnese.dc.html` | **implementada**; adaptações em [mob-61-anamnese.md](mob-61-anamnese.md) | 61 |
| Catálogo de gráficos | `ui/comum/graficos` | `G-Graficos.dc.html` | referência | 47 |
| PDF da avaliação (A4) | backend (BE-46) | `PDF-Avaliacao.dc.html` | referência de layout | BE-46 |

Ainda **sem artboard** (pedir ao Design antes de implementar): micronutrientes e adequação à DRI (aba do planejador, MOB-54), suplementos e produtos no planejador (MOB-54), "salvar dieta como modelo" e editar modelo (MOB-56), PDF da dieta (BE-50).

### Paciente: dieta com macros e lista de substituição (DES-11, 01/11/2026)

| Tela | Pacote / composable | Artboard | Status | MOB |
| --- | --- | --- | --- | --- |
| Dieta com rosca de macros do dia | `ui/paciente/dieta` · `DietaPacienteTela` | `P-Dieta-Macros.dc.html` | **implementada** (MOB-55; ressalvas em divergencias.md) | 55 |
| Refeição com rosca de macros, "1 porção" e "Trocar" | `ui/paciente/dieta` · refeição | `P-Refeicao-Macros.dc.html` | **implementada** (MOB-55; ressalvas em divergencias.md) | 55 |
| Lista de substituição para consulta (celular) | `ui/paciente/substituicao` · `ListaSubstituicaoTela` | `P-Lista-Substituicao.dc.html` | **implementada** (MOB-55; ressalvas em divergencias.md) | 55 |
| Lista de substituição, sem lista publicada | idem (estado vazio) | `P-Lista-Substituicao-Vazio.dc.html` | **implementada** (MOB-55; ressalvas em divergencias.md) | 55 |
| Lista de substituição (tablet, lista-detalhe) | idem expandida | `T-Lista-Substituicao.dc.html` | **implementada** (MOB-55; ressalvas em divergencias.md) | 55 |
| PDF da lista: porção pequena, média, grande | backend (BE-50b) | `PDF-Lista-Pequena`, `PDF-Lista-Media`, `PDF-Lista-Grande` | referência de layout | BE-50b |
| PDF da lista com contagem de carboidratos | backend (BE-50b) | `PDF-Lista-Carboidratos.dc.html` | referência de layout | BE-50b |

## Release 1.4.0 (ondas 24 a 27)

Plano em [`docs/plano-de-execucao-1.4.0.md`](../../../docs/plano-de-execucao-1.4.0.md) (seção 4.1). **Todas faltando**: cada tela entra com o artboard da `DES-NN` aprovado no canvas, copiado para [artboards/](artboards/README.md), e o status muda aqui para "desenhada" no PR da própria DES (branch `feature/DES-NN-…`). O benchmark é referência de fluxo: textos, ícones, cores e layout são do Dieta+ ([padrao-ui.md](padrao-ui.md)). Estados de carregando, vazio, erro e sem internet seguem [componentes.md](componentes.md). A MOB-29b não tem tela nova: usa `IndicadorSync`, `DialogoConflito` e `FaixaOffline`.

| Tela | Pacote | DES | Status | MOB |
| --- | --- | --- | --- | --- |
| Ficha: seção "Saúde e restrições" e selo no cabeçalho; folha de novo item | `ui/nutri/paciente` | DES-16 | desenhada: `A-Saude-Ficha.dc.html`, `A-Saude-Item.dc.html` | 82 |
| Montador: alerta de intolerância e confirmação de alergia | `ui/nutri/dieta` | DES-16 | desenhada: `A-Montador-Alerta.dc.html`, `A-Montador-Confirmar.dc.html` | 83 |
| Consulta: formulário (tipo, modalidade, link, orientação, observação) e ações de status | `ui/nutri/agenda` | DES-17 | desenhada: `A-Consulta-Form.dc.html`, `A-Consulta-Status.dc.html` | 84 |
| Agenda: visão do mês | `ui/nutri/agenda` | DES-17 | desenhada: `A-Agenda-Mes.dc.html` | 85 |
| Conta da nutri: preferência de lembrete | `ui/nutri/assinatura` (conta) | DES-17 | desenhada: `A-Conta-Lembrete.dc.html` | 86 |
| Paciente: próxima consulta com "Entrar na consulta" e orientação | `ui/paciente/hoje` | DES-17 | desenhada: `P-Proxima-Consulta.dc.html` | 87 |
| Registro da refeição: fome antes e sensação depois; "Comi algo fora do plano"; preferência no perfil | `ui/paciente/hoje`, `ui/paciente/perfil` | DES-18 | desenhada: `P-Refeicao-Humor.dc.html`, `P-Fora-Plano.dc.html`, `P-Perfil-Preferencias.dc.html` | 88 |
| Adesão do paciente: humor da semana e registros fora do plano | `ui/nutri/paciente` | DES-18 | desenhada: `A-Adesao-Humor.dc.html` | 89 |
| Hoje com as metas (a água é uma delas, com desfazer e valor livre); registro, sequência e progresso | `ui/paciente/hoje` | DES-19 | desenhada: `P-Hoje-Metas.dc.html`, `P-Metas-Semana.dc.html` | 90, 99 |
| Metas do paciente: cadastro, modelos e cumprimento na ficha | `ui/nutri/paciente` | DES-19 | desenhada: `A-Metas-Paciente.dc.html`, `A-Meta-Nova.dc.html`, `A-Modelos-Meta.dc.html` | 91 |
| Prescrever suplemento, orientação avulsa e modelos | `ui/nutri/paciente` | DES-20 | desenhada: `A-Suplementos-Paciente.dc.html`, `A-Suplemento-Novo.dc.html`, `A-Orientacao-Nova.dc.html` | 92 |
| "Suplementos" e "Orientações" do paciente | `ui/paciente/dieta` | DES-20 | desenhada: `P-Suplementos.dc.html`, `P-Orientacoes.dc.html` | 93 |
| Documentos: modelos, emitir, anular e lista na ficha; layout do PDF e da página de conferência | `ui/nutri/paciente` | DES-21 | desenhada: `A-Documentos-Paciente.dc.html`, `A-Documento-Emitir.dc.html`, `A-Documento-Anular.dc.html`, `A-Modelos-Documento.dc.html`, `PDF-Declaracao.dc.html`, `W-Conferir-Documento.dc.html` | 94 |
| "Documentos" do paciente | `ui/paciente/perfil` | DES-21 | desenhada: `P-Documentos.dc.html` | 95 |
| Assinatura: comparar planos (mensal/anual), uso dos limites, contagem do teste e aviso no painel | `ui/nutri/assinatura` | DES-22 | desenhada: `A-Assinatura-Planos.dc.html`, `A-Assinatura-Ativa.dc.html`, `A-Assinatura-Sem-Preco.dc.html`, `A-Painel-Fim-Teste.dc.html` | 96 |

Os pacotes são a proposta do PO; a DES e a tarefa MOB podem ajustar, dizendo no PR.
