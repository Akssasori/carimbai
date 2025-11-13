-- V2025.11.XX__checks_roles_sources_status.sql
ALTER TABLE core.staff_users
  ADD CONSTRAINT ck_staff_role CHECK (role IN ('ADMIN','CASHIER'));

ALTER TABLE fidelity.stamps
  ADD CONSTRAINT ck_stamps_source CHECK (source IN ('A','B'));

ALTER TABLE fidelity.cards
  ADD CONSTRAINT ck_cards_status CHECK (status IN ('ACTIVE','BLOCKED','EXPIRED'));
