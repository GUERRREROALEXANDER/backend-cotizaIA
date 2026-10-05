--
-- Agency email remains unique only within an agency; login requires a global key.
-- Set lower-case login_email only for users with credentials. NULLs are distinct
-- in this portable unique key, as with owner_flag in V2, leaving staff without
-- credentials and the existing per-agency email constraint unaffected.
--
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN login_email VARCHAR(255) NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_login_email UNIQUE (login_email);
