-- Enriquecer fidelity.programs para suportar promocoes/campanhas
ALTER TABLE fidelity.programs ADD COLUMN description TEXT;
ALTER TABLE fidelity.programs ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE fidelity.programs ADD COLUMN start_at TIMESTAMPTZ;
ALTER TABLE fidelity.programs ADD COLUMN end_at TIMESTAMPTZ;
ALTER TABLE fidelity.programs ADD COLUMN category VARCHAR(50);
ALTER TABLE fidelity.programs ADD COLUMN terms TEXT;
ALTER TABLE fidelity.programs ADD COLUMN image_url VARCHAR(500);
ALTER TABLE fidelity.programs ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
