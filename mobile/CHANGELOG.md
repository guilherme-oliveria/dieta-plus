# Changelog — dieta-mobile

Formato [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), versões [SemVer](https://semver.org/lang/pt-BR/). Cada versão é um marco de `docs/arquitetura/plano-de-execucao.md` (seção 3). Comum ao `dieta-backend` (mesmos números de versão).

> **M8 1.2.0 integrado à main em 03/11/2026.** A develop preserva as entregas posteriores de M9/M10 e recebe por PR o retorno das versões da release. Nenhuma tag manual; publicação nas lojas e implantação continuam pendentes.

## [Não lançado] — pendente

- MOB-58: carrega a base IANA de fusos no Wasm para abrir e calcular avaliações na web sem `IllegalTimeZoneException` em `America/Sao_Paulo`. Preserva o dia local, a virada do aniversário e o horário de verão histórico; inclui regressão no Chromium para a idade infantil e a prévia adulta.
- **Web:** cadastro de nutri bloqueado (termos de uso não configurados no `main.kt`); árvore de acessibilidade desatualizada depois que folha ou diálogo fecha; autofill de senha não funciona (campos do Compose não são `<input>`). Detalhes e medições em `docs/arquitetura/web.md`.
- MOB-18b Ações Comi/Troquei/Pulei na notificação, MOB-27b push no iOS, MOB-31b pacotes do desktop no CI, MOB-33b envio da logo, MOB-35a beta interno (assinatura de release, Play Console, TestFlight).
- MOB-46 Publicação da web na VPS (depende do BE-32b).
- Conferir com leitor de tela real (NVDA, VoiceOver, TalkBack), Safari e Firefox (G-14).

## [1.4.0] — M11: saúde, consultas, hábitos, prescrições, documentos e planos (fechada na develop em 18/11/2026)

Histórias H-01 a H-07 do `docs/plano-de-execucao-1.4.0.md` (raiz do monorepo), com as decisões D-03 a D-12 (a D-07 mudou: a água virou meta).

- **Contrato e offline:** MOB-81 DTOs novos e opcionais da 1.4.0, JSONs do contrato com teste e enums desconhecidos tratados. MOB-29b rascunho de dieta, MOB-29c lista de substituição e modelo de questionário, MOB-29d modelo de dieta: todos gravam no aparelho e na fila na mesma transação.
- **H-01 Saúde e restrições:** MOB-82 aba "Saúde" na ficha (alergia, intolerância, condição, medicamento, com gravidade, observação e marcadores) e selo no cabeçalho. MOB-83 alerta de intolerância e confirmação de alergia no montador, com os mesmos vetores do backend e a recusa `ALERGIA_NAO_CONFIRMADA` ao publicar.
- **H-02 Consultas:** MOB-84 formulário com tipo, modalidade, link `https`, orientação ao paciente e observação interna; "Realizada" e "Faltou" no menu, ligados ao financeiro. MOB-85 visão do mês. MOB-86 lembrete das consultas na Conta da nutri. MOB-87 próxima consulta do paciente com "Entrar na consulta".
- **H-03 Humor e fora do plano:** MOB-88 fome antes e sensação depois no registro da refeição e "Comi algo fora do plano", offline. MOB-89 médias da semana e registros na adesão do paciente.
- **H-04 Metas de hábito:** MOB-90 metas no Hoje com sequência e progresso da semana. MOB-99 a água passa a ser uma meta, mantendo "Desfazer último" e "Outro valor". MOB-90b lembrete local por meta. MOB-91 cadastro, modelos e cumprimento na ficha.
- **H-05 Prescrições:** MOB-92 suplementos, orientações com negrito e lista, modelos de orientação. MOB-93 "Suplementos" e "Orientações" do paciente, com lembrete local do suplemento.
- **H-06 Documentos:** MOB-94 modelos, emitir atestado e declaração, anular com motivo e compartilhar o PDF. MOB-95 "Documentos" no Perfil do paciente, lista offline.
- **H-07 Planos:** MOB-96 comparação mensal/anual lida da API, uso dos limites, contagem do teste e aviso no painel; sem preço nem botão quando a chave `exibePrecoEBotao` não permite (D-09).
- **Testes:** MOB-97 ponta a ponta da web das histórias (`e2e/historias-1-4-0.spec.ts`) com a trava de acessibilidade em cada tela nova, e as 10 abas da ficha no `acessibilidade.spec.ts`.
- **Fechamento (MOB-98):** versão 1.4.0 no Android (`versionCode` 10400), iOS, desktop e web (`X-App-Versao`); `telas.md` e `divergencias.md` atualizados; cópias dos docs comuns iguais às do backend.
- Pendente: o planejador por porções ainda não mostra os alertas de alergia (itens por grupo); a web do paciente não tem metas nem prescrições (só online, ver `divergencias.md`).

## [1.3.0] — M9 e M10: necessidades, protocolos, crianças, exames e anamnese (16/11/2026)

- MOB-59 Crescimento para nutri e paciente: seleção de avaliação/indicador, medidas e referências OMS congeladas, intervalos confirmados, fonte/versão e alternativa em tabela. Histórico offline sem classificação adulta para menores, sem misturar pacientes, vínculos, sexos ou versões. Referências ligadas somente em meses consecutivos disponíveis; lacunas permanecem explícitas.

- MOB-58 Avaliação infantil e adolescente: campos por idade, comprimento/estatura em cm com precisão de milímetros, peso ao nascer em kg convertido para gramas e escolhas opcionais de referência, Slaughter e atividade. Resultados confirmados pelo servidor (OMS/Frisancho, composição e energia), com intervalos sem interpolação e G-16 explícita. Escrita offline preserva entradas e remove derivados antigos; refresh respeita mutações pendentes. Histórico e paciente não usam classificação de IMC adulto para menores. A curva histórica de MOB-59 continua pendente.
- MOB-61 Anamnese estruturada: campos agrupados, medidas de bebida em seletor, resultados de pressão arterial, álcool, sal, óleo e duração recebidos do servidor. Digitação válida salva parcial, resultados antigos desaparecem após edição e podem ser atualizados sem concluir. Editor preserva códigos; web incorpora o PUT sem sobrescrever novas entradas. Falhas de gravação mantêm o formulário e permitem nova tentativa. Layout amplo com resultados laterais e fonte 200% em uma coluna.


- MOB-52 Passo "Necessidades" da avaliação (método do GET, nível de atividade, objetivo, gestante/lactante, protocolos das metas, dislipidêmico) com GEB, GET, VET, rosca e metas ao vivo; avaliações salvas reabrem com as escolhas. Textos antigos com `%%` corrigidos.
- MOB-53 "Meus protocolos" na conta da nutri (dobras e massa, salvo online com `If-Match`); a avaliação nova já vem com o padrão dela.

## [1.2.0] — M8: avaliação de adulto completa

- MOB-47 Biblioteca de gráficos (rosca, linhas com faixa, medidor, régua de faixas, "ver como tabela"). MOB-48 `dominio/calculos` com os mesmos vetores do backend (prévia ao vivo, offline).
- MOB-49 Paciente vê a avaliação inteira (perímetros, dobras, composição, parecer). MOB-50 Avaliação em passos com resultados ao vivo (nutri).
- MOB-51 "Meus resultados" do paciente: semáforos com texto, rosca de composição, meta de peso e evolução com faixa saudável.
- Pendente: crianças e adolescentes mostram só um aviso (onda 22); o app ainda não lê o protocolo padrão da nutri (MOB-53); textos antigos com `%%` aparecem duplicados na tela.

## [1.1.0] — M7: web no navegador (nutri e paciente)

- MOB-40 Alvo `wasmJs` e build da web no CI. MOB-41 `ApiDieta` sem banco local (`ArmazemSessao`). MOB-42 Sessão no navegador: access em memória, refresh por cookie, `X-Plataforma: WEB`.
- MOB-43 Telas da nutri pela API (pacientes, montador de dieta, avaliações, lista de substituição, alimentos, agenda, conta, painel, avisos, anamnese, evolução). Medido na A5: 5,5 MB gzip no primeiro carregamento, 7 s em 4G rápido, 31 s em 4G lento.
- MOB-44 Telas do paciente (Hoje, Dieta, Evolução, Compras, Perfil, anamnese, avisos). MOB-45 URL e botão voltar do navegador, link direto do convite, atalhos Alt+1 a Alt+5; acessibilidade medida com axe-core (A6): controles com nome, títulos, foco visível nos botões, contraste.
- Sessão: recarregar a página (F5) mantém a sessão pelo refresh do cookie, com tela de carregando; sem rede avisa e não desloga (`e2e/sessao-f5.spec.ts`); queda de rede do navegador agora vira falha de comunicação.
- Correções: aceite de convite pela web (`fuso` `SYSTEM` recusado pelo backend), checkbox das compras e switch do perfil sem nome acessível, título da aba Evolução, dia selecionado parecendo desabilitado.
- Teste de ponta a ponta com Playwright (`e2e/`, `ops/e2e-wsl.sh`).

## [1.0.0] — M6: assinatura, PDF, privacidade

- MOB-32 Assinatura. MOB-33 PDF da dieta e da avaliação (link assinado aberto no navegador do sistema). MOB-34 Privacidade (exportar e excluir dados). MOB-35 Aviso de atualização do app (`426 VERSAO_APP_OBSOLETA`).

## [0.5.0] — M5: nutri offline com conflitos

- MOB-29 Escrita offline da nutri (consultas, avaliações, cadastro de paciente, alimentos). MOB-30 Tela de conflito e lista de pendências. MOB-31 Desktop para consultório (sync a cada 5 min com a janela na bandeja, menu, atalhos).

## [0.4.0] — M4: avaliação, anamnese, agenda e push

- MOB-22 Avaliação e histórico. MOB-23 Evolução com registro de peso. MOB-24 Anamnese da nutri. MOB-25 Anamnese do paciente. MOB-26 Agenda de consultas. MOB-27 Push e central de avisos (Android e comum). MOB-28 Painel da nutri.

## [0.3.0] — M3: rotina do paciente

- MOB-16 Envio da fila. MOB-17 Check-in (Comi/Troquei/Pulei) e água. MOB-18 Lembretes locais. MOB-19 Perfil e preferências. MOB-20 Lista de compras. MOB-21 Agenda do celular (`.ics`).

## [0.2.0] — M2: dieta montada, publicada e vista pela paciente

- MOB-10 Alimentos da nutri. MOB-11 Lista de substituição do paciente. MOB-12 Montador de dieta. MOB-13 Publicar e duplicar. MOB-14 Hoje e Dieta. MOB-15 Trocar alimento. MOB-36 Perfil da nutri.

## [0.1.0] — M1: conta, paciente e convite

- MOB-01 Dependências e estrutura. MOB-02 Tipografia e componentes. MOB-03 Navegação e shell adaptativo. MOB-04 Banco local e fila. MOB-05 Cliente da API e sessão. MOB-06 Entrar e cadastro da nutri. MOB-07 Pacientes da nutri. MOB-08 Aceitar convite. MOB-09 Sync de leitura.

## Checklist de loja (preenchido até onde dá sem contas)

| Item | Estado |
| --- | --- |
| Nome "Dieta+", cores e fonte do design | Pronto |
| `426 VERSAO_APP_OBSOLETA` → tela "atualize o app" | Pronto |
| Ícones, splash e capturas de tela | Falta (depende do design final e do Guilherme) |
| Assinatura de release (keystore, secrets no CI) | Falta (G-06; MOB-35a) |
| Google Play Console, teste interno | Falta (conta do Guilherme) |
| TestFlight / App Store Connect | Falta (Apple Developer e Mac, G-06) |
| Política de privacidade publicada e texto de consentimento | Falta (Guilherme; sugerida revisão jurídica) |
| Regras das lojas para assinatura contratada fora do app (S4) | Falta conferir |
| Push no iOS (APNs, Firebase) | Falta (MOB-27b) |
| Acessibilidade com leitor de tela real | Falta (G-14); medição por axe-core feita para a web |
