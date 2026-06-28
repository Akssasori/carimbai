---
name: "source-command-deploy"
description: "Checklist e acompanhamento do deploy do carimbai (GitHub Actions → VPS)"
---

# source-command-deploy

Use this skill when the user asks to run the migrated source command `deploy`.

## Command Template

# Deploy — carimbai

O deploy é **automático**: todo `push` em `main` dispara o workflow
`.github/workflows/deploy.yml`, que builda o jar, copia para a VPS (scp) e
reinicia `carimbai.service` (`systemctl restart`).

Não há deploy manual local. Este comando é um checklist para fazer o release com
segurança.

## Antes do push

1. Build local passa: `./mvnw clean package -DskipTests`
2. (Opcional, recomendado) testes: `./mvnw test`
3. `git status` limpo e na branch certa; revisar `git diff`.
4. Rodar a skill `security-review` se a mudança mexe em auth/segredos/endpoints.
5. Conferir que segredos novos foram adicionados como **GitHub Secrets**
   (`SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, e quaisquer `CARIMBAI_*` que a
   VPS precise) — não no código.

## Release

- `git push origin main` (ou merge do PR em `main`).
- Acompanhar a Action em GitHub → aba Actions → "Deploy Carimbai".

## Pós-deploy

- Verificar que `carimbai.service` subiu (logs na VPS, se acessível).
- Smoke test: Swagger/health do ambiente de produção.

⚠️ Não faça operações destrutivas na VPS a partir daqui. Em caso de falha,
investigar os logs da Action antes de re-tentar.
