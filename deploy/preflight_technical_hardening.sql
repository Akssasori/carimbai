-- Read-only checks. Run only in the deployment environment with authorized access.
-- Review the actual Flyway history before deploying V2026.31.*; do not repair/reset it blindly.
SELECT installed_rank, version, description, success
FROM public.flyway_schema_history ORDER BY installed_rank;

-- Counts only: no social identifiers or contacts in exported reports.
SELECT count(*) AS duplicate_social_identity_groups
FROM (
    SELECT provider_id FROM fidelity.customers
    WHERE provider_id IS NOT NULL AND btrim(provider_id) <> ''
    GROUP BY provider_id HAVING count(*) > 1
) duplicates;

SELECT count(*) AS case_insensitive_duplicate_email_groups
FROM (
    SELECT lower(btrim(email)) FROM fidelity.customers
    WHERE email IS NOT NULL AND btrim(email) <> ''
    GROUP BY lower(btrim(email)) HAVING count(*) > 1
) duplicates;

SELECT count(*) AS merchants_without_active_admin
FROM core.merchants merchant
WHERE merchant.active AND NOT EXISTS (
    SELECT 1 FROM core.staff_user_merchants link
    JOIN core.staff_users staff ON staff.id = link.staff_user_id
    WHERE link.merchant_id = merchant.id AND link.active AND staff.active AND link.role = 'ADMIN'
);
