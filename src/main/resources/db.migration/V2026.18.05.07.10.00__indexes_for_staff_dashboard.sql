-- Acelera "stamps cujo when_at >= hoje" e "stamps recentes do merchant".
-- O indice existente (card_id, when_at) so ajuda quando ja ha filtro por card_id.
CREATE INDEX IF NOT EXISTS idx_stamps_when_at_desc
  ON fidelity.stamps (when_at DESC);

-- Mesma logica para rewards na query "premios hoje" e "rewards recentes".
CREATE INDEX IF NOT EXISTS idx_rewards_issued_at_desc
  ON fidelity.rewards (issued_at DESC);
