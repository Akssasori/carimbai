CREATE TABLE fidelity.push_subscriptions (
    id          BIGSERIAL    PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES fidelity.customers(id),
    endpoint    TEXT         NOT NULL,
    p256dh      VARCHAR(255) NOT NULL,
    auth        VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_sub_customer_endpoint UNIQUE (customer_id, endpoint)
);

CREATE INDEX idx_push_sub_customer ON fidelity.push_subscriptions(customer_id);
