# Spec 11 - Automatic email intake from Gmail

## Why
Briefs that arrive by email were copy-pasted into the UI. Every new email to the `cotiza@` mailbox that a Gmail
filter labels `cotizar` must become a brief on its own, through the same `EmailBriefAdapter` the API uses.

## Judge
`src/test/java/com/cotizaia/ingest/gmail/*Tests.java` (23 tests) and the full suite.

## Contract
- **Off by default.** `gmail.enabled=false` creates no Gmail bean; POST `/api/ingest/email` then falls under the
  normal authenticated chain (401).
- **Source of "new" = Gmail history.** `GmailIngestService.sync()` reads `users.history.list`
  (`messageAdded` + `labelAdded`, filtered by the label id) from a cursor stored in `gmail_sync_state`. The first
  run only stores the current `historyId`: older mail is not backfilled. A 404 (cursor older than Gmail's ~7-day
  window) falls back to `label:cotizar newer_than:7d` and resets the cursor.
- **Two triggers, one path.** Poll: `@Scheduled` every `gmail.poll-interval` (PT1M). Push: Pub/Sub POSTs to
  `/api/ingest/email`; the controller verifies the OIDC token, checks `emailAddress` is our mailbox, queues a sync
  and answers 204. The push's own `historyId` is never trusted, so a replayed push can only cause an extra read.
  In push mode the poll stays as a safety net (PT15M) and `users.watch` is renewed at start and daily (it expires
  after 7 days).
- **Only labelled mail.** A message whose `labelIds` lacks the `cotizar` label id is ignored and not recorded.
- **Idempotency.** `gmail_ingested_messages.message_id` (Gmail id) is the primary key. The claim is
  `INSERT ... ON CONFLICT DO NOTHING` (`save()` would merge and overwrite). The row is written before the brief, so
  a crash leaves a FAILED/CLAIMED row, never a duplicate brief. Statuses: `BRIEF_CREATED`, `PROCESSED`,
  `PROCESS_FAILED` (brief kept, reprocess from the UI), `FAILED`, `SKIPPED_UNKNOWN_SENDER`, `SKIPPED_EMPTY`.
- **Reuse, no new Brief code.** The brief is created by `BriefService.ingest(agencyId, EMAIL, clientId, payload)`,
  which resolves `EmailBriefAdapter` from `BriefSourceRegistry`. Payload: `from`, `subject`, `body`, `receivedAt`,
  `gmailMessageId`, `messageIdHeader`, `truncated`. `EmailBriefAdapter` now reads the optional ISO-8601
  `receivedAt` (unparseable = ingest time). With `gmail.auto-process=true` the brief then goes through
  `QuotationFacade.processBrief(agencyId, briefId)`.
- **Client.** Sender address -> `ClientRepository.findByAgencyIdAndEmailHash` (blind index). Unknown sender: new
  client (`gmail.create-unknown-senders=true`) or `SKIPPED_UNKNOWN_SENDER`.
