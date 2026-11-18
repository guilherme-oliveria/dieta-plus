# Índice: artboard por tarefa

Para quem vai implementar (Codex, Claude, pessoas). Cada `.dc.html` desta pasta é a **especificação visual** de uma tela: abra o arquivo e leia o HTML (layout, cores, tamanhos, textos e dados de exemplo). Eles não abrem sozinhos no navegador (dependem do `support.js` do canvas), mas as fotos (PNG) de cada um estão em [../evidencias/DES-08-10/](../evidencias/DES-08-10/) e [../evidencias/DES-11/](../evidencias/DES-11/). Antes de implementar leia [../padrao-ui.md](../padrao-ui.md) e [../graficos.md](../graficos.md); as divergências conhecidas do app estão em [../divergencias.md](../divergencias.md). Regra: o PR cita o artboard e lista as divergências.

Estado conferido na `develop` em 01/11/2026 (plano de ação, linha de MOB-49 em diante).

## Release 1.4.0 (DES-16 a DES-22)

Fotos em `../evidencias/DES-NN/`. Dados de exemplo fictícios (paciente "Ana Lima").

| Tarefa | Artboards (nesta pasta) | Ver também |
| --- | --- | --- |
| **MOB-82** Ficha: saúde e restrições | `A-Saude-Ficha` (seção, selo no cabeçalho, aviso de que o paciente não vê), `A-Saude-Item` (folha de novo/editar item) | Selo laranja só para alergia grave; intolerância em azul. Marcadores sugeridos pelo nome, editáveis |
| **MOB-83** Montador: alerta e confirmação | `A-Montador-Alerta` (alerta de alergia com Revisar/Trocar, aviso de intolerância, Publicar bloqueado), `A-Montador-Confirmar` (diálogo de confirmação) | Intolerância só avisa; alergia exige confirmação por item antes de publicar (`ALERGIA_NAO_CONFIRMADA`) |
| **MOB-84** Agenda: formulário e status | `A-Consulta-Form` (tipo, modalidade, link, orientação, observação interna), `A-Consulta-Status` (Realizada, Faltou, Remarcar, Cancelar) | Link só aparece com modalidade Online. Realizada/Falta atualizam o financeiro (RN-FIN-01); para o paciente viram "Consulta encerrada" |
| **MOB-85** Agenda: visão do mês | `A-Agenda-Mes` (Dia/Semana/Mês, pontos por consulta, horário livre abre a nova consulta) | Hoje com contorno, dia escolhido preenchido |
| **MOB-86** Preferência de lembrete | `A-Conta-Lembrete` (2 h, 1 dia, 2 dias; extra 1 h antes; e-mail de confirmação) | Vale para a nutri e para os pacientes (D-08). Push sem dado clínico (RN-NOT-02) |
| **MOB-87** Paciente: próxima consulta | `P-Proxima-Consulta` (cartão no Hoje com "Entrar na consulta" e a orientação) | O `.ics` não leva link nem orientação |
| **MOB-88** Paciente: humor e fora do plano | `P-Refeicao-Humor` (folha após "Comi", duas escalas de 5 níveis, Pular/Salvar, Desfazer continua), `P-Fora-Plano` (texto até 280, refeição opcional, horário), `P-Perfil-Preferencias` (desligar a pergunta, só no aparelho) | Ícone de nível = barras crescentes do Dieta+, sem emoji. Só inclusão, nunca conflito (RN-ROT-06) |
| **MOB-89** Nutri: humor e fora do plano na adesão | `A-Adesao-Humor` (médias da semana e lista fora do plano) | Média só das refeições com resposta |
| **MOB-90** e **MOB-99** Paciente: metas e água como meta | `P-Hoje-Metas` (cartões no Hoje; água com +250/+500, Outro valor e Desfazer último), `P-Metas-Semana` (dias da semana, sequência, lembrete local por meta) | Sem pontos nem níveis. Lembrete respeita o horário de silêncio (RN-NOT-03) |
| **MOB-91** Nutri: metas, modelos e cumprimento | `A-Metas-Paciente` (cumprimento na semana, abaixo de 50% em laranja), `A-Meta-Nova` (tipo, alvo, frequência, início e fim), `A-Modelos-Meta` (aplicar modelo) | Água mostra o alvo padrão de 35 ml/kg, ajustável (RN-MET-05) |
| **MOB-92** Nutri: prescrever e orientar | `A-Suplementos-Paciente` (em uso e encerrados), `A-Suplemento-Novo` (meus produtos ou texto livre, dose, forma, horário, frequência, fim), `A-Orientacao-Nova` (título, negrito e lista, usar e salvar modelo) | Cópia congelada de nome e dose (RN-SUP-01). Texto em Markdown restrito, nunca HTML |
| **MOB-93** Paciente: suplementos e orientações | `P-Suplementos` (lembrete local por suplemento), `P-Orientacoes` (nova aberta e anteriores) | Offline. Push "Você tem uma nova orientação" sem nome do suplemento |
| **MOB-96** Assinatura: comparar planos | `A-Assinatura-Planos` (teste com dias restantes e uso, Mensal/Anual, três planos, botão que abre o navegador), `A-Assinatura-Ativa` (uso perto do limite, trocar de plano), `A-Assinatura-Sem-Preco` (variante quando a chave do servidor desliga preço e botão, D-09), `A-Painel-Fim-Teste` (aviso no painel) | Valores da seção 7.1 vêm do servidor (`planosDisponiveis`), nunca fixos no app. Troca: subir na hora, descer no vencimento (D-11) |

