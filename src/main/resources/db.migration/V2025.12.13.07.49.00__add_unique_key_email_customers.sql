ALTER TABLE fidelity.customers
ADD CONSTRAINT uq_customers_email UNIQUE (email);