- **Body.** First `text/plain` part (attachments skipped), else `text/html` with tags stripped; quoted reply
  dropped; capped at 5,000 chars (the adapter's limit) with `truncated=true`.
- **Least privilege.** OAuth scope `gmail.readonly` only. At most `gmail.max-messages-per-run` (20) per sync, so a
  flood cannot burn the LLM quota; the rest is read on the next run.

## Files
| file | change |
|---|---|
| `db/migration/V28__gmail_ingest.sql` | new: ledger + cursor tables |
| `ingest/gmail/GmailProperties` | new: `gmail.*` settings |
| `ingest/gmail/GmailClient`, `GmailRestClient` | new: port + plain-HTTP adapter (refresh-token auth) |
| `ingest/gmail/GmailMessage`, `GmailMessageParser` | new: MIME -> sender/subject/body |
| `ingest/gmail/GmailIngestService` | new: sync, claim, brief, facade |
| `ingest/gmail/GmailIngestedMessage(+Repository)`, `GmailSyncState(+Repository)` | new: JPA |
| `ingest/gmail/GmailIngestScheduler` | new: poll + daily watch renewal |
| `ingest/gmail/GmailPushController`, `PushTokenVerifier`, `GooglePushTokenVerifier` | new: webhook + OIDC check |
| `ingest/gmail/GmailIngestConfiguration` | new: conditional wiring + `@Order(0)` chain for the webhook |
| `ingest/EmailBriefAdapter` | edit: optional `receivedAt` |
| `application.yml`, `.env.example` | edit: `gmail.*` / `GMAIL_*` |

Why a separate security chain: Pub/Sub sends a Google OIDC token in `Authorization`. The app's resource server
would try to decode it as an app JWT and answer 401 before `permitAll` is consulted.

## Google Cloud setup (step by step)

Pub/Sub's free tier (10 GB/month) covers this; nothing here costs money.

### A. Project and APIs
1. https://console.cloud.google.com -> create project `cotizaia-mail` (or reuse one). Note the project id and number.
2. Enable the APIs:
   ```bash
   gcloud config set project PROJECT_ID
   gcloud services enable gmail.googleapis.com pubsub.googleapis.com
   ```

### B. OAuth client and refresh token (poll and push both need this)
3. **Google Auth Platform -> Branding / Audience**: user type **Internal** if `cotiza@` is a Workspace account,
   **External** if it is a plain Gmail account. Under **Data access** add the scope
   `https://www.googleapis.com/auth/gmail.readonly`.
4. External only: **publish the app ("In production")**. In "Testing" status Google expires refresh tokens after
   **7 days** and the intake goes silent a week later. Unverified + In production is fine for your own mailbox;
   the consent screen shows a warning you click through once.
5. **Clients -> Create client -> Web application**. Authorized redirect URI:
   `https://developers.google.com/oauthplayground`. Copy the client id and secret.
6. Get the refresh token: open https://developers.google.com/oauthplayground -> gear icon -> "Use your own OAuth
   credentials" -> paste id/secret. In step 1 type the scope `https://www.googleapis.com/auth/gmail.readonly`,
   authorize **signed in as cotiza@**, then "Exchange authorization code for tokens". Copy the `refresh_token`.
   It reads the whole mailbox: it goes into Render's env only, never into git or chat.

### C. Gmail label and filter
7. In the `cotiza@` mailbox: Settings -> Labels -> create `cotizar`.
8. Settings -> Filters -> Create filter: `To: cotiza@yourdomain` (narrow it more if you want, e.g. a subject
   word) -> **Apply the label: cotizar** + **Never send it to Spam**. Only mail this filter labels becomes a brief.

### D. Backend env (Render) - poll mode
9. Set on the Render service and redeploy:
   ```
   GMAIL_ENABLED=true
   GMAIL_MAILBOX=cotiza@yourdomain
   GMAIL_LABEL=cotizar
   GMAIL_AGENCY_ID=<id of the agency that owns the mailbox>
   GMAIL_CLIENT_ID=... GMAIL_CLIENT_SECRET=... GMAIL_REFRESH_TOKEN=...
   GMAIL_POLL_INTERVAL=PT1M
   ```
   The log shows `Gmail intake started for cotiza@...; cursor at historyId N`. Mail sent **after** that line is
   picked up within a minute.
   **Caveat:** a Render free instance sleeps after 15 idle minutes and the scheduler sleeps with it. On a free
   plan use push (an HTTP request wakes the instance) or a paid instance.

### E. Pub/Sub push (optional, replaces the 1-minute wait)
10. Topic, and let Gmail publish to it:
    ```bash
    gcloud pubsub topics create gmail-cotiza
    gcloud pubsub topics add-iam-policy-binding gmail-cotiza \
      --member=serviceAccount:gmail-api-push@system.gserviceaccount.com --role=roles/pubsub.publisher
    ```
    (An org policy "Domain restricted sharing" blocks this binding; it needs an exception for that account.)
11. Service account whose identity signs the push token, and let Pub/Sub mint tokens for it:
    ```bash
    gcloud iam service-accounts create gmail-push --display-name="Gmail push to CotizaIA"
    gcloud iam service-accounts add-iam-policy-binding gmail-push@PROJECT_ID.iam.gserviceaccount.com \
      --member=serviceAccount:service-PROJECT_NUMBER@gcp-sa-pubsub.iam.gserviceaccount.com \
      --role=roles/iam.serviceAccountTokenCreator
    ```
12. Push subscription with OIDC auth (audience = the exact endpoint URL):
    ```bash
    gcloud pubsub subscriptions create gmail-cotiza-push --topic=gmail-cotiza \
      --push-endpoint=https://BACKEND_HOST/api/ingest/email \
      --push-auth-service-account=gmail-push@PROJECT_ID.iam.gserviceaccount.com \
      --push-auth-token-audience=https://BACKEND_HOST/api/ingest/email \
      --ack-deadline=30 --message-retention-duration=1d
    ```
13. Add to Render and redeploy:
    ```
    GMAIL_PUSH_ENABLED=true
    GMAIL_PUSH_TOPIC=projects/PROJECT_ID/topics/gmail-cotiza
    GMAIL_PUSH_AUDIENCE=https://BACKEND_HOST/api/ingest/email
    GMAIL_PUSH_SERVICE_ACCOUNT=gmail-push@PROJECT_ID.iam.gserviceaccount.com
    GMAIL_POLL_INTERVAL=PT15M
    ```
    The log shows `Gmail watch on label cotizar renewed until ...`. The backend calls `users.watch` itself at start
    and daily; nothing to run by hand.

### F. Check it end to end
14. From a known client's address, send a mail to `cotiza@` that the filter labels. Expect in the log
    `Gmail message <id> became brief <n>`, and the brief in `GET /api/briefs` with `source=EMAIL`.
15. Push only: Pub/Sub -> subscription `gmail-cotiza-push` -> Metrics should show acked pushes, no 401s. A 401
    means audience or service-account email differ between the subscription and the env vars.
16. Idempotency is proven by the tests (you cannot resend the same Gmail id by hand); in prod, watch
    `select status, count(*) from gmail_ingested_messages group by status`.