## Mobile pendente

Plano de melhorias (05/11): as telas novas seguem os IDs `ART-P1-nn` a `ART-P4-nn` de [ART-IDS.md](ART-IDS.md). MOB-63 (4 gráficos): ART-P1-09 e ART-P1-10; MOB-64: ART-P1-02 a 08; MOB-65 e MOB-66: ART-P4-04 e ART-P4-05; MOB-67: ART-P1-01 e ART-P1-12; MOB-68 e MOB-69: ART-P2-01 a 05; MOB-56b e MOB-70 a 76: ART-P3-01 a 09; MOB-77 e 78: ART-P4-02 e ART-P4-03.

| Tarefa | O que falta | Artboards (nesta pasta) | Ver também |
| --- | --- | --- | --- |
| **MOB-55** Paciente: rosca de macros e lista de substituição | Implementado; limitações de metadados em divergencias.md | `P-Dieta-Macros`, `P-Refeicao-Macros`, `P-Lista-Substituicao`, `P-Lista-Substituicao-Vazio`, `T-Lista-Substituicao` | `padrao-ui.md` seção 8b; `graficos.md` (rosca sem anel de meta para o paciente) |
| **MOB-56b** Editar o conteúdo do modelo e leitura offline | Edição do modelo reaproveita o planejador | `A-Planejador-Porcoes`, `T-Planejador-Porcoes` (edição); biblioteca em `A-Modelos-Biblioteca` (já feita, 56a) | Sem artboard próprio da edição: use o planejador com o título "Editar modelo" e sem paciente |
| **MOB-58** Avaliação infantil e adolescente | Tudo | `A-Avaliacao-Infantil` | `plano-melhorias-planilhas.md` 3.3 (campos por faixa) |
| **MOB-59** Curva de crescimento (nutri e responsável) | Tudo | `P-Curva-Crescimento` (usa `CurvaCrescimento`, já existe em `ui/comum/graficos`) | `graficos.md` item "Curva" |
| **MOB-61** Anamnese estruturada (álcool, sal, óleo) | Implementado; resultados confirmados do servidor, adaptações documentadas | `A-Anamnese-Estruturada`, `P-Anamnese` | `../mob-61-anamnese.md`; contrato-anamnese.md |
| Divergências D-03, D-05, D-06 | Série de gordura em Meus resultados, "Gerar PDF" no Resumo, botão "i" de explicação | `P-Resultados-Evolucao`, `A-Avaliacao-Resumo` (substituído por `A-Avaliacao-Graficos-2`, ART-P1-10) | `divergencias.md` |

## Mobile já entregue (a tela conferida com o artboard)

| Tarefa | Artboards |
| --- | --- |
| MOB-50 Avaliação em passos | `A-Avaliacao-Medidas`, `A-Avaliacao-Composicao`, `A-Avaliacao-Resumo`, `T-Avaliacao-Passos` (removidos em 05/11: substituídos por ART-P1-03 a 10, ver `ART-IDS.md`) |
| MOB-51 e MOB-51b Meus resultados | `P-Resultados`, `P-Resultados-Evolucao`, `P-Resultados-Vazio` |
| MOB-52b Necessidades | `A-Avaliacao-Necessidades` |
| MOB-53 Meus protocolos | `N-Protocolos` |
| MOB-54b Planejador por porções | `A-Planejador-Porcoes`, `T-Planejador-Porcoes` |
| MOB-56a e MOB-57 Modelos | `A-Modelos-Biblioteca`, `A-Modelo-Aplicar` |
| MOB-60a e MOB-60b Exames | `P-Exames`, `A-Exames-Nutri` |

## Referência de PDF (para o backend; o app só abre o PDF)

| Tarefa | Artboards |
| --- | --- |
| BE-46 PDF da avaliação | `PDF-Avaliacao` |
| BE-50b PDF da lista de substituição (pequena, média, grande, com contagem de carboidratos) | `PDF-Lista-Pequena`, `PDF-Lista-Media`, `PDF-Lista-Grande`, `PDF-Lista-Carboidratos` |
| BE-50 PDF da dieta | **sem artboard**: use o layout de `PDF-Avaliacao` (cabeçalho, rodapé com CRN) com a coluna "Substituições: 1 porção do grupo X" |

Nos PDFs de lista o desenho varia só as gramas; a medida caseira também deve escalar (arredondando para ¼).

## Padrão e catálogo

`G-Graficos` (catálogo dos gráficos e estados) e `canvas.json` (posição de cada artboard no canvas https://claude.ai/artifact/6thej5zGsTo81ZosiigGxC). Telas anteriores a 24/10 (Hoje, Refeição, Trocar, Compras, Evolução, Lembretes, Anamnese passo 7, Entrar, Pacientes, Ficha, Painel, Dieta, Avaliação tablet) estão listadas em [README.md](README.md).

## Sem artboard ainda (peça ao Design antes, ou siga `padrao-ui.md` e os componentes)

Micronutrientes e adequação à DRI (aba do planejador), suplementos e produtos no planejador, editar modelo (usa o planejador), PDF da dieta (BE-50).

## IDs novos (nov/2026)

Os artboards ART-Pn-nn de `ART-IDS.md` substituem os de avaliação do adulto e acrescentam infantil, dieta (12 abas da planilha) e anamnese/exames. Cada tarefa do plano cita o ID esperado.
