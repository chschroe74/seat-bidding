# Office Seat Bidding

Office Seat Bidding is a Java 25 Quarkus application that serves its REST API and compiled Flutter PWA from one process. PostgreSQL is the only supported database. Employees are provisioned by an operator, activate by email, and sign in through Quarkus form authentication with an encrypted stateless cookie.

## Prerequisites and local development

- JDK 25 and Maven 3.9+ (`mvn25` may be used where that command selects JDK 25)
- A PostgreSQL database for local development
- Docker or Podman for PostgreSQL Testcontainers during backend tests
- Flutter stable; this workstation uses `C:\Tools\flutter\bin\flutter.bat`

The dev profile contains local-only authentication secrets and uses Quarkus's captured mock mailbox. PostgreSQL Dev Services are disabled: set `DB_HOST`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` for your own database before starting the backend. `DB_HOST` is a hostname and may include a non-default port, for example `localhost:5433`; PostgreSQL's default port is used when it is omitted.

Start the backend against that configured database with:

```powershell
mvn25 quarkus:dev
```

To compile and serve the PWA from Quarkus in the same run, use:

```powershell
mvn25 -Pfrontend "-Dflutter.executable=C:\Tools\flutter\bin\flutter.bat" quarkus:dev
```

Mock messages are never sent to a real SMTP server. In the dev profile, Quarkus logs the complete mock message at INFO so the activation code is visible in the console. Mail-body logging remains disabled in test and production because activation codes must not enter those logs. To exercise real local SMTP instead, override the dev mailer settings with a local capture server such as Mailpit; never point development or tests at a real company mailbox.

Run all backend tests with:

```powershell
mvn25 test
```

Every persistence-related test starts PostgreSQL through Testcontainers. There is no H2 configuration or dependency.

## Frontend and production builds

Run Flutter directly:

```powershell
cd client
C:\Tools\flutter\bin\flutter.bat pub get
C:\Tools\flutter\bin\flutter.bat analyze
C:\Tools\flutter\bin\flutter.bat test
C:\Tools\flutter\bin\flutter.bat build web --release
```

Or build, test, and copy the release web output into the Quarkus application:

```powershell
mvn25 -Pfrontend "-Dflutter.executable=C:\Tools\flutter\bin\flutter.bat" verify
```

The runnable application is `target/quarkus-app/quarkus-run.jar`. To build the Java 25 Jib image, add `-Dquarkus.container-image.build=true`. The image contains Quarkus and the PWA; PostgreSQL and SMTP remain separate services.

Android builds must provide the absolute HTTPS API base ending in `/api`, for example `--dart-define=API_BASE_URL=https://seats.example.com/api`. Android uses the same cookie protocol as the PWA; its managed cookie jar persists data only through `flutter_secure_storage`. The web build relies on the browser and cannot read the HttpOnly authentication cookie.

## Production configuration

The packaged application uses `application-prod.yaml` plus shared `application.yaml`. Supply at least:

