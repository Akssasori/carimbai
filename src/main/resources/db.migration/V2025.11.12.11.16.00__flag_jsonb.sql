-- Garante que o campo 'requirePinOnRedeem' exista em todas as locations
UPDATE core.locations
SET flags = jsonb_set(flags, '{requirePinOnRedeem}', 'true'::jsonb, true)
WHERE NOT (flags ? 'requirePinOnRedeem');

-- Flags “latentes” para o roadmap (A ligado, B desligado por padrão)
UPDATE core.locations
SET flags = jsonb_set(flags, '{enableScanA}', 'true'::jsonb, true)
WHERE NOT (flags ? 'enableScanA');

UPDATE core.locations
SET flags = jsonb_set(flags, '{enableScanB}', 'false'::jsonb, true)
WHERE NOT (flags ? 'enableScanB');
