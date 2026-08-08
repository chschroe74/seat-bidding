# Office Seat Bidding Application — Implementation Specification

**Status:** Approved implementation specification  
**Target release:** Version 1  
**Primary client:** Flutter Progressive Web App (PWA)  
**Optional client:** Native Flutter Android application  
**Backend:** Java 25, Quarkus, PostgreSQL, Hibernate ORM with Panache, Liquibase, application-managed authentication, Quarkus Scheduler, Jib

## 1. Purpose

The application distributes a limited number of office seats fairly among employees. Employees receive tokens for each bidding round and privately bid for individual weekdays. At the configured cutoff, the backend ranks bids per day, resolves only capacity-boundary ties randomly, publishes assignments, deducts only successful bids, and opens the next round.

This document is the authoritative version 1 implementation contract. Requirements marked **MUST**, **SHOULD**, and **MAY** have their conventional meanings.

## 2. Scope

### 2.1 Version 1 goals

- Support one office and one shared seat capacity for every weekday.
- Run one weekly round covering Monday through Friday.
- Let authenticated, pre-provisioned employees view published assignments and replace their bids until cutoff.
- Keep all employees' open-round bids private.
- Maintain auditable token balances and round results.
- Serve the compiled Flutter PWA and REST API from one Quarkus application image.
- Optionally distribute a native Android client using the same API.

### 2.2 Explicitly out of scope

- Administrative UI or automatic employee provisioning.
- Public-holiday and office-closure handling.
- Different capacities per day, multiple offices, named desks, or reservations outside bidding.
- Half-day attendance and seat sharing (defined as a future extension in section 20).
- Non-weekly or overlapping round cadences in the UI.
- Kubernetes or multiple active scheduler instances.
- Server-driven PWA reminders and guaranteed offline bidding.
- Managing unassigned seats or employees who attend without an assignment.

## 3. Terminology and time model

- **Round:** One immutable business period created for one scheduled allocation. It has an open bidding window and five target dates. The model MUST NOT rely only on a calendar week number.
- **Target date:** One Monday–Friday date covered by a round.
- **Open round:** The only round whose bids may be edited.
- **Published round:** The latest completed round shown in **Seat assignments**. Immediately after Friday cutoff this normally covers the following Monday–Friday, even though that week has not begun.
- **Balance:** Tokens available to an employee for the open round.
- **Bid:** A positive integer token amount for one round date. Zero means no bid and need not be persisted.
- **Carry-over:** The capped portion of the balance remaining after successful bids are deducted. Tokens committed to unsuccessful bids remain unspent.
- **Cutoff:** An instant computed by the backend from a Quartz cron expression and IANA time zone.

All persisted instants MUST use UTC (`timestamptz`/`Instant`). Target dates use `date`/`LocalDate`. The scheduler interprets its cron expression in the configured IANA zone, including daylight-saving transitions.

## 4. Business configuration

The following settings come from Quarkus application configuration and are validated at startup:

| Setting | Default | Constraint |
|---|---:|---|
| Tokens granted per round | 50 | integer `>= 0` |
| Carry-over cap | 20 | integer `>= 0` |
| Seat capacity | deployment-specific | integer `>= 1`, same for all dates |
| Scheduler cron | `0 0 22 ? * FRI` | valid Quarkus/Quartz cron |
| Scheduler time zone | `Europe/Berlin` | valid IANA zone |
| Scheduler enabled | `true` | boolean |
| Lock timeout | deployment-specific, recommended 5 seconds | positive duration |

Every round MUST snapshot the first three business values, cutoff instant, configured time zone, and schedule-derived target dates. Changing runtime configuration MUST affect only subsequently created rounds, not an already open or completed round.

## 5. Core business rules

### 5.1 Round and token rules

1. Version 1 has exactly one open round and it contains five consecutive target dates, Monday through Friday. Weekends are excluded; public holidays are treated as ordinary weekdays.
2. Each participating employee receives the round's token grant plus carry-over from the preceding round.
3. After allocation, remaining balance is `startingBalance - successfulBidTokens`. Carry-over is `min(carryOverCap, remainingBalance)`.
4. The next starting balance is `tokensGrantedPerRound + carryOver`.
5. A manually provisioned employee appearing during an open round immediately receives the full standard grant for that round; it is not prorated. There is no historic carry-over for that employee unless explicitly migrated.
6. The sum of an employee's positive bids MUST NOT exceed the employee's round starting balance.
7. Submitted tokens are reserved for the current bid set, so their total cannot exceed the starting balance. At processing, only bids that receive a seat are spent.
8. Tokens not bid and tokens committed to unsuccessful bids remain unspent and are eligible for carry-over.
9. Balances and ledger entries use non-negative whole integers only.

### 5.2 Bid rules

- An employee may bid once per target date; a submission replaces the employee's complete five-date bid set atomically.
- Bids may be replaced any number of times while the round is `OPEN` and before its cutoff instant.
- Zero is equivalent to no bid and MUST be removed or omitted in persistence.
- Negative, fractional, duplicate-date, out-of-round, weekend, or over-budget bids are invalid.
- The backend derives the employee from Quarkus `SecurityIdentity` established by the validated form-authentication cookie; a client-supplied employee identifier is forbidden.
- Other employees' open-round bids MUST never be returned by any endpoint.

### 5.3 Allocation rules

For each target date independently:

1. Select all positive bids.
2. Rank them by token amount descending.
3. If the number of bidders is at most seat capacity, every bidder receives a seat and surplus seats remain unassigned.
4. If a group tied at the same amount lies entirely above or below the capacity boundary, no random selection is needed.
5. If a tie crosses the capacity boundary, select only the required number uniformly at random from that tied group.
6. Persist the final rank, tie group information, selection result, and a reproducibility/audit value (for example a securely generated random draw value or seed plus deterministic algorithm version). Results MUST never be redrawn when read.
7. A successful bidder spends the full bid amount.
8. An unsuccessful bidder spends no tokens for that bid; its full amount remains part of the employee's balance before the carry-over cap is applied.

The ranking shown after publication places successful employees above the seat boundary. Within equal-result tied groups, persisted draw order determines display order. No physical seat number is assigned in version 1.

### 5.4 Visibility

- Before processing, an employee can see only their own bid set, balance, dates, and public round/configuration metadata.
- After processing, all authenticated employees can see all bidders' names, bid amounts, final ordering, and success status for the published round.
- Employees who did not bid on a date do not appear in its participant list.

## 6. Round lifecycle and scheduler

### 6.1 States

`OPEN -> PROCESSING -> COMPLETED`

`FAILED` MAY be used for operational visibility, provided retry behavior is explicit and safe. A completed round is immutable. State transitions and timestamps are persisted.

### 6.2 Initialization

On a fresh database, an idempotent bootstrap service MUST create an open round with the next valid cutoff and corresponding Monday–Friday dates. It creates participation/balance records for all provisioned employees.

### 6.3 Scheduled processing

The `quarkus-scheduler` job runs using the configured Quartz expression and time zone. Version 1 assumes one Quarkus instance; it does not implement distributed locking, clustered Quartz, or leader election. The job MUST nevertheless be transactional and idempotent.

At each trigger, the service MUST:

