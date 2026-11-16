# Dieta+

App para nutricionistas e pacientes (multi-nutri, com assinatura). Monorepo com dois projetos independentes:

| Pasta | O que é | Como começar |
| --- | --- | --- |
| [`backend/`](backend/README.md) | API em Java 25 + Spring Boot, Postgres 18, Liquibase | `cd backend && ./gradlew bootRun` |
| [`mobile/`](mobile/README.md) | App Kotlin Multiplatform (Android, iOS, Desktop e web) | `cd mobile && ./gradlew :shared:jvmTest` |

- Instruções para agentes de IA e para quem contribui: [`AGENTS.md`](AGENTS.md) (cada pasta tem o seu, que vence o da raiz).
- Gitflow: `develop` → `release/X.Y.Z` → `main`. Tags `vX.Y.Z`, `backend-vX.Y.Z`, `mobile-vX.Y.Z`.
- CI separado por projeto: `.github/workflows/` roda só o que mudou.

## Estado da migração

Histórico dos repositórios `dieta-backend` e `dieta-mobile` preservado neste repositório. Os repositórios antigos serão arquivados depois.
