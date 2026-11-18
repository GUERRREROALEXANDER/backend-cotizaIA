# Spec 16 - Email: account verification and document delivery

## Why
New accounts are never verified and nothing leaves the system by email. Wanted: a verification email when an
account is created, and the proposal/contract PDFs mailed to the person who made the quotation.

## Contract
- **Port + adapters (Adapter pattern).** `mail.EmailGateway { void send(EmailMessage) }`, `EmailMessage(to, subject,
  text, attachments[name, contentType, bytes])`. `SmtpEmailGateway` (Spring Mail `JavaMailSender`) is the bean when
  `mail.enabled=true`; otherwise `NoopEmailGateway` logs one line `email skipped: <subject>` (never recipient or body).
- **Env** (all optional, mail is OFF by default): `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT` (587), `MAIL_USERNAME`,
  `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_STARTTLS` (true), `APP_PUBLIC_URL` (link base), `MAIL_VERIFICATION_REQUIRED`
  (false), `MAIL_SEND_TO_CLIENT` (false). Secrets only from env; add them to `.env.example` and `render.yaml`
  (`sync:false` for secrets) and the README config table.
- **Verification.** Migration `V30__email_verification.sql` (vendor-folder convention like V28/V29): `app_users`
  (check the real table name) gets `email_verified boolean not null default true` (existing users stay verified) and a
  table `email_verification_tokens(id, user_id, token_hash, expires_at, used_at)`; token = 32 random bytes URL-safe,
  only its SHA-256 stored, valid 24 h, single use.
  - `POST /api/auth/register`: after creating the user, if `mail.enabled`: create a token and mail a Spanish message
    with `${APP_PUBLIC_URL}/verify-email?token=...`. If `MAIL_VERIFICATION_REQUIRED=true` the new user is created with
    `email_verified=false`; otherwise it stays true. The register response gains `emailVerificationRequired` (boolean).
  - `POST /api/auth/login` for an unverified user: `403 {"error":"Email not verified"}`.
  - `POST /api/auth/verify-email {token}` -> 204 and marks the user verified; invalid/expired/used token -> 400
    `{"error":"Invalid or expired token"}`. `POST /api/auth/resend-verification {email}` -> always 204 (no account
    enumeration), sends only if the user exists and is unverified; both public, CSRF-exempt like login, rate limited
    like the other auth endpoints (reuse `RateLimiter`).
  - The demo agency must keep working: `DemoSeeder` and `scripts/seed_demo.py` accounts are never blocked (when the
    flag is on, mark the demo user verified in `DemoSeeder`; for the script, `/verify-email` is not needed because the
    flag is off in the demo deploy: say so in the README).
- **Documents.** Observer: after commit of `ApprovalQueueService.send` and of `ProposalAcceptanceService.accept`
  (follow `KnowledgeObserver`: `@TransactionalEventListener(AFTER_COMMIT)`, event records, failures caught and logged
  with the proposal id only, never failing the request). Recipient = the authenticated user who performed the action
  (their decrypted email); plus the client's email only when `MAIL_SEND_TO_CLIENT=true`. Send -> proposal PDF attached;
  accept -> contract PDF attached, deposit amount and reference in the text. Spanish text, no raw model output.
  Email text never contains the access token or anything not already visible to that user.

## Judge
Tests with GreenMail (add `com.icegreen:greenmail-junit5` test scope; if it cannot resolve, use a capturing
`JavaMailSender` stub): verification mail is sent on register with a working one-time link; verify-email marks the user
verified and the same token fails the second time; expired token 400; login 403 only when required and unverified;
resend never reveals existence; send/accept deliver the right attachment to the acting user and to the client only
with the flag; a failing SMTP never fails the HTTP call; noop gateway sends nothing and logs no address. Then full
suite 0 failures (someone else runs it). Out of scope: HTML templates, bounce handling, password reset, queues.