1. Find the due `OPEN` round and atomically mark it `PROCESSING`; do nothing if no due round exists.
2. Lock the round for write and verify that it has not already been processed.
3. For every target date, allocate capacity and persist all results.
4. Persist bid-spend ledger entries for successful bids only.
5. Calculate each participant's successful-bid spend, remaining balance, carry-over, and closing balance. Unsuccessful bids do not create a debit or refund entry because they are never spent.
6. Mark the round `COMPLETED` with `processed_at`.
7. Create exactly one successor `OPEN` round, snapshot current configuration, and create its five dates.
8. Create participation records for all provisioned employees with `grant + carry-over`.

These operations SHOULD commit in one transaction. If transaction size later becomes a concern, a staged design is allowed only if externally invisible, restartable, and protected by equivalent constraints.

Database uniqueness constraints MUST prevent duplicate successor rounds, bids, assignments, participation records, and ledger effects. Retrying after rollback MUST produce the same business result except for a new random draw if no previous draw was committed.

### 6.4 Boundary behavior

The backend's current instant is authoritative. A request arriving at or after cutoff MUST be rejected even if the scheduled job has not yet completed. The UI refreshes after cutoff and may temporarily show processing status. The assignment view switches to the newly completed round as soon as it is published; the bidding view switches to its successor.

## 7. Authentication, identity, and security

### 7.1 Account model and provisioning

- Employees are created manually in PostgreSQL before they may use the application. The application never self-registers an unknown email address.
- Email is the only login identifier. It is trimmed, Unicode-normalized as appropriate, converted to lowercase, and matched case-insensitively.
- Every employee is provisioned with email, first name, and last name. A password hash is initially null.
- There is no separate username, external identity provider, role model, or administrative UI in version 1.

### 7.2 Quarkus Security architecture

- Use Quarkus's built-in form-based HTTP authentication mechanism to process email/password login, create the encrypted persistent authentication cookie, renew it during activity, resolve authenticated requests to `SecurityIdentity`, and perform server-side logout.
- Configure form authentication for SPA behavior: no login, landing, or error redirects. Successful submission returns `200`; invalid credentials return `401`.
- Use a custom Quarkus `IdentityProvider<UsernamePasswordAuthenticationRequest>` only for the missing credential-verification piece. It normalizes the submitted email, loads the employee with Panache, verifies the Argon2id hash through a maintained library, and returns a `SecurityIdentity` whose principal name is the normalized email.
- Do not implement authentication in an ad hoc Jakarta REST filter. Quarkus `HttpAuthenticationMechanism`, `IdentityProvider`, `SecurityIdentity`, path permissions, and security annotations are the integration points.
- The built-in form login endpoint is `/j_security_check`. It accepts `application/x-www-form-urlencoded` fields `j_username` (the email) and `j_password`.
- Use `quarkus-rest-csrf` for CSRF token generation and verification. Do not implement a separate CSRF algorithm.
- Use `quarkus-mailer` for SMTP delivery and `MockMailbox` in development/tests.

### 7.3 Authentication entry flow

The login screen initially contains only an email field. Submitting it calls the authentication-start endpoint:

1. The backend normalizes the email and looks up an enabled, pre-provisioned employee.
2. If the employee has a password hash, the response instructs the client to show the password screen.
3. If the employee has no password hash and has a valid pending activation, the response instructs the client to show the six-digit code screen without automatically sending another message.
4. If the employee has no password and no valid pending activation, the backend generates and sends an activation code, then instructs the client to show the code screen.
5. Unknown accounts are not created and receive a generic response that does not disclose names or other account details. Authentication endpoints are rate-limited by normalized email and source address to reduce enumeration and abuse.

The client retains the submitted email only as flow state. Every security decision and email destination comes from the server-side employee record.

### 7.4 First-time activation

- Generate a uniformly random six-digit decimal code (`000000` through `999999`) using a cryptographically secure random generator. Leading zeroes are significant.
- The plaintext code exists only long enough to compose the email. Store an HMAC-SHA-256 (or equivalent keyed digest) using a separately configured activation-code pepper; a plain hash is insufficient for a six-digit secret.
- A code is single-use and expires after 15 minutes by default. Expiry is configurable.
- Limit verification to five failed attempts per code by default. On exhaustion, invalidate the code and require resend. Limits are configurable.
- **Resend code** is available on the code screen. It generates a new code, invalidates all earlier codes for that employee, and sends it only to the provisioned email address. Enforce a configurable resend cooldown (default 60 seconds) and rolling request limits.
- A returning employee with no password and an unexpired pending code resumes on the code screen. If the code expired, the screen offers resend; the backend does not accept the expired code.
- Successful verification consumes the code and creates a short-lived, single-use activation authorization. The client must present that authorization when setting the password; verified state must never be inferred from an email address supplied by the client.
- The activation authorization is an opaque cryptographically random value. Store only its hash, expire it after 15 minutes by default, and invalidate it after successful password creation.
- Do not place codes or activation authorizations in URLs, logs, metrics, traces, analytics, or error details.

Email sending uses authenticated SMTP configured at runtime. Persist the new code state before sending. If delivery fails, return a retryable error without claiming success; the user may use resend. Email content identifies the application, contains the code and its expiry, and states that the message can be ignored if the recipient did not initiate activation.

### 7.5 Password creation and login

- Password creation requires the valid activation authorization plus `password` and `passwordConfirmation`.
- The password and confirmation must match. A password must contain at least 15 Unicode code points. Do not impose uppercase, lowercase, digit, symbol, or other composition rules.
- Allow at least 128 Unicode code points, including spaces and all printing characters, and never silently truncate input.
- Reject passwords found in a maintained local blocklist of common, expected, or compromised passwords. The check must not send the candidate password or a derived value to an external service. The blocklist and its update procedure are versioned operational assets.
- Treat the password exactly as entered: do not trim it, change case, or silently normalize it. Validation, confirmation, hashing, and later verification operate on the same UTF-8 string representation.
- Hash passwords with Argon2id through a maintained library. Store the encoded PHC string containing algorithm, salt, and work parameters; never store or reversibly encrypt a password.
- Argon2id memory, iteration, and parallelism parameters are configurable and chosen according to deployment capacity. Verification must support rehashing after parameters are strengthened.
- The custom Quarkus identity provider compares the submitted password through the Argon2id library and returns a generic invalid-credentials result for an incorrect email/password combination.
- Apply progressive throttling/rate limits by account and source address. Avoid permanent automatic account lockout that an attacker could use for denial of service.
- Successful password creation stores the password hash transactionally and consumes activation state. The Flutter client immediately and automatically posts the same email/password to `/j_security_check`; the user is then taken directly into the application without manually logging in again.
- Version 1 has no password change or forgotten-password flow. An operator may clear the password hash and activation state through a controlled database operation to require activation again; any already issued stateless cookie remains valid until expiry unless the global session-encryption key is rotated.

### 7.6 Persistent form-authentication cookie

Quarkus form authentication stores the authenticated identity and idle expiry in an encrypted cookie rather than in a server-side session table:

