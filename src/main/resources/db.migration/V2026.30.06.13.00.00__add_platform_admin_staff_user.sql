-- Platform admin: pode fazer onboarding de novos merchants (POST /api/merchants).
-- Atributo GLOBAL do staff (não é papel por-merchant). Fecha SEC-020 (FIX-03).
ALTER TABLE core.staff_users
  ADD COLUMN IF NOT EXISTS platform_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Bootstrap (executar manualmente após o deploy, escolhendo a conta operadora):
--   UPDATE core.staff_users SET platform_admin = TRUE WHERE email = 'operador@plataforma.com';
