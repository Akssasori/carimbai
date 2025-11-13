CREATE INDEX IF NOT EXISTS idx_stamps_card_loc_when
  ON fidelity.stamps(card_id, location_id, when_at);