- Set the inactivity timeout to 30 days by default and the new-cookie/renewal interval to 24 hours. Active use renews the encrypted cookie no more frequently than the configured interval.
- Set cookie `Max-Age` to 30 days so authentication survives browser/app restarts and is refreshed when Quarkus renews the cookie.
- Configure a strong, externally supplied session-encryption key. Changing that key invalidates all outstanding form-authentication cookies.
- The authentication cookie is host-only and has `Secure`, `HttpOnly`, `SameSite=Strict`, and `Path=/`. Do not set a `Domain` attribute.
- Logout uses `FormAuthenticationMechanism.logout(SecurityIdentity)` and expires the current client's cookie.
- The form cookie is stateless. Version 1 intentionally has no per-device session database, individual server-side session revocation, or separate absolute session lifetime. A copied cookie remains valid until its encrypted idle expiry unless the global encryption key is rotated.

The PWA relies on the browser cookie jar and cannot read the HttpOnly authentication cookie. Flutter web requests must include credentials.

Android uses the same form login endpoint and encrypted authentication cookie. Its HTTP client must persist the authentication cookie using platform-secure storage and send it through a managed cookie jar. It must not copy the cookie into ordinary preferences, logs, crash reports, analytics, or an `Authorization` header.

### 7.7 CSRF, request authentication, and web security

- `quarkus-rest-csrf` issues a separate CSRF cookie and verifies the matching `X-CSRF-TOKEN` request header on state-changing requests. Configure an HMAC signature key of at least 32 characters, JSON-request support, `Secure`, `SameSite=Strict`, and a JavaScript-readable CSRF cookie. The authentication cookie remains HttpOnly.
- Both PWA and Android obtain the CSRF cookie from the configured token-creation GET endpoint and echo its value in the header for Jakarta REST state-changing requests, including public activation endpoints.
- `/j_security_check` is handled by Quarkus HTTP authentication before Jakarta REST, so it MUST additionally enforce the configured allowed `Origin` (and a safe `Referer` fallback where appropriate) through Quarkus HTTP security customization. Combined with HTTPS and `SameSite=Strict`, this prevents login CSRF without reimplementing the REST CSRF token algorithm.
- All `/api/*` endpoints except the explicitly documented public configuration, CSRF-token creation, and activation-flow endpoints require Quarkus authentication. `/j_security_check` is also public but CSRF-protected. Health and OpenAPI exposure is controlled separately by deployment configuration.
- Resources use Quarkus security annotations/path policies and obtain the acting user from `SecurityIdentity`; they never trust a client-provided employee identity.
- PWA API calls use relative same-origin `/api/...` URLs. Android uses its configured absolute HTTPS base URL; browser CORS, if enabled at all, uses a strict allowlist.
- TLS is mandatory outside local development. Apply CSP, HSTS at the HTTPS boundary, secure headers, request-size limits, dependency scanning, secret rotation procedures, and redaction of authentication and personal data in logs.
- Passwords, activation codes, activation authorizations, authentication cookies, stored hashes, session-encryption/CSRF keys, and SMTP credentials MUST never be returned by ordinary APIs or written to logs, metrics, or traces.

## 8. Concurrency and transactions

Bid replacement MUST use pessimistic database locking, not an optimistic version column:

1. Begin a transaction.
2. Load the authenticated employee's participation record for the open round with `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`).
3. Re-read round state/cutoff and recalculate balance within the transaction.
4. Validate the entire replacement set.
5. Replace all existing bids atomically and commit.

The lock is scoped to one employee and round, allowing different employees to submit concurrently. A lock timeout/deadlock maps to `409 Conflict` (or `503` if the database is broadly unavailable) with a retryable problem code. No `@Version` column is required.

Scheduler processing locks the due round. Password creation locks the employee and activation rows. All balance- and activation-state changes use database transactions even in the initial single-instance deployment; form-cookie renewal itself is handled by Quarkus and has no database transaction.

## 9. Database model

Use PostgreSQL-generated `bigint` identities or UUIDs consistently. The following uses `bigint` for readability. Every table SHOULD include `created_at`; mutable tables SHOULD include `updated_at`. All foreign keys are indexed.

### 9.1 `employee`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK, not null |
| `email` | `varchar(320)` | not null, stored normalized lowercase, unique |
| `first_name` | `varchar(255)` | not null |
| `last_name` | `varchar(255)` | not null |
| `password_hash` | `varchar(512)` | nullable encoded Argon2id PHC string |
| `password_set_at` | `timestamptz` | nullable; set with password hash |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

Manual provisioning supplies normalized email, first name, and last name. It leaves `password_hash` and `password_set_at` null. Enforce nonblank names/email in application provisioning guidance and a unique normalized email in the database.

### 9.2 `account_activation`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `employee_id` | `bigint` | FK, not null, unique |
| `code_digest` | `varchar(255)` | nullable keyed digest; never plaintext |
| `code_expires_at` | `timestamptz` | nullable |
| `failed_attempts` | `integer` | not null, default `0`, check `>= 0` |
| `last_sent_at` | `timestamptz` | nullable |
| `activation_token_hash` | `varchar(64)` | nullable, unique when non-null |
| `activation_token_expires_at` | `timestamptz` | nullable |
| `verified_at` | `timestamptz` | nullable |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

There is at most one current activation row per employee. Resend replaces the digest and expiry and resets failed attempts. Verification clears the code digest, records `verified_at`, and stores only the hash of the short-lived activation authorization. Successful password creation deletes or fully consumes the row. Database checks SHOULD enforce coherent nullable field combinations; application transactions enforce the state machine.

### 9.3 `bidding_round`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `status` | `varchar(20)` | `OPEN`, `PROCESSING`, `COMPLETED`, optional `FAILED` |
| `sequence_no` | `bigint` | unique, monotonically increasing |
| `bidding_opens_at` | `timestamptz` | not null |
| `cutoff_at` | `timestamptz` | not null, unique |
| `schedule_zone` | `varchar(64)` | not null |
| `tokens_granted` | `integer` | not null, check `>= 0` |
| `carry_over_cap` | `integer` | not null, check `>= 0` |
| `seat_capacity` | `integer` | not null, check `>= 1` |
| `processing_started_at` | `timestamptz` | nullable |
| `processed_at` | `timestamptz` | nullable |
| `predecessor_round_id` | `bigint` | nullable FK, unique |

Enforce at most one `OPEN` round with a PostgreSQL partial unique index. Validate legal timestamps and state transitions in application code.

### 9.4 `round_date`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_id` | `bigint` | FK, not null |
| `target_date` | `date` | not null |
| `ordinal` | `smallint` | not null, `1..5` |

Unique `(round_id, target_date)` and `(round_id, ordinal)`. Application validation requires Monday–Friday and exactly five rows.

### 9.5 `round_participation`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_id` | `bigint` | FK, not null |
| `employee_id` | `bigint` | FK, not null |
| `grant_tokens` | `integer` | not null, `>= 0` |
| `carried_in_tokens` | `integer` | not null, `>= 0` |
| `starting_balance` | `integer` | not null, `grant + carried in` |
| `successful_bid_tokens` | `integer` | not null, default `0` |
| `remaining_balance` | `integer` | nullable until completion; `starting_balance - successful_bid_tokens` |
| `carried_out_tokens` | `integer` | nullable until completion |

Unique `(round_id, employee_id)`. This is the row pessimistically locked for bid replacement.

