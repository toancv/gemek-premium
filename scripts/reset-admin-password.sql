-- reset-admin-password.sql
-- Resets the seeded admin's password_hash to the current .env ADMIN_PASSWORD.
--
-- WHY THIS IS NEEDED:
--   AdminSeeder is idempotent — it only creates the admin on first boot when no
--   ADMIN role user exists. If ADMIN_PASSWORD in .env changes after the first
--   boot (or the DB is kept across re-deploys), the seeder skips and the stored
--   BCrypt hash no longer matches the new plaintext. Login returns 401.
--
-- HOW TO USE (three steps):
--
-- Step 1 — Generate a BCrypt-12 hash for the new plaintext.
--   Run GenHash.java (see scripts/GenHash.java) with the project's
--   spring-security-crypto jar and pass ADMIN_PASSWORD via env:
--
--     ADMIN_PASSWORD=<plaintext> java \
--       -cp spring-security-crypto-6.x.x.jar:commons-logging-1.x.x.jar:. \
--       GenHash
--
--   Verify: hash is 60 chars, starts with $2a$12$ or $2b$12$.
--   Do NOT hand-write or copy a hash from external sources.
--
-- Step 2 — Replace <GENERATED_HASH> below with the hash from Step 1.
--   Confirm the WHERE clause matches your ADMIN_EMAIL in .env.
--
-- Step 3 — Apply inside the postgres container:
--
--     cat scripts/reset-admin-password.sql | \
--       docker exec -i gemek-postgres psql -U gemek -d gemek
--
--   Expected output: UPDATE 1 (exactly 1 row).

UPDATE users
SET    password_hash = '<GENERATED_HASH>'
WHERE  email = '<ADMIN_EMAIL>'       -- replace with value from .env ADMIN_EMAIL
  AND  role  = 'ADMIN';

-- Sanity: confirm exactly 1 row exists for this admin
SELECT id, email, role,
       LENGTH(password_hash) AS hash_len,
       LEFT(password_hash, 7) AS hash_prefix
FROM   users
WHERE  email = '<ADMIN_EMAIL>'
  AND  role  = 'ADMIN';
