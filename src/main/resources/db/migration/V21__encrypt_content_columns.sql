-- V21__encrypt_content_columns.sql
-- Widen encrypted content columns to TEXT and add blind-index hash columns.
-- No backfill: existing deployments start from an empty database (demo data only).

-- Widen encrypted VARCHAR columns to TEXT.
ALTER TABLE agencies ALTER COLUMN name SET DATA TYPE TEXT;

ALTER TABLE users ALTER COLUMN full_name SET DATA TYPE TEXT;
ALTER TABLE users ALTER COLUMN email SET DATA TYPE TEXT;
ALTER TABLE users ALTER COLUMN login_email SET DATA TYPE TEXT;

ALTER TABLE clients ALTER COLUMN name SET DATA TYPE TEXT;
ALTER TABLE clients ALTER COLUMN email SET DATA TYPE TEXT;
ALTER TABLE clients ALTER COLUMN company SET DATA TYPE TEXT;

ALTER TABLE brief_attachments ALTER COLUMN file_name SET DATA TYPE TEXT;

ALTER TABLE brief_questions ALTER COLUMN question SET DATA TYPE TEXT;

ALTER TABLE extracted_requirements ALTER COLUMN description SET DATA TYPE TEXT;

ALTER TABLE agent_steps ALTER COLUMN input_summary SET DATA TYPE TEXT;
ALTER TABLE agent_steps ALTER COLUMN output_summary SET DATA TYPE TEXT;

-- Blind-index hash columns.
ALTER TABLE users ADD COLUMN email_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN login_email_hash VARCHAR(64);
ALTER TABLE clients ADD COLUMN email_hash VARCHAR(64);

-- Replace login_email unique constraint with hash-based unique constraint.
ALTER TABLE users DROP CONSTRAINT uq_users_login_email;
ALTER TABLE users ADD CONSTRAINT uq_users_login_email_hash UNIQUE (login_email_hash);