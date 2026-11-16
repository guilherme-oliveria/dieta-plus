# AGENTS.md — Dieta+ (monorepo)

Instruções para agentes de código (Codex, Claude Code e outros). O `CLAUDE.md` importa este arquivo; edite só aqui.

## Como as instruções se organizam

| Arquivo | Vale para |
| --- | --- |
| `AGENTS.md` (este) | O repositório todo: Gitflow, tags, CI, QA, idioma, Orquestrador IA. |
| [`backend/AGENTS.md`](backend/AGENTS.md) | Só a pasta `backend/` (API Java/Spring). |
| [`mobile/AGENTS.md`](mobile/AGENTS.md) | Só a pasta `mobile/` (app KMP: Android, iOS, Desktop e web). |

**Regra de precedência:** ao mexer em arquivos de uma pasta, valem as instruções dela **e** as da raiz; se divergirem, **a da subpasta vence**. Nunca copie regra da raiz para a subpasta (e vice-versa): quem muda o Gitflow ou o fluxo de PR muda aqui.

## O projeto

Dieta+: app para nutricionistas e pacientes (multi-nutri, com assinatura). Um repositório, dois projetos bem separados:

```
backend/   API (Java 25, Spring Boot, Postgres, Liquibase). Única fonte de verdade e de regras de negócio.
mobile/    App (Kotlin Multiplatform + Compose): Android, iOS, Desktop e web (wasmJs). Offline-first.
docs/      (a criar, tarefa MONO-02) documentos comuns aos dois projetos.
.github/   workflows, templates e dependabot do repositório todo.
ops/       (opcional) scripts que envolvem os dois projetos.
```

Não há Gradle na raiz: cada projeto tem o seu `gradlew`. Rode comandos **dentro da pasta do projeto** (`cd backend && ./gradlew build`, `cd mobile && ./gradlew :shared:jvmTest`). O pacote do backend é `com.br.dietamais`; o do app é `org.br.dietamais` (não renomeie).

Idioma: **português** em docs, commits, PRs, mensagens de erro, textos da UI e nomes de domínio. Termos técnicos (controller, ViewModel, repository) ficam em inglês.

O `dieta-frontend` (Angular) foi arquivado: a web sai de `mobile/` como alvo `wasmJs`.

## Qual projeto eu mexo?

- Tarefa `BE-NN` → `backend/`, plano em `backend/docs/arquitetura/plano-de-acao.md`.
- Tarefa `MOB-NN` → `mobile/`, plano em `mobile/docs/arquitetura/plano-de-acao.md`.
- **Um PR mexe em um projeto só.** Exceções: mudança de raiz (`.github/`, este arquivo, `docs/`) e contrato que não dá para separar (explique no PR).
- **PR que cruza projetos (contrato):** o backend vai primeiro (`BE-NN`), o app depois (`MOB-NN`) citando o PR do backend. Quando o endpoint do backend ainda não existe, o app usa `MockEngine` com o JSON do contrato e diz isso no PR.
- Os documentos `visao-geral`, `regras-de-negocio`, `integracao` e `funcionalidades-mvp` existem hoje em `backend/docs/arquitetura/` (oficial) e `mobile/docs/arquitetura/` (cópia idêntica). Mude os dois no mesmo PR até a tarefa MONO-02 unificar.

## Gitflow

`main` (produção) e `develop` (integração). Nada entra direto nelas: tudo por PR.

| Branch | Sai de | Volta para |
| --- | --- | --- |
| `feature/BE-NN-descricao`, `feature/MOB-NN-descricao` | `develop` | `develop` |
| `bugfix/<desc>` | `develop` (ou `release/X.Y.Z`) | a branch de onde saiu |
| `release/X.Y.Z` | `develop` | `main` |
| `hotfix/X.Y.Z` | `main` | `main` |
| `backmerge/vX.Y.Z` | `main` | `develop` (criada pela pipeline) |

Se o ambiente já definiu uma branch para você (`claude/…`, `codex/…`), use a dele. **Merge na `main` é só do Guilherme.** Detalhes: skill `gitflow`.

A release é do repositório inteiro (`release/X.Y.Z` leva o que estiver na `develop`, dos dois projetos).

### Tags

| Tag | Quando | Quem cria |
| --- | --- | --- |
| `vX.Y.Z` | merge de `release/X.Y.Z` ou `hotfix/X.Y.Z` em `main` | `main.yml` |
| `backend-vX.Y.Z` | idem, **só se `backend/` mudou** desde a última tag `backend-v*` | `main.yml` |
| `mobile-vX.Y.Z` | idem, **só se `mobile/` mudou** desde a última tag `mobile-v*` | `main.yml` |
| `vX.(Y+1).0-dev.N` | cada merge em `develop` | `develop.yml` |

Deploys e publicações de cada projeto (futuros) disparam nas tags `backend-v*` e `mobile-v*`, independentes uma da outra.

## CI/CD por projeto

`.github/workflows/`:

