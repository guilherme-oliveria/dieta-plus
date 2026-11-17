# Contrato da fase 9 e 10: release 1.4.0 (BE-C9 e BE-C10)

Cópia oficial no backend. Plano em [`docs/plano-de-execucao-1.4.0.md`](../../../docs/plano-de-execucao-1.4.0.md). Regras `RN-SAU`, `RN-MET`, `RN-SUP`, `RN-ORI`, `RN-DOC` e as atualizadas estão em [regras-de-negocio.md](regras-de-negocio.md); mapa de endpoints, coleções de sync e códigos em [integracao.md](integracao.md). Erros seguem o `ProblemDetail` de `integracao.md`.

Tudo é **aditivo** (campos novos opcionais, `/api/v1`). O app 1.3.x continua funcionando contra o backend 1.4.0. Exemplos tipados em `src/test/resources/contrato/fase-9/` e `fase-10/`, conferidos por `ContratoFase9Test`. Vetores em `contrato/calculos/alertas-restricao.json` e `metas.json`, conferidos no mobile pelos mesmos arquivos.

## Status por tarefa

| Parte | Contrato | Servidor |
| --- | --- | --- |
| Tipos, exemplos, vetores, regras e códigos | BE-C9 e BE-C10 (pronto) | — |
| Restrições de saúde na ficha | BE-C9 | BE-68 |
| Marcadores novos | BE-C9 | BE-69 |
| Alerta e confirmação na dieta | BE-C9 | BE-70 |
| Consulta: status, modalidade, link, projeção do paciente | BE-C9 | BE-71 |
| Lembretes e preferências da agenda | BE-C9 | BE-72 |
| Humor e fora do plano | BE-C9 | BE-73 |
| Metas e água como meta | BE-C9 | BE-74, BE-86 |
| Suplementos e orientações | BE-C9 | BE-75, BE-76, BE-77 |
| Documentos | BE-C9 | BE-78, BE-79 |
| Planos, anual e troca | BE-C10 | BE-80, BE-81, BE-82 |
| `modelosDieta` na escrita do sync | — | BE-83 |

Os controllers não são publicados com `501` nesta tarefa: seguindo a BE-C7 e a BE-C8, o contrato é publicado por tipos (`record`), exemplos e documento, e cada tarefa de servidor cria o controller. Os tipos ficam em `api/` do módulo dono; os módulos novos `metas`, `prescricoes` e `documentos` nascem com `api/` e `package-info`.

## Decisões registradas (C-02, C-03, C-08, C-09 e C-10, aprovadas como recomendadas, D-12)

- **C-02** `ONLINE` no tipo da consulta continua aceito na entrada, é gravado como RETORNO + ONLINE e some das respostas novas. O app 1.3.x mostra essas consultas como "Retorno".
- **C-03** Alergias em texto livre da anamnese e itens estruturados da ficha coexistem; sem migração automática.
- **C-08** A prescrição de suplemento guarda cópia de nome e dose; o paciente não recebe o catálogo de produtos.
- **C-09** Entidades novas da nutri (metas, suplementos, orientações, documentos): escrita online, leitura offline. O paciente registra offline (só inclusão).
- **C-10** A RN-NOT-01 ganha o push "nova orientação" e o e-mail de confirmação de consulta, sempre com texto genérico.
- **Restrições na ficha:** coluna JSON `restricoes_saude` em `ficha_paciente` (mesmo padrão do `cadastro_ampliado`), lista inteira substituída a cada escrita.
- **Módulo de suplementos e orientações:** módulo novo `prescricoes` (e não dentro de `dietas`), para o `ArquiteturaTest` não criar dependência circular com `relatorios` (o PDF da dieta consulta `prescricoes.api`).

## H-01 Restrições de saúde e alerta

`restricoesSaude[]` (tipo `RestricaoSaude`) opcional em `POST/PATCH /pacientes` e na coleção `pacientes` do sync, só para a nutri dona; nunca na projeção do paciente. Marcadores em condição ou medicamento: `422 ITEM_INVALIDO`.

No item da dieta (requisição): `alergiaConfirmada` (boolean, opcional). Na resposta: `alertas[]` (`AlertaRestricao`), calculado pelo servidor ao salvar. Publicar com item de alergia sem confirmação: `422 ALERGIA_NAO_CONFIRMADA` (RN-DIE-14). Regra de cálculo: `alertas-restricao.json`.

Marcadores novos (BE-69), além de `CONTEM_LACTOSE`, `CONTEM_GLUTEN`, `ALTO_SODIO`, `ACUCAR_ADICIONADO` e `ALTO_POTASSIO`: `AMENDOIM`, `FRUTOS_DO_MAR`, `OVO` e `SOJA`.

## H-02 Agenda

