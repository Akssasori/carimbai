-- Version follows V2026.30.* already present; existing migrations are immutable.
ALTER TABLE core.staff_users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ops.refresh_tokens
    ADD COLUMN family_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_refresh_tokens_family ON ops.refresh_tokens (family_id);

CREATE TABLE ops.revoked_tokens (
    jti VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_revoked_tokens_expiry ON ops.revoked_tokens (expires_at);
