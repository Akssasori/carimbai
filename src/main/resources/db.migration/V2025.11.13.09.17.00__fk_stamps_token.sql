ALTER TABLE fidelity.stamps
  ADD CONSTRAINT fk_stamps_token
  FOREIGN KEY (token_id) REFERENCES ops.stamp_tokens(id);