### 9.6 `bid`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_date_id` | `bigint` | FK, not null |
| `participation_id` | `bigint` | FK, not null |
| `tokens` | `integer` | not null, check `> 0` |

Unique `(round_date_id, participation_id)`. Application code verifies both references belong to the same round.

### 9.7 `seat_assignment`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_date_id` | `bigint` | FK, not null |
| `bid_id` | `bigint` | FK, not null, unique |
| `assigned` | `boolean` | not null |
| `final_rank` | `integer` | not null, `>= 1` |
| `tie_group` | `varchar(64)` | nullable |
| `draw_value` | `varchar(255)` | nullable; audit/reproduction value |
| `algorithm_version` | `varchar(32)` | not null |

Persist one result for every positive bid, including unsuccessful bids. Unique `(round_date_id, final_rank)`.

### 9.8 `token_ledger`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `employee_id` | `bigint` | FK, not null |
| `round_id` | `bigint` | FK, not null |
| `bid_id` | `bigint` | nullable FK |
| `type` | `varchar(32)` | `GRANT`, `CARRY_IN`, `BID_SPEND`, `EXPIRY` |
| `amount` | `integer` | signed, non-zero |
| `idempotency_key` | `varchar(255)` | unique, not null |
| `occurred_at` | `timestamptz` | not null |

The participation row is the efficient balance snapshot; the ledger is the audit source. `BID_SPEND` is written only for a successful bid. Their totals MUST reconcile in tests. `EXPIRY` records remaining tokens removed by the carry-over cap.

### 9.9 Panache mapping

Use explicit entity classes and repositories. Avoid exposing entities directly as REST DTOs. Mark associations lazy where practical, prevent N+1 queries with dedicated projections/fetch joins, and use enum converters for state/type values. Database constraints are mandatory even where Bean Validation duplicates them.

## 10. Liquibase

- Use a root `db/changelog/db.changelog-master.yaml` (or XML) that includes ordered, immutable changelogs.
- The initial changeset creates all tables, checks, foreign keys, unique constraints, partial indexes, and supporting indexes.
- A separate seed/bootstrap mechanism MAY create the first round; do not insert environment-specific employees in production migrations.
- Provide rollback blocks where safe; never edit an applied changeset—append a new one.
- Quarkus runs Liquibase at application startup. Production deployment MUST ensure only one migrator runs; this follows naturally from the version 1 single instance.
- Integration tests MUST start from an empty PostgreSQL database and apply the full changelog.

## 11. REST API

Base path: `/api`. JSON uses camelCase, ISO-8601 instants, and `YYYY-MM-DD` dates. Do not expose persistence entities.

### 11.1 Public configuration

`GET /api/public/configuration`

Unauthenticated, non-sensitive runtime data needed before login:

```json
{
  "androidDownloadUrl": "https://distribution.example/seat-app",
  "apiBasePath": "/api"
}
```

Omit target-inapplicable/null values. Never return secrets or authentication policy internals that would aid an attacker. The PWA uses relative API URLs; Android receives its absolute API base URL through build/flavor configuration, managed mobile configuration, or an equivalent environment mechanism.

### 11.2 Authentication

All authentication responses use `Cache-Control: no-store`. Application authentication endpoints accept JSON over TLS outside local development and are subject to request-size and rate limits. The Quarkus form-login endpoint is the documented exception and consumes URL-encoded form fields. The CSRF cookie/header is required for application authentication POSTs; `/j_security_check` instead requires the strict allowed-origin check described in section 7.7.

#### Obtain CSRF token

`GET /api/auth/csrf` is public and returns `204 No Content`. The `quarkus-rest-csrf` extension creates the signed CSRF cookie. PWA and Android clients read that cookie and send its value as `X-CSRF-TOKEN` on every Jakarta REST state-changing request, including the following public activation requests.

#### Start or resume login

`POST /api/auth/start`

```json
{"email": "alex@example.com"}
```

For a known account, return one of:

```json
{"nextStep": "PASSWORD_REQUIRED"}
```

```json
{
  "nextStep": "CODE_REQUIRED",
  "codeExpiresAt": "2026-08-06T12:15:00Z",
  "resendAvailableAt": "2026-08-06T12:01:00Z"
}
```

This endpoint creates and emails a code only when activation is required and no valid pending code exists. Unknown accounts receive a generic `ACCOUNT_UNAVAILABLE` problem without disclosing account data.

#### Password login

`POST /j_security_check`

```text
Content-Type: application/x-www-form-urlencoded

j_username=alex%40example.com&j_password=user-entered-password
```

This is Quarkus's built-in form-authentication endpoint, not a custom Jakarta REST resource. On success it returns `200` and sets the encrypted persistent authentication cookie for both PWA and Android. Incorrect credentials return a generic `401`. Redirect locations are disabled for the SPA/API clients.

#### Resend activation code

`POST /api/auth/activation/resend`

```json
{"email": "alex@example.com"}
```

Return `202 Accepted` when a resend is accepted. The response for an unknown or already activated account remains generic. A `429` response includes a safe `Retry-After` header when the cooldown or request limit applies.

#### Verify activation code

`POST /api/auth/activation/verify`

```json
{"email": "alex@example.com", "code": "031947"}
```

On success, consume the code and return a short-lived opaque `activationToken` and `expiresAt`. Incorrect, expired, or exhausted codes return a generic problem response; never reveal the expected code or which digit was wrong.

#### Create password

`POST /api/auth/activation/password`

```json
{
  "activationToken": "opaque-one-time-token",
  "password": "user-selected password",
  "passwordConfirmation": "user-selected password"
}
```

On success, atomically store the password hash, consume activation state, and return `204 No Content`. The client immediately submits the same email and password to `/j_security_check`. Once Quarkus sets the encrypted cookie, the client discards the plaintext password and navigates directly to **Seat assignments**. This automatic second HTTP request is part of one user-visible activation flow and must not require the user to re-enter credentials.

#### Logout

`POST /api/auth/logout` requires authentication and the CSRF header. Its implementation calls Quarkus `FormAuthenticationMechanism.logout(SecurityIdentity)` and returns an expired authentication cookie. PWA and Android clear their local cookie state. Logout is idempotent from the user's perspective, but because the cookie is stateless it cannot revoke a copied cookie held elsewhere.

### 11.3 Current user

`GET /api/me`

Returns resolved profile data and no other employee's private data:

```json
{
  "id": 42,
  "firstName": "Alex",
  "lastName": "Example",
  "email": "alex@example.com"
}
```

### 11.4 Bidding context

`GET /api/bidding/current`

```json
{
  "roundId": 18,
  "status": "OPEN",
  "cutoffAt": "2026-08-07T20:00:00Z",
  "cutoffTimeZone": "Europe/Berlin",
  "serverTime": "2026-08-04T10:15:00Z",
  "startingBalance": 70,
  "bidTotal": 20,
  "availableToBid": 50,
  "days": [
    {"date": "2026-08-10", "weekday": "MONDAY", "tokens": 20},
    {"date": "2026-08-11", "weekday": "TUESDAY", "tokens": 0}
  ]
}
```

The response contains all five dates. `availableToBid` is the amount not currently reserved by the editable bid set; it is not the post-allocation balance. `serverTime` supports countdown display; `cutoffAt` is authoritative.

### 11.5 Replace bids

