# Artboards do canvas (cópia)

Cópia dos artboards do canvas https://claude.ai/artifact/6thej5zGsTo81ZosiigGxC, para agentes sem acesso ao claude.ai (Codex e outros) lerem o desenho de cada tela.

- **O canvas é a fonte.** Estes arquivos são uma foto de 01/11/2026 (13 telas de 24/10 + 20 artboards de DES-08 a DES-10). Quando uma tarefa `DES-NN` desenhar ou mudar telas no canvas, o mesmo PR atualiza esta pasta.
- Cada `*.dc.html` é o HTML de uma tela: layout, cores, tamanhos, textos e os dados de exemplo (no `renderVals()` do script, no fim do arquivo). Não abre sozinho no navegador (depende do `support.js` do canvas); leia como especificação.
- `canvas.json` dá o título e o tamanho de cada artboard (celular 390×844, tablet 1180×820).
- Antes de implementar, aplique as correções de [../validacao.md](../validacao.md) (cores de contraste, abas da nutri, "Sou nutricionista" etc.): elas vencem o artboard.
- Mapa artboard → composable em [../telas.md](../telas.md).

| Arquivo | Tela |
| --- | --- |
| `Main.dc.html` | Paciente · Hoje |
| `P-Refeicao.dc.html` | Paciente · Refeição |
| `P-Substituir.dc.html` | Paciente · Trocar alimento |
| `P-Compras.dc.html` | Paciente · Lista de compras |
| `P-Progresso.dc.html` | Paciente · Evolução |
| `P-Lembretes.dc.html` | Paciente · Lembretes e agenda |
| `P-Anamnese.dc.html` | Paciente · Anamnese (passo 7) |
| `Entrar.dc.html` | Entrar |
| `N-Pacientes.dc.html` | Nutri · Pacientes (celular) |
| `N-Paciente.dc.html` | Nutri · Ficha do paciente (celular) |
| `T-Painel.dc.html` | Nutri · Painel (tablet) |
| `T-Dieta.dc.html` | Nutri · Montar dieta (tablet) |
| `T-Avaliacao.dc.html` | Nutri · Avaliação (tablet) |
| `A-Avaliacao-Medidas.dc.html` | Nutri · Avaliação, passo 1 Medidas (celular) |
| `A-Avaliacao-Composicao.dc.html` | Nutri · Avaliação, passo 2 Composição |
| `A-Avaliacao-Necessidades.dc.html` | Nutri · Avaliação, passo 3 Necessidades (rosca das metas) |
| `A-Avaliacao-Resumo.dc.html` | Nutri · Avaliação, passo 4 Resumo |
| `T-Avaliacao-Passos.dc.html` | Nutri · Avaliação em passos com painel de resultados (tablet) |
| `P-Resultados.dc.html`, `P-Resultados-Evolucao.dc.html`, `P-Resultados-Vazio.dc.html` | Paciente · Meus resultados |
| `N-Protocolos.dc.html` | Nutri · Meus protocolos |
| `A-Planejador-Porcoes.dc.html`, `T-Planejador-Porcoes.dc.html` | Nutri · Planejar dieta por porções (celular e tablet) |
| `A-Modelos-Biblioteca.dc.html`, `A-Modelo-Aplicar.dc.html` | Nutri · Modelos de dieta |
| `A-Avaliacao-Infantil.dc.html`, `P-Curva-Crescimento.dc.html` | Criança: avaliação e curva de crescimento |
| `A-Exames-Nutri.dc.html`, `P-Exames.dc.html` | Exames: nutri lança, paciente vê |
| `A-Anamnese-Estruturada.dc.html` | Anamnese: álcool, sal e óleo |
| `G-Graficos.dc.html` | Padrão · catálogo de gráficos |
| `PDF-Avaliacao.dc.html` | PDF da avaliação (A4) |
| `A-Saude-Ficha.dc.html`, `A-Saude-Item.dc.html` | Nutri · Ficha, saúde e restrições (DES-16) |
| `A-Montador-Alerta.dc.html`, `A-Montador-Confirmar.dc.html` | Nutri · Montador com alerta de restrição e confirmação de alergia (DES-16) |
| `A-Consulta-Form.dc.html`, `A-Consulta-Status.dc.html`, `A-Agenda-Mes.dc.html` | Nutri · Consulta, status e agenda do mês (DES-17) |
| `A-Conta-Lembrete.dc.html` | Nutri · Preferência de lembrete (DES-17) |
| `P-Proxima-Consulta.dc.html` | Paciente · Hoje com a próxima consulta (DES-17) |
| `P-Hoje-Metas.dc.html`, `P-Metas-Semana.dc.html` | Paciente · Metas de hábito no Hoje e na semana (DES-19) |
| `A-Metas-Paciente.dc.html`, `A-Meta-Nova.dc.html`, `A-Modelos-Meta.dc.html` | Nutri · Metas, nova meta e modelos (DES-19) |

Fotos (PNG) dos artboards novos em [../evidencias/DES-08-10/](../evidencias/DES-08-10/). Os dados dos artboards novos são de exemplo (paciente Guilherme do PDF da nutri; criança fictícia).

Rodada DES-11 (01/11/2026): `P-Dieta-Macros`, `P-Refeicao-Macros`, `P-Lista-Substituicao`, `P-Lista-Substituicao-Vazio`, `T-Lista-Substituicao` (MOB-55) e `PDF-Lista-Pequena`, `PDF-Lista-Media`, `PDF-Lista-Grande`, `PDF-Lista-Carboidratos` (BE-50b). Fotos em [../evidencias/DES-11/](../evidencias/DES-11/). Alimentos, medidas e gramas são ilustrativos; no PDF o desenho varia só as gramas, mas o backend deve escalar também a medida caseira (arredondando para ¼).

**Índice por tarefa (qual artboard ler para cada MOB/BE): [INDICE-POR-TAREFA.md](INDICE-POR-TAREFA.md).**

Rodada DES-16 (17/11/2026, release 1.4.0): saúde e restrições na ficha e alerta no montador. Fotos em [../evidencias/DES-16/](../evidencias/DES-16/). Paciente fictícia.

Rodada DES-17 (17/11/2026, release 1.4.0): consulta com tipo, modalidade e link, ações de status, visão do mês, preferência de lembrete e próxima consulta do paciente. Fotos em [../evidencias/DES-17/](../evidencias/DES-17/).

Rodada DES-19 (17/11/2026, release 1.4.0): metas de hábito, com a água como uma delas. Fotos em [../evidencias/DES-19/](../evidencias/DES-19/).
