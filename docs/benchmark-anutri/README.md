# Comparação com o A Nutri (release 1.4.0)

> Criado em 16/11/2026 a pedido do Guilherme, para o PO transformar em tarefas `BE-NN`, `MOB-NN` e `DES-NN` da release **1.4.0**.
> Documento comum aos dois projetos, por isso fica na raiz (`docs/`), e não em `backend/docs` nem em `mobile/docs`.

## O que tem aqui

| Arquivo | Para quê |
| --- | --- |
| [`analise-de-lacunas.md`](analise-de-lacunas.md) | Comparação área por área: o que o A Nutri tem e o Dieta+ não, o que o Dieta+ faz pior e o que já faz melhor. Inclui a parte comercial (planos, teste grátis, limites) e a recomendação sobre a landing page. |
| [`backlog-1.4.0.md`](backlog-1.4.0.md) | Histórias priorizadas (valor para a nutri × esforço), com critérios de aceite, regras afetadas e divisão sugerida em BE, MOB e DES. É o que o PO pega. |

## De onde veio

- **A Nutri** (`anutri.ia.br`), SaaS brasileiro para nutricionistas: captura funcional do app da nutri, do app do paciente e da landing comercial, feita entre 13 e 16/11/2026 com a conta de teste do Guilherme. A captura fica no repositório privado `guilherme-oliveria/especialista-scraping`, em `capturas/anutri.ia.br/` (`RESUMO.md`, `fluxos/01` a `09` e `landing/comercial/planos-e-precos.md`). Ela tem dados pessoais e **não deve ser copiada para este repositório**.
- **Dieta+**: `develop` em 16/11/2026 (backend até BE-67 e BE-55c, mobile até MOB-79 e MOB-UX-10), `visao-geral.md`, `regras-de-negocio.md`, `funcionalidades-mvp.md` e os planos de ação.

## Regras para usar este material

1. **A captura é referência de funcionalidade e de fluxo.** Não copie textos, nomes de planos, marca, cores, ícones nem layout do A Nutri. Telas novas seguem o canvas e `mobile/docs/design/` (regra do design no `mobile/AGENTS.md`).
2. **As decisões que já existem continuam valendo** (`visao-geral.md`): sem mensageria, sem microsserviço, offline-first no app, o backend decide. Onde uma lacuna esbarra numa decisão (chat, foto do prato, clínica com secretária), o backlog marca "decisão do Guilherme" em vez de propor a mudança.
3. **Preços e limites são decisão do Guilherme.** O documento mostra os números do concorrente e sugere uma direção, mas os valores vão para a tabela `plano` só depois que ele escolher.
4. Os IDs `L-xx` (lacunas) e `H-xx` (histórias) são deste documento. O PO dá os números `BE-NN`/`MOB-NN`/`DES-NN` definitivos ao passar para os `plano-de-acao.md`, para não colidir com tarefas abertas ao mesmo tempo.
