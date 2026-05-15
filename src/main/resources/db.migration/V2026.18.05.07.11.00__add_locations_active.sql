-- Adiciona soft-disable em core.locations para o painel de gestao.
-- Postgres >= 11 trata ADD COLUMN com DEFAULT constante como rewriteless.
ALTER TABLE core.locations
  ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