`POST/PATCH /consultas` e a coleção `consultas` do sync ganham, todos opcionais: `modalidade` (`ModalidadeConsulta`), `linkOnline` (`https`, até 300), `orientacaoPaciente` (até 2.000) e `observacaoInterna` (até 2.000); `tipo` ganha `AVALIACAO` e `status` ganha `REALIZADA` e `FALTA`. O `PATCH` aceita `status` com as transições da RN-AGE-03 (`422 TRANSICAO_STATUS_INVALIDA`); `linkOnline` fora do padrão: `422 LINK_INVALIDO`. A resposta é a mesma `ConsultaResposta` de sempre com os campos novos opcionais (`modalidade`, `linkOnline`, `orientacaoPaciente`, `observacaoInterna`). A nutri recebe tudo; o paciente recebe a projeção em `GET /eu/consultas` e na coleção `consultas` do sync dele: sem `observacaoInterna`, `linkOnline` só em consulta ONLINE ainda AGENDADA e status `ENCERRADA` no lugar de REALIZADA e FALTA (o `ENCERRADA` só existe nessa projeção e nunca se grava nem se envia). **Mudança em relação ao BE-C9:** o `ConsultaPacienteProjecaoResposta` e o `StatusConsultaPaciente` publicados no BE-C9 saíram; a projeção do paciente é a própria `ConsultaResposta`, com `ENCERRADA` no mesmo enum `StatusConsulta` (o MOB-81, PR #37, já modela assim). Pelo financeiro, `comparecimento` continua aceito para o app 1.3.x e vira status (RN-FIN-01, C-01); voltar REALIZADA ou FALTA para pendente dá `422 TRANSICAO_STATUS_INVALIDA`. O app 1.3.x ignora campos e trata enum desconhecido.

`GET/PUT /eu/nutri/preferencias-agenda` (`PreferenciasAgendaRequisicao`/`Resposta`, sem `ETag`, o último vale).

## H-03 Diário

`POST /eu/checkins` e a coleção `checkins` ganham `fomeAntes` e `sensacaoDepois` (1 a 5). `POST /eu/registros-fora-do-plano` e a coleção `registrosForaDoPlano` (só `CRIAR`). `GET /pacientes/{id}/adesao` ganha `humor` (`HumorAdesao`) e `foraDoPlano` (lista de `RegistroForaDoPlanoResposta` da semana).

## H-04 Metas de hábito

Endpoints e coleções em `integracao.md`. `PATCH /metas/{id}` e `PUT /eu/nutri/modelos-meta/{id}` usam `If-Match`. `POST /pacientes/{id}/metas/de-modelo/{modeloId}` devolve `MetasResposta` (`201`). A meta `AGUA` existe para todo paciente; tentar criar uma segunda ou encerrá-la: `422 META_INVALIDA`. O alvo padrão e a precedência estão na RN-MET-05. Cálculo de cumprimento: RN-MET-03 e `metas.json`.

## H-05 Suplementos e orientações

Endpoints em `integracao.md`. `produtoId` e `nome` juntos ou nenhum dos dois: `422 ITEM_INVALIDO`. O push "nova orientação" não leva o título nem o texto.

## H-06 Documentos

Endpoints em `integracao.md`. `GET /documentos/{id}/pdf` e `GET /eu/documentos/{id}/pdf` devolvem `PdfResposta` (link assinado, `Cache-Control: no-store`). Modelo do sistema editado ou apagado: `403 ACESSO_NEGADO`. Anular de novo: `422 DOCUMENTO_JA_ANULADO`. A rota pública de conferência não exige autenticação, tem limite de requisições (BE-31) e devolve `ConferenciaDocumentoResposta`; código inexistente: `404 RECURSO_NAO_ENCONTRADO`.

## H-07 Planos (BE-C10)

- `GET /api/v1/planos` (pública): `PlanosPublicosResposta`, só planos à venda; sem nenhum valor fixo no código (os valores entram pelo admin, C-05).
- `POST /assinatura/checkout`: `CheckoutPlanoRequisicao` (`planoCodigo`, `periodicidade` opcional, padrão `MENSAL`). O corpo antigo `{ "plano": "..." }` continua aceito por pelo menos 6 meses. Plano fora de venda ou sem preço anual: `422 PLANO_INDISPONIVEL`.
- `GET /assinatura`: campos opcionais de `AssinaturaAmpliadaResposta` somados aos de `AssinaturaDetalheResposta`.
- **Troca de plano com a assinatura ativa (BE-81b, RN-ASS-10):** o mesmo `POST /assinatura/checkout`. Subir (preço mensal maior) devolve a `url` da cobrança cheia do plano novo, que passa a valer quando o pagamento é confirmado. Descer, ou só mudar a periodicidade, não cobra agora: a resposta vem sem `url` e com `trocaAgendadaPara` (data), e `GET /assinatura` mostra `trocaAgendada` (`planoCodigo`, `periodicidade`, `aPartirDe`) até o vencimento. Pedir o plano e a periodicidade atuais desfaz a troca agendada; sem troca agendada, continua `409 ASSINATURA_JA_ATIVA`.
- Planos decididos na G-21: Teste (30 dias, 5 pacientes), Básico R$ 29,90 (20), Consultório R$ 79,90 (80), Plus R$ 179,90 (sem limite), anual = 10x o mensal. Pagamento por PIX, cartão e boleto.

## Compatibilidade com o app 1.3.x

Nenhum campo existente muda de nome ou tipo. Enum novo (`REALIZADA`, `FALTA`, `AVALIACAO`) só aparece nas respostas da nutri e nos dados que o app 1.3.x já tolera como desconhecidos. `POST /eu/agua` e a coleção `agua` seguem aceitos por pelo menos 6 meses.