- Database: `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- SMTP/TLS: `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`
- Authentication secrets: `ACTIVATION_CODE_PEPPER`, `AUTH_SESSION_ENCRYPTION_KEY`, and `CSRF_TOKEN_SIGNATURE_KEY` (independent random values of at least 32 characters; use high entropy)
- Browser policy: `AUTH_ALLOWED_WEB_ORIGINS` as exact HTTPS origins
- Optional tuning: activation limits, form-cookie timeout/renewal/max-age, rate limits, Argon2id parameters, and `PASSWORD_BLOCKLIST_RESOURCE`
- Round defaults: `TOKENS_PER_ROUND` defaults to `60`; `CARRY_OVER_CAP` defaults to `24`
- Reverse proxy: set `PROXY_ADDRESS_FORWARDING=true` only when forwarded headers come from a trusted proxy configuration

TLS terminates at the reverse proxy/platform boundary. Keep the application private behind that boundary, mount `/logs` when `LOG_TO_FILE=true`, and rotate SMTP and authentication secrets through the deployment secret store. Rotating the CSRF signature key invalidates current CSRF proofs, rotating the activation pepper invalidates pending activation codes, and rotating the form-session encryption key invalidates all authentication cookies. With signing enabled, Quarkus returns the random header token in `X-CSRF-TOKEN` from `GET /api/auth/csrf`; the readable cookie contains its HMAC signature and is deliberately a different value.

The bundled `password-blocklist.txt` is a versioned operational asset. Update it through normal source review from a trusted local compromise/common-password source; candidate passwords must never be sent to an external service.

## Employee provisioning and account operations

Email is stored trimmed and lowercase. New employees have no password until activation:

```sql
insert into employee (email, first_name, last_name, password_hash, password_set_at, enabled)
values (lower(btrim('employee@example.com')), 'First', 'Last', null, null, true);
```

Administrator status is deliberately maintained only through controlled database access. Grant it to an already provisioned employee with:

```sql
update employee set is_admin = true, updated_at = now()
where email = lower(btrim('employee@example.com'));
```

Removing the flag blocks subsequent administrator API calls even when the browser still has an older authentication cookie. There are no role-management endpoints or controls in the application.

Disable an employee while retaining bidding history:

```sql
update employee set enabled = false, updated_at = now() where email = lower(btrim('employee@example.com'));
```

Disabling prevents new logins and application services reject the employee when resolving current-user data. Because form cookies are stateless, an already issued cookie cannot be individually revoked; rotate `AUTH_SESSION_ENCRYPTION_KEY` if immediate global invalidation is required.

To require activation again, clear both password fields and delete activation state in one controlled transaction. Re-enabling is a separate operator decision. Existing stateless cookies remain valid until idle expiry unless the global encryption key is rotated:

```sql
begin;
update employee set password_hash = null, password_set_at = null, updated_at = now()
where email = lower(btrim('employee@example.com'));
delete from account_activation
where employee_id = (select id from employee where email = lower(btrim('employee@example.com')));
commit;
```

The deployed version 1.2 migrations `001-initial-schema`, `002-global-fairness-allocation`, and `003-administrator-seat-reservations` remain immutable. Migration `004-half-day-allocation-units` adds attendance periods and normalized allocation units in staged changesets. It backfills existing bids as full-day and historical results as one-member single units without recalculating completed rounds. Historical assignments retain their published order, audit data, fingerprints, ledger entries, and algorithm version.

The authenticated bidding context and successful bid-replacement response include the round's physical capacity plus each date's reserved count, assignable capacity, and optional public description. These values are read-only context and do not participate in token validation, auto-distribution, or charging.

### Allocation audit encoding v4

Allocation fingerprints use a versioned canonical UTF-8 line encoding with LF separators. Null reservation IDs and boundary groups use `-`. Dates are chronological; units use canonical `EMPLOYEE:<id>` or unordered `PAIR:<lowerId>:<higherId>` fairness identities; members use stable bid identifiers. The `v4` input records physical/reserved/assignable capacity, canonical pairing audit, every constructed unit, score classification, and every member's tokens and attendance period. Public reservation descriptions are excluded. The selected solution records every persisted unit and member outcome, including score/unit rank and individual display rank. Stored fingerprints are lowercase SHA-256 hex digests. Completed rounds using older algorithm versions are never reinterpreted.

Pairing randomness is recorded only in `pairing_audit`; selection among complete globally equivalent capacity solutions is recorded separately in `capacity_selection_value`. Changing the encoding or allocation semantics requires a new algorithm version. Audit values are diagnostic only; persisted allocation-unit and member results are the accounting and read source.

A due bidding round is still processed by `RoundProcessingService.processDueRound()`. Operational retries must call that same idempotent service or restart before the next configured trigger; never repair assignments or ledger rows with ad-hoc inserts.

### Testing seat assignments locally

To exercise assignment processing without waiting for Friday, submit the desired bids first. In the local development database only, mark the open round as due:

```sql
update bidding_round
set cutoff_at = now() - interval '1 second'
where status = 'OPEN';
```

While `mvn25 quarkus:dev` is running, open the Quarkus Dev UI at `http://localhost:8080/q/dev-ui/`, select **Scheduler**, find `RoundScheduler.run()`, and choose **Execute**. This invokes the same idempotent processing path as the Friday schedule, publishes the assignments, and opens the next round. Refresh the Assignments screen afterward. Never use the cutoff update against production data.