`PUT /api/bidding/current/bids`

```json
{
  "roundId": 18,
  "bids": [
    {"date": "2026-08-10", "tokens": 20},
    {"date": "2026-08-11", "tokens": 8}
  ]
}
```

Semantics:

- `roundId` prevents a stale page from writing into a successor round.
- The array represents the complete replacement set; omitted/zero dates become no bid.
- On success return `200` with the same authoritative shape as `GET /api/bidding/current`.
- Bidding endpoints never accept an email, employee ID, balance, allocation result, or token-spend decision from the client; the acting employee always comes from Quarkus `SecurityIdentity`.

### 11.6 Published assignments

`GET /api/assignments/latest`

```json
{
  "roundId": 17,
  "status": "COMPLETED",
  "publishedAt": "2026-08-07T20:00:02Z",
  "seatCapacity": 12,
  "days": [{
    "date": "2026-08-10",
    "weekday": "MONDAY",
    "myStatus": "ASSIGNED",
    "assignedCount": 12,
    "participants": [{
      "employeeId": 42,
      "firstName": "Alex",
      "lastName": "Example",
      "tokens": 20,
      "assigned": true,
      "rank": 4,
      "isCurrentUser": true
    }]
  }]
}
```

`myStatus` is `NO_BID`, `ASSIGNED`, or `NOT_ASSIGNED`. Participants are already ordered by persisted final rank. Provisioned first and last names are always present.

### 11.7 Help

Help content SHOULD be bundled with Flutter for availability without another authenticated call. If centrally managed later, use `GET /api/public/help` with versioned/sanitized content.

### 11.8 Error contract

Use `application/problem+json`:

```json
{
  "type": "https://seat-app.example/problems/bid-budget-exceeded",
  "title": "Bid total exceeds available balance",
  "status": 400,
  "code": "BID_BUDGET_EXCEEDED",
  "detail": "The submitted total is 72; 70 tokens are available.",
  "instance": "/api/bidding/current/bids",
  "violations": [{"field": "bids", "message": "must total at most 70"}],
  "traceId": "..."
}
```

Status mapping:

| Status | Use |
|---|---|
| `400` | malformed/invalid values, dates, duplicates, negatives, total over balance |
| `401` | invalid credentials; missing, invalid, or expired Quarkus form-authentication cookie |
| `403` | authenticated request lacks required CSRF proof or its origin is rejected |
| `404` | no published/open resource when applicable |
| `409` | stale round, cutoff passed, round processing, pessimistic lock timeout/concurrent update |
| `429` | authentication/code request, resend, verification, or login rate limit exceeded |
| `500` | unexpected server failure, without sensitive details |
| `503` | temporary database/service unavailability |

## 12. Backend implementation

### 12.1 Java platform

The backend MUST use Java 25 for compilation, automated tests, local execution, and production runtime. The Maven or Gradle build MUST set its Java release/toolchain to 25 and fail clearly when an incompatible JDK is used. CI and developer documentation MUST require JDK 25. The selected Quarkus platform version and all build plugins MUST support Java 25; do not lower the bytecode target for compatibility with an older runtime.

### 12.2 Required Quarkus extensions

```text
quarkus-rest-jackson
quarkus-hibernate-orm-panache
quarkus-jdbc-postgresql
quarkus-liquibase
quarkus-scheduler
quarkus-security
quarkus-mailer
quarkus-rest-csrf
quarkus-hibernate-validator
quarkus-container-image-jib
quarkus-config-yaml
quarkus-opentelemetry
quarkus-smallrye-openapi
quarkus-smallrye-health
```

### 12.3 Suggested modules/packages

```text
auth/            activation, Argon2id identity provider, form-auth integration, rate limiting
round/           lifecycle, dates, configuration snapshot
bidding/         bid queries, validation, replacement
allocation/      ranking, tie draw, successful-bid charging
tokens/          participation balances and ledger
resource/        REST resource interfaces only
resource/impl/   REST resource implementation classes
dto/             REST request/response DTOs and problem representations
exception/       application exceptions and REST exception mappers
persistence/     Panache entities and repositories
bootstrap/       initial round and newly provisioned participant reconciliation
```

The package named `api` MUST NOT be used. The `resource` package MUST contain only Java interfaces that define the REST contract. These interfaces carry the JAX-RS endpoint annotations and all SmallRye OpenAPI annotations, including operation descriptions, parameters, response codes, media types, security requirements, and schema references. Concrete classes belong in `resource.impl`, implement the corresponding resource interfaces, and delegate immediately to application/domain services. Endpoint and OpenAPI annotations MUST NOT be duplicated on the implementation classes. DTOs and exception mappers belong in their dedicated packages, not in `resource`.

Domain services own transactions; resource implementations remain thin and contain no business logic. Inject a `Clock` and random-selection abstraction so cutoff behavior and ties are deterministic in tests.

### 12.4 Validation

Use Bean Validation for shape constraints and domain validation for cross-field/state rules. The backend MUST independently repeat all client checks. Startup validation rejects impossible configuration. API input limits should cap array size at five and reject unknown dates/duplicate JSON entries after normalization.

### 12.5 OpenAPI and health

- Publish OpenAPI for all REST DTOs and problem responses. The REST interfaces in `resource` are the authoritative source of endpoint-level OpenAPI metadata; contract tests MUST verify that annotations inherited through `resource.impl` produce the expected OpenAPI document.
- Define one OpenAPI cookie security scheme for the Quarkus form-authentication cookie. Public operations declare no authentication requirement; protected operations document the cookie scheme and `X-CSRF-TOKEN` header where relevant. `/j_security_check` is a Quarkus framework endpoint and is documented separately because it is not declared by a resource interface.
- Provide liveness and readiness; readiness verifies database connectivity and successful Liquibase state.
- Scheduler failure is logged/observed but MUST NOT leak details to clients.

## 13. Flutter client specification

### 13.1 Shared architecture

Use one Flutter codebase with platform adapters. A recommended structure is feature-first with immutable DTO/domain models, repository/service interfaces, and a predictable state-management package (for example Riverpod). Use `go_router` or equivalent declarative routing.

Routes:

```text
/assignments   initial authenticated route
/bids
/help
/login
/activate/code
/activate/password
```

Quarkus MUST serve `index.html` as an SPA fallback for client routes while excluding `/api/*`, health, OpenAPI, and real static assets.

### 13.2 Authentication UX