- `ci.yml`: PR e push em `main`/`release/**`. Confere o Gitflow, descobre por caminho o que mudou (`_mudancas.yml`) e roda só `_backend.yml` e/ou `_mobile.yml`. Mudança em `.github/workflows/` roda os dois; push em `main`/`release/**` roda os dois.
- **iOS só roda em release** (PR de `release/*`, push em `main` ou `release/**`), nunca em PR comum.
- `develop.yml` (testes + tag `-dev.N`), `main.yml` (testes completos + tags + GitHub Release + back-merge).
- `e2e-web.yml`: Playwright contra o backend desta mesma árvore; roda em PR que mexe em `mobile/e2e`, `mobile/shared`, `mobile/webApp` ou no próprio fluxo.
- `seguranca.yml`: CodeQL, revisão de dependências e Gitleaks no repositório todo.
- Checks obrigatórios nas proteções de `main` e `develop`: **`gitflow`** e **`ci-ok`** (o `ci-ok` sempre roda, mesmo quando um projeto é pulado por não ter mudado).

## Regras comuns

- Commits pequenos, mensagem em português no imperativo (`Adiciona cadastro da nutri`), prefixo do projeto no título do PR (`BE-NN …`, `MOB-NN …`).
- **Segredos** só em variáveis de ambiente ou secrets do GitHub. Nada de chave, senha, token, `google-services.json`, `GoogleService-Info.plist`, keystore ou `local.properties` no Git. Não logue senha, token nem dado de saúde.
- Não adicione mensageria (RabbitMQ/Kafka), microsserviço ou Keycloak sem pedido explícito: está decidido que não entram no MVP.
- PR com o template `.github/pull_request_template.md`. Marque a tarefa como `[x]` no `plano-de-acao.md` do projeto no mesmo PR. Uma tarefa por PR.
- Sem Docker no ambiente (nuvem), os testes do backend com Testcontainers não sobem: escreva-os mesmo assim, diga no PR e confie no CI.

## Revisão por QA (vale para Codex, Claude e qualquer agente)

Quem escreve o código nunca aprova o próprio trabalho. O fluxo de todo PR de tarefa é:

1. **Agente de código** (Codex ou outro) abre o PR para a `develop` com o ID da tarefa no título (`BE-NN …`, `MOB-NN …`). Ele **nunca** aprova, faz merge, fecha PR alheio ou resolve comentário do QA.
2. **Agente QA** (Claude; um por projeto, ou um só para os dois, a critério do Guilherme) revisa o PR contra a tarefa do `plano-de-acao.md`, a definição de pronto do projeto, as regras `RN-xxx` citadas, o contrato (`integracao.md` e os JSONs de `contrato/`) e, no mobile, `mobile/docs/design/`. Confere também o CI verde. E publica o veredito:
   - **Aprovado:** review "Approve" ou, se o GitHub não deixar (mesma conta), comentário que começa com `QA: APROVADO`. Depois o QA faz o merge na `develop`. Merge na `main` é só do Guilherme.
   - **Corrigir:** review "Request changes" ou comentário que começa com `QA: CORRIGIR`, com a lista do que falta.
3. **Agente de código**, ao receber `QA: CORRIGIR`: corrige na **mesma branch**, faz push, responde cada apontamento dizendo o que mudou e comenta `QA: PRONTO PARA REVISAR`. Discordou de um apontamento? Responde explicando, sem ignorar.
4. **Próxima tarefa só depois do veredito.** Antes de começar qualquer tarefa nova, o agente de código confere os PRs que abriu: com `QA: CORRIGIR` pendente, corrige primeiro; sem veredito ainda, para e avisa "aguardando QA" (pode adiantar só tarefas que não dependem daquele PR, saindo da branch dele).

## Orquestrador IA (contexto compartilhado)

O painel local do Orquestrador IA acompanha os PRs abertos para a `develop` (etapa, CI e vereditos do QA) no projeto **`dieta-mais`**. O fluxo de PR acima não muda.

Se a sessão tiver o MCP `orquestradoria` (sessões locais na máquina do Guilherme), use-o com `project: "dieta-mais"` e `repo: "guilherme-oliveria/dieta-plus"` (nome do monorepo; ajuste se for outro):

- Antes de começar uma tarefa: `listar_contexto` do repositório, para ver decisões e contratos registrados.
- Ao tomar uma decisão que o outro projeto precisa respeitar: `registrar_decisao`.
- Ao criar ou mudar um contrato (endpoint, DTO, código de erro, evento): `registrar_contrato`. Para atualizar um registro existente, passe o ID dele em `supersedes`.
- Risco ou pendência que afeta o outro projeto: `registrar_nota`.
- `instrucoes_do_papel` devolve as instruções completas do seu papel (PO, Dev ou QA).

Sem o MCP (sessão na nuvem), siga normalmente: os documentos de `docs/arquitetura/` de cada projeto continuam sendo a referência e nada aqui bloqueia o trabalho.

## Suposições

Pontos ainda não confirmados com o Guilherme estão marcados `[SUPOSIÇÃO Sx]` em `visao-geral.md`. Implemente como está escrito, deixando o valor configurável, e não bloqueie a tarefa por causa deles.
