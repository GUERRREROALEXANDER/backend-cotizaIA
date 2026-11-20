--
-- Quotations from WhatsApp: a user who verified their number can quote by chatting with the CotizaIA number.
--
-- Portable on purpose (no vendor syntax), like V30 and V31.
--
-- whatsapp_contacts.phone_hash is the blind index (HMAC, same key and normalization as users.email_hash) of the
-- verified number. The number itself stays encrypted, so this hash is how an incoming message finds its user. It is
-- unique: a number belongs to one user, and verifying it on another account moves it there. Rows verified before this
-- migration have no hash and are simply not recognized until verified again (none exist in production yet).
--
-- whatsapp_quote_sessions keeps where each user is in the chat that builds a quotation (client name, email,
-- description, a clarifying question from the agent). It lives in the database, not in memory, because the backend
-- restarts often on the free hosting plan and a half-written quotation must survive it. client_name is encrypted
-- like the rest of the client data, hence TEXT.
--
ALTER TABLE whatsapp_contacts ADD COLUMN phone_hash VARCHAR(64) NULL;
ALTER TABLE whatsapp_contacts ADD CONSTRAINT uq_whatsapp_contacts_phone_hash UNIQUE (phone_hash);

CREATE TABLE whatsapp_quote_sessions (
    user_id      BIGINT PRIMARY KEY,
    step         VARCHAR(20) NOT NULL,
    client_name  TEXT NULL,
    client_id    BIGINT NULL,
    brief_id     BIGINT NULL,
    question_id  BIGINT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_whatsapp_quote_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_whatsapp_quote_sessions_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE SET NULL,
    CONSTRAINT fk_whatsapp_quote_sessions_brief FOREIGN KEY (brief_id) REFERENCES briefs (id) ON DELETE SET NULL,
    CONSTRAINT ck_whatsapp_quote_sessions_step
        CHECK (step IN ('IDLE', 'CLIENT_NAME', 'CLIENT_EMAIL', 'DESCRIPTION', 'QUESTION'))
);