- On startup, the client checks the current session through an authenticated endpoint. A valid session opens the intended route without showing login; a `401` opens `/login`.
- `/login` initially shows one email field. After `POST /api/auth/start`, it shows either the password screen or navigates to `/activate/code` according to `nextStep`.
- The password screen displays the selected email and a password field. Successful login opens **Seat assignments**. It does not offer password reset or password change.
- The code screen displays a six-digit numeric input, code-expiry guidance, and **Resend code**. Resend remains disabled until `resendAvailableAt`; `429` feedback uses `Retry-After`. Leading zeroes are preserved.
- Successful code verification navigates to `/activate/password`. This screen contains password and password-confirmation fields and explains the 15-character minimum, support for long passphrases/spaces/Unicode, absence of composition rules, and rejection of common or compromised passwords.
- After successful password creation, the client automatically posts the email and password to `/j_security_check`, discards the plaintext password immediately after the response, and opens **Seat assignments** without another user action.
- Returning users normally remain authenticated across browser/app restarts. The PWA relies on Quarkus's HttpOnly form-authentication cookie. Android restores the same cookie from platform-secure storage.
- Before the first state-changing request and whenever the CSRF cookie is missing, a centralized HTTP client calls `GET /api/auth/csrf`, reads the separate JavaScript/client-readable CSRF cookie, and echoes it in `X-CSRF-TOKEN`. Both platforms send cookies on protected requests.
- A `401` clears local authentication state/cookies and returns to login while preserving only a safe intended route.
- Logout is available from the application menu, calls the backend logout endpoint, clears the cookie jar, and returns to `/login`.
- Authentication errors avoid confirming unnecessary account details. Passwords, codes, and tokens are never placed in URLs, persisted as ordinary application preferences, or sent to analytics.
- Flutter web HTTP requests MUST opt into browser credentials so the authentication and CSRF cookies are sent. Application code can read only the CSRF cookie, never the HttpOnly authentication cookie.

### 13.3 Navigation

- Primary views are **Seat assignments** and **Place bids**.
- Desktop/wide PWA uses two labeled tabs or navigation destinations.
- Mobile uses a two-item tab bar/segmented navigation and MAY allow horizontal swipe; swipe is never the only mechanism.
- Compact layouts use a hamburger menu. Wider layouts use an equivalent conventional menu.
- The menu contains **Help** and conditionally **Get the Android app**.

### 13.4 Seat assignments view

This is the initial view. It shows exactly five collapsed cards for the latest completed round, sized to fit together on a typical viewport where practical.

Each card displays localized weekday and `dd/MM` date (no year) and has these states:

- neutral: current employee did not bid;
- green: bid and assigned;
- red: bid and not assigned;
- a stronger shade for today when today is among displayed dates;
- a visible accent border and/or **Today** label when today's state is neutral.

Between cutoff and the target week, none may be today; no artificial highlight is applied. Colors MUST have text/icon equivalents and accessible contrast.

Expanding a card displays:

- `assignedCount of seatCapacity seats assigned`;
- participants by final rank;
- first and last name (fallback as specified by API);
- token amount;
- a clear boundary after the last assigned bidder;
- explicit assigned/not-assigned icon/text;
- a highlight for the current employee.

Collapsed/expanded state is local UI state. Loading, empty/bootstrap, processing, authentication, and retryable error states require dedicated UI.

### 13.5 Place bids view

The view displays:

- all five next-round weekdays and `dd/MM` dates;
- authoritative starting balance, current bid total, and amount still available to bid;
- numeric token input with increment/decrement controls;
- one auto-distribution selector per day;
- **Auto-distribute** and **Save bids** actions;
- exact cutoff date/time, `Europe/Berlin` (or configured zone), and a periodically refreshed relative countdown;
- Android-only reminder icon.

Draft behavior:

- Existing saved bids load into fields.
- Edits remain local until **Save bids**; saving replaces all bids atomically.
- Unsaved changes trigger route/back confirmation where appropriate.
- Inputs accept whole numbers only, never below zero.
- Minus is disabled at zero; plus is disabled when no tokens remain.
- Manual typing may temporarily exceed budget, but fields and summary show an error and saving is disabled.
- Zero counts as empty and remains eligible for auto-distribution.
- A positive field disables its auto-distribution selector.
- The UI explains that only successful bids are deducted and that all remaining tokens, including unsuccessful bids, are still subject to the carry-over cap.

Auto-distribution:

1. Select one or more eligible zero-value days.
2. Compute `share = remainingTokens ~/ selectedCount`.
3. Add `share` to every selected day.
4. Leave `remainingTokens % selectedCount` unallocated.
5. Resulting values become ordinary editable values.
6. If `share == 0`, do not modify fields and explain that too few tokens remain.

On `409`, preserve the draft, refresh context, and explain whether cutoff or a concurrent/stale round caused failure. Never silently apply a draft to another round.

### 13.6 Help content

Help MUST explain:

- weekly grants, balances, spending, and capped carry-over;
- placing, changing, saving, and auto-distributing bids;
- cutoff and privacy before cutoff;
- allocation ranking and random boundary ties;
- successful bids being charged, unsuccessful bids remaining unspent, and the carry-over cap;
- first-time email verification, password creation, persistent form-cookie login, inactivity expiry, and logout;
- assignment colors, today indicator, participant order, and capacity boundary;
- surplus/unassigned seats being outside application control;
- Android reminders where relevant.

### 13.7 Android app download promotion

When the PWA detects an Android browser, it MAY show **Get the Android app** in the menu and a non-intrusive footer when space permits. It MUST not displace core controls. Hide it in the native app. Detection is progressive enhancement, not a business/security rule, and the target SHOULD be a managed distribution/store page rather than a raw APK.

### 13.8 Accessibility and localization

- Support keyboard navigation, screen readers, scalable text, touch targets, focus indicators, and WCAG AA contrast.
- Never communicate status by color alone.
- Dates are presented as agreed (`dd/MM`) while weekday labels and prose are localizable.
- Store/transport no locale-specific numeric formats; token values are integers.

### 13.9 PWA behavior

- Provide a valid manifest, icons, service worker, and installability metadata.
- Cache the application shell, not authenticated API responses containing personal data unless a deliberate secure strategy is implemented.
- Core bidding requires connectivity; offline changes MUST NOT appear saved. Show offline state clearly.
- Updates SHOULD prompt reload when a new app version is available and avoid mixing incompatible client/API versions.

## 14. Native Android reminder

Only the native Android app shows the reminder icon in version 1. It opens a local configuration UI for:

- enabled/disabled;
- one or more weekdays;
- local device time.

The reminder is device-local, not synchronized to the backend. It uses the current device time zone and creates an Android notification that deep-links to **Place bids**. Prefer an inexact scheduled notification because this is a reminder, not an alarm clock; request notification permission when required and avoid exact-alarm permission unless later justified. Reschedule after reboot/time-zone changes if the chosen Flutter plugin requires it. The PWA hides this control.

## 15. Deployment

### 15.1 Build and image

1. Build Flutter web in release mode.
2. Copy its output into Quarkus `META-INF/resources` during the build.
3. Build the Quarkus container image with Jib using a Java 25-compatible runtime base image. The application MUST run on Java 25 inside the resulting container.
4. Run one application container containing REST API, PWA assets, scheduler, ORM, and Liquibase.
5. Run PostgreSQL separately (container or managed service).

No Node web server, Qute rendering, or separate PWA container is required. The Android APK/AAB is distributed separately.

### 15.2 Runtime topology

```text
Browser/PWA ─┐
             ├─ HTTPS ─> Quarkus application container ─> PostgreSQL
Android app ─┘              │
                            └─ SMTP/TLS ─> company mail server
```

Use TLS at a reverse proxy/platform boundary. The PWA calls same-origin relative URLs, so the application hostname is not compiled into it. Forwarded headers, secure-cookie handling, allowed-origin validation, and SPA fallback must be configured safely. The SMTP server must be reachable from the application container; mobile clients never connect to SMTP directly.

### 15.3 Future multi-instance deployment

