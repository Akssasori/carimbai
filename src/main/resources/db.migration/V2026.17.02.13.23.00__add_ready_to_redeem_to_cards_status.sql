ALTER TABLE fidelity.cards
DROP CONSTRAINT IF EXISTS ck_cards_status;

ALTER TABLE fidelity.cards
    ADD CONSTRAINT ck_cards_status
        CHECK (status IN ('ACTIVE','READY_TO_REDEEM','BLOCKED','EXPIRED'));