If Kubernetes is introduced later, the agreed direction is a StatefulSet: all pods serve HTTP, but only ordinal zero (hostname ending `-0`) enables scheduled jobs through deployment configuration/hostname logic. Do not add distributed scheduler coordination in version 1. Database transaction and uniqueness rules remain mandatory for concurrent HTTP traffic.

## 16. Quarkus YAML configuration contract

Backend configuration MUST use YAML; an `application.properties` file MUST NOT be used. Shared configuration belongs in `src/main/resources/application.yaml`. Profile-specific values MUST be placed in separate profile-aware files such as `application-dev.yaml`, `application-test.yaml`, and `application-prod.yaml`. Profile-prefixed keys such as `%dev`, `%test`, and `%prod` are prohibited in every YAML file. Each profile file contains ordinary unprefixed keys and overrides the shared configuration through Quarkus's profile-aware file loading.

Names may be adjusted consistently, but all values must be externally configurable. Environment variables continue to provide deployment-specific values and secrets through Quarkus expression expansion:

```yaml
seat-bidding:
  tokens-per-round: 50
  carry-over-cap: 20
  seat-capacity: ${SEAT_CAPACITY}
  scheduler:
    cron: "0 0 22 ? * FRI"
    time-zone: Europe/Berlin
    enabled: true
  lock-timeout: 5S
  authentication:
    activation:
      code-ttl: 15M
      maximum-attempts: 5
      resend-cooldown: 60S
      token-ttl: 15M
      code-pepper: ${ACTIVATION_CODE_PEPPER}
    password:
      minimum-length: 15
      maximum-length: 128
      blocklist-resource: password-blocklist.txt
      argon2-memory-kib: 19456
      argon2-iterations: 2
      argon2-parallelism: 1
    allowed-web-origins: ${AUTH_ALLOWED_WEB_ORIGINS}

quarkus:
  http:
    auth:
      session:
        encryption-key: ${AUTH_SESSION_ENCRYPTION_KEY}
      form:
        enabled: true
        login-page: ""
        landing-page: ""
        error-page: ""
        username-parameter: j_username
        password-parameter: j_password
        post-location: /j_security_check
        timeout: 30d
        new-cookie-interval: 24h
        cookie-name: seat_session
        cookie-path: /
        http-only-cookie: true
        cookie-same-site: strict
        cookie-max-age: 30d
  datasource:
    db-kind: postgresql
    jdbc:
      url: ${JDBC_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  liquibase:
    migrate-at-start: true
  mailer:
    from: ${MAIL_FROM}
    host: ${SMTP_HOST}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    start-tls: REQUIRED
  rest-csrf:
    enabled: true
    create-token-path: /api/auth/csrf
    token-header-name: X-CSRF-TOKEN
    cookie-name: csrf-token
    cookie-path: /
    cookie-max-age: 2h
    cookie-force-secure: true
    cookie-http-only: false
    token-size: 32
    token-signature-key: ${CSRF_TOKEN_SIGNATURE_KEY}
    verify-token: true
    require-form-url-encoded: false
  otel:
    sdk:
      disabled: true
  log:
    file:
      enabled: ${LOG_TO_FILE:false}
      path: /logs/application.log
      format: '%d{yyyy-MM-dd HH:mm:ss} %-5p [%c] (%t) %s%e%n'
      rotation:
        rotate-on-boot: false
```

OpenTelemetry MUST be configured as the common observability mechanism for logging, metrics, and tracing. Its SDK MUST be disabled by default with `quarkus.otel.sdk.disabled: true`, as shown above. Profile-specific files or deployment environment variables MAY enable it and configure OTLP exporters/endpoints, protocols, resource attributes, service identity, sampling, and signal-specific options. When enabled, logs MUST carry trace and span correlation identifiers where a tracing context exists. Enabling or disabling telemetry MUST require configuration only, not a code change.

File logging MUST use the configuration shown above. It is disabled unless `LOG_TO_FILE=true`, writes to `/logs/application.log`, uses the specified format, and does not rotate merely because the application starts. The container deployment MUST mount a writable volume at `/logs` whenever file logging is enabled. Console logging remains available for normal container operation.

Authentication durations, limits, password blocklist, Argon2id parameters, allowed web origins, Quarkus form-authentication settings, and mail settings MUST be validated at startup. Secrets such as the activation-code pepper, form-session encryption key, CSRF signature key, SMTP password, and password hashes must never be exposed through the public configuration endpoint. `application-test.yaml` MUST use Quarkus `MockMailbox` and MUST NOT contact a real SMTP server. The Android download URL may be exposed through the public configuration endpoint.

## 17. Testing and verification

The backend test suite MUST use a real PostgreSQL instance supplied by Testcontainers for every test that exercises Panache entities or repositories, Liquibase, SQL constraints, transactions, pessimistic locking, or scheduler persistence. This requirement also applies when such tests are organized or named as unit tests in the project. H2 and other in-memory database substitutes MUST NOT be used. Pure domain-logic tests that have no persistence dependency SHOULD remain ordinary database-free unit tests.

All backend tests and static-analysis/build jobs MUST execute with JDK 25 so local, CI, and production Java versions remain aligned.

### 17.1 Backend unit tests

- Balance/grant/carry-over calculations, including successful-bid deductions, cap, and expiry.
- Successful bids are charged in full; unsuccessful bids create no debit.
- Capacity zero is rejected at startup; fewer/equal/more bidders than capacity.
- Ties entirely above/below boundary and ties crossing it.
- Random selector fairness contract and deterministic injected test implementation.
- Friday cutoff, DST transitions, and target-date calculation.
- Zero normalization and complete bid replacement validation.
- Email normalization and lookup behavior for known, unknown, activated, and unactivated employees.
- Six-digit code generation including leading zeroes, keyed digest verification, expiry, failed-attempt exhaustion, resend invalidation, and cooldown calculations.
- Password-policy length boundaries, Unicode handling, absence of composition rules, matching confirmation, and common/compromised-password blocklist.
- Custom Argon2id identity-provider success/failure, PHC parsing, parameter-upgrade detection, and generic invalid-credential behavior.

### 17.2 Integration tests with PostgreSQL

- Liquibase from an empty database and constraint enforcement.
- Pessimistic serialization of simultaneous submissions for the same participant.
- Independent employees can update concurrently.
- Cutoff racing with bid update.
- Scheduler rollback/retry and no duplicate assignments, bid-spend ledger entries, or successor round.
- Round configuration snapshots remain unchanged after runtime configuration changes.
- Ledger reconciles with participation balances.
- Open bids cannot be queried through published endpoints.
- Activation start/resume/resend against a captured test mailbox; no test may contact a real SMTP server.
- Concurrent activation/resend/password-creation requests cannot reuse a code or create conflicting password state.
- Codes and activation authorizations are stored only as digests/hashes and never appear in captured logs or problem responses.
- Argon2id password creation and verification, generic invalid-credential behavior, and configurable rehash detection.
- `/j_security_check` delegates credential verification to the custom Quarkus identity provider and sets the exact encrypted cookie attributes for both PWA and Android.
- Quarkus form-cookie inactivity expiration, 24-hour renewal interval, persistence across restarts, malformed/tampered-cookie rejection, logout cookie removal, and global invalidation after encryption-key rotation.
- `quarkus-rest-csrf` creates and signs the CSRF cookie and rejects missing, mismatched, malformed, and unsigned tokens on Jakarta REST JSON state-changing requests for both platforms; separate tests verify allowed-origin enforcement on `/j_security_check`.
- Authentication `401`/`403`/`429` behavior and `Retry-After` where applicable.

Use Quarkus tests plus a shared Testcontainers PostgreSQL setup. The container SHOULD be reused across test classes or the test suite where supported to keep execution time reasonable, while database state MUST be isolated or reset between tests. The full Liquibase changelog MUST initialize the test database so tests exercise the production schema.

### 17.3 API contract tests

- OpenAPI matches implemented DTOs.
- Authentication start, form login, resend, verification, password creation, CSRF, logout, and cookie contracts match the documented OpenAPI operations and cookie security scheme.
- Problem details and all status mappings.
- Stale `roundId`, dates outside round, duplicate dates, negative/fractional/overspent bids.
- No employee identifier is accepted or honored.

### 17.4 Flutter tests

- Widget/golden tests for all assignment colors, today marker, capacity boundary, and responsive layouts.
- Bid balance, spinner states, carry-over explanation, integer division/remainder, zero behavior, and disabled save.
- Navigation by tap, keyboard, and swipe; unsaved-change handling.
- Email-first login branching, password login, pending-code resume, resend cooldown, code verification, password requirements/confirmation, automatic form login after password creation, persistent-cookie restoration, inactivity expiry, CSRF recovery, logout, and generic authentication error states.
- Platform tests ensure reminders are visible only on Android and Android promotion only in eligible PWA contexts.
- End-to-end flow: login, load bids, edit/save, cutoff simulation, published result.

### 17.5 Acceptance scenarios

1. With a starting balance of 70 and a successful 20-token bid, an employee has 50 remaining; only 20 carries out when the cap is 20.
2. With a starting balance of 70 and an unsuccessful 20-token bid, no tokens are deducted; the remaining balance is 70 and only 20 carries out when the cap is 20.
3. With successful bids of 20 and 8 plus an unsuccessful bid of 15, exactly 28 tokens are deducted.
4. At capacity 2 with bids `20, 10, 10`, the 20-token bidder wins and exactly one 10-token bidder is randomly selected; only both winners are charged and the persisted result never changes.
5. At capacity 4 with three positive bidders, all three win, all three bids are charged, and one seat remains unassigned.
6. One minute before cutoff, bids can be replaced; at/after cutoff, replacement returns `409` even before scheduler completion.
7. Two simultaneous updates for the same employee serialize under a pessimistic lock and cannot reserve more than the starting balance.
8. A provisioned employee with no password starts activation, receives one captured six-digit email code, verifies it, creates a compliant password, is automatically form-authenticated, and enters the application without another user action.
9. Returning with a valid pending code resumes at code entry; resend invalidates the old code, observes cooldown, and only the new code succeeds.
10. A successful login on either platform survives an app restart through the securely persisted encrypted cookie, uses Quarkus's 30-day inactivity expiry, and renews no more than once per 24 hours.
11. Logout removes the current client's cookie; an expired, malformed, tampered, or cookie encrypted with an obsolete key returns `401`.
12. PWA and Android bid replacement without a valid Quarkus REST CSRF cookie/header pair is rejected; the same request succeeds with the signed matching token.
13. Refreshing `/bids` directly serves Flutter `index.html`; `/api/unknown` remains an API `404`, not SPA HTML.

## 18. Observability and operations

- OpenTelemetry is the required instrumentation path for logs, metrics, and traces, while the SDK remains disabled by default. Production operators explicitly enable and configure export through profile YAML and/or environment variables.
- Structured logs include round ID, employee internal ID where necessary, operation, outcome, and trace ID; never passwords, activation codes/authorizations, authentication cookies, password hashes, encryption/signature keys, SMTP credentials, or unnecessary bid details.
- OpenTelemetry metrics SHOULD cover bid-save success/failure, scheduler duration/status, bidders per date, lock conflicts, and open/completed round counts.
- Authentication metrics SHOULD cover aggregate start/login/activation success and failure, rate limiting, email delivery failure, and form-cookie authentication failure without using email addresses or other high-cardinality personal identifiers as metric labels.
- OpenTelemetry traces SHOULD cover inbound REST requests, database operations, and scheduled round processing, with custom spans around allocation where they materially improve diagnosis.
- Alert when a due round is not completed, no open successor exists, Liquibase fails, or ledger reconciliation fails.
- Document a manual operational retry that calls the same idempotent processing service; do not repair results with ad hoc duplicate inserts.
- Back up PostgreSQL. Published history and token ledger are business/audit data.

## 19. Implementation order

1. Create Quarkus/Flutter projects and shared build packaging.
2. Add Liquibase schema, Panache entities/repositories, configuration validation, and bootstrap.
3. Implement employee provisioning schema, SMTP activation, the custom Argon2id Quarkus identity provider, built-in form authentication, Quarkus REST CSRF, and request authorization.
4. Implement bidding context/replacement with pessimistic locking.
5. Implement allocation, ledger, scheduler, and idempotency.
6. Implement published assignments and problem/OpenAPI contracts.
7. Build Flutter authentication, assignments, bidding, help, and responsive navigation.
8. Add Android reminder/platform behavior and PWA promotion.
9. Complete integration, concurrency, accessibility, container, and end-to-end verification.

## 20. Future extensions

### 20.1 Half-day attendance and seat sharing

Future bidding may allow `FULL_DAY`, `MORNING_ONLY`, or `AFTERNOON_ONLY` per bid. Attendance period does not reduce the bid or token cost. Complementary morning/afternoon employees may share one physical seat, so assigned employee count may exceed seat capacity while physical occupancy does not. Both employees pay their full bids and participate normally. Published assignments must make attendance periods and sharing clear. The precise allocation algorithm, tie behavior, schema, and controls are intentionally deferred; version 1 treats every bid and assignment as full-day.

### 20.2 Other deferred enhancements

- Configurable cadences beyond weekly and corresponding UI semantics.
- Public holidays, exceptional closures, and configurable workdays.
- Per-day capacity, multiple offices, physical seat numbers, and office attendance management.
- Administration UI for employees and settings.
- Password change, forgotten-password recovery, operator-assisted reset UI, and self-service session management.
- Server-driven PWA push reminders and synchronized reminder preferences.
- Kubernetes StatefulSet deployment with ordinal-zero scheduler activation.
- Historical browsing, analytics, exports, and privacy/retention controls beyond the latest published round.
- Localization beyond the initial language and date convention.

## 21. Definition of done

Version 1 is complete when all mandatory rules in this document are implemented, the backend builds, tests, and runs on Java 25, the initial Liquibase changelog provisions an empty PostgreSQL database, concurrency and scheduler idempotency tests pass against PostgreSQL, SMTP-backed first-time activation works for provisioned employees, the Argon2id identity provider and Quarkus form authentication/REST CSRF satisfy the security, cookie-renewal, and inactivity-expiry tests, the PWA is served from the Java 25-compatible Jib-built Quarkus image, bid privacy is verified, allocation and ledger acceptance scenarios pass, and the responsive PWA supports the complete core workflow without the optional Android app.
