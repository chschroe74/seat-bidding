# Office Seat Bidding Application — Implementation Specification

**Status:** Approved implementation specification  
**Target release:** Version 1.3  
**Primary client:** Flutter Progressive Web App (PWA)  
**Optional client:** Native Flutter Android application  
**Backend:** Java 25, Quarkus, PostgreSQL, Hibernate ORM with Panache, Liquibase, application-managed authentication, Quarkus Scheduler, standards-based Web Push, Jib

## 1. Purpose

The application distributes a limited number of office seats fairly among employees. Employees receive tokens for each bidding round and privately bid for individual weekdays, optionally indicating morning-only or afternoon-only attendance. Administrators may reserve physical seats before allocation, and employees see those reservations while deciding their bids. Opted-in employees may register one or more web-capable devices and receive daily reminders until they place a positive bid or suppress reminders for the current round. At cutoff, the backend subtracts reservations from capacity, forms complementary half-day sharing pairs, ranks full-day employees, unpaired half-day employees, and half-day pairs as allocation units, resolves capacity-boundary ties globally across the round using fairness-aware allocation, publishes assignments, deducts only successful individual bids, and opens the next round.

This document is the authoritative version 1.3 implementation contract. Requirements marked **MUST**, **SHOULD**, and **MAY** have their conventional meanings.

## 2. Scope

### 2.1 Version 1.3 goals

- Support one office and one shared seat capacity for every weekday.
- Run one weekly round covering Monday through Friday.
- Let authenticated, pre-provisioned employees view published assignments and replace their bids until cutoff.
- Show each open-round date's reserved count, assignable capacity, and public reservation description as read-only bidding information.
- Let employees independently choose full-day, morning-only, or afternoon-only attendance for every positive bid.
- Pair complementary half-day bidders so two employees may share one physical seat while retaining individual bids and token charges.
- Keep all employees' open-round bids private.
- Maintain auditable token balances and round results.
- Let manually designated administrators create and delete dated seat reservations through a desktop-only PWA section before the affected assignments are processed.
- Let employees manage synchronized bid-reminder preferences and registered Web Push devices from a settings page available on desktop and mobile.
- Send one server-driven reminder per eligible weekday to every active registered device until the employee places a positive bid or suppresses reminders for that round.
- Serve the compiled Flutter PWA and REST API from one Quarkus application image.
- Optionally distribute a native Android client using the same API.

### 2.2 Explicitly out of scope for version 1.3

- Administrative functions other than dated seat reservations, including employee provisioning and administrator-role management.
- Public-holiday and office-closure handling.
- Configurable capacities per day, multiple offices, named desks, or reservation workflows beyond reducing the bidding capacity for a date.
- Time ranges other than the fixed morning/afternoon periods, partial-day pricing, and administrator-created pairings.
- Non-weekly or overlapping round cadences in the UI.
- Kubernetes or multiple active scheduler instances.
- Guaranteed notification delivery, SMS reminders, and guaranteed offline bidding.
- Managing unassigned seats or employees who attend without an assignment.

## 3. Terminology and time model

- **Round:** One immutable business period created for one scheduled allocation. It has an open bidding window and five target dates. The model MUST NOT rely only on a calendar week number.
- **Target date:** One Monday–Friday date covered by a round.
- **Open round:** The only round whose bids may be edited.
- **Published round:** The latest completed round shown in **Seat assignments**. Immediately after Friday cutoff this normally covers the following Monday–Friday, even though that week has not begun.
- **Balance:** Tokens available to an employee for the open round.
- **Bid:** A positive integer token amount for one round date. Zero means no bid and need not be persisted.
- **Attendance period:** `FULL_DAY`, `MORNING_ONLY`, or `AFTERNOON_ONLY` on a positive bid. It affects pairing and display, not that bid's token price.
- **Allocation unit:** One indivisible capacity competitor for one target date: one full-day bidder, one unpaired half-day bidder, or one complementary morning/afternoon pair. Every successful unit consumes one physical seat.
- **Half-day pair:** One morning-only bid and one afternoon-only bid combined into a single allocation unit. Its ranking score is the sum of both individual bids, but each employee retains their own bid and accounting result.
- **Fairness identity:** The stable identity used to count round-level boundary-tie wins. A single unit uses its employee ID; a pair uses the canonical unordered combination of both employee IDs.
- **Carry-over:** The capped portion of the balance remaining after successful bids are deducted. Tokens committed to unsuccessful bids remain unspent.
- **Seat reservation:** One administrator-created record that removes a positive number of physical seats from bidding for one future Monday–Friday date and may include a public description.
- **Business time zone:** The single configured IANA zone used for round calendars, allocation cutoff scheduling, reminder scheduling, weekday interpretation, and user-facing business times.
- **Push subscription / registered device:** One browser-generated Web Push endpoint and encryption-key set bound to the authenticated employee and one browser installation. One employee may have multiple active subscriptions.
- **Bid reminder:** One generic Web Push notification generated for an eligible employee by the weekday reminder schedule and delivered independently to every active registered device.
- **Round reminder suppression:** An irreversible employee choice to stop further bid reminders for one open bidding round. It expires by scope when the successor round becomes current and does not disable future rounds.
- **Physical seat capacity:** The round's configured `seatCapacity` snapshot before reservations.
- **Assignable seat capacity:** `physicalSeatCapacity - reservedSeatCount` for a target date. It may be zero even though configured physical capacity must be positive.
- **Cutoff:** An instant computed by the backend from the allocation Quartz cron expression and shared business time zone.

All persisted instants MUST use UTC (`timestamptz`/`Instant`). Target and reminder business dates use `date`/`LocalDate`. Both scheduled methods interpret their cron expressions in the shared business time zone, including daylight-saving transitions. The JVM, container, database, and client-device default zones are never authoritative for business rules.

## 4. Business configuration

The following settings come from Quarkus application configuration and are validated at startup:

| Setting | Default | Constraint |
|---|---:|---|
| Tokens granted per round | 60 | integer `>= 0` |
| Carry-over cap | 24 | integer `>= 0` |
| Seat capacity | deployment-specific | integer `>= 1`, same for all dates |
| Business time zone | `Europe/Berlin` | valid IANA zone, shared by both schedules |
| Allocation scheduler cron | `0 0 22 ? * FRI` | valid Quarkus/Quartz cron |
| Allocation scheduler enabled | `true` | boolean |
| Bid-reminder scheduler cron | `0 0 10 ? * MON-FRI` | valid Quarkus/Quartz cron representing one fixed local-time trigger on every Monday–Friday |
| Bid-reminder scheduler enabled | `true` | boolean |
| Lock timeout | deployment-specific, recommended 5 seconds | positive duration |

Every round MUST snapshot the first three business values, cutoff instant, shared business time zone, and schedule-derived target dates. Changing round configuration MUST affect only subsequently created rounds, not an already open or completed round. Reminder weekday evaluation for an open round uses that round's snapshotted zone; deployments MUST change the shared business time zone only at a round boundary so the scheduler trigger and open-round calendar cannot disagree.

Reservations do not change the snapshotted physical capacity. The reservation applicable to a target date is read and locked during round processing, and its count determines that date's assignable capacity.

## 5. Core business rules

### 5.1 Round and token rules

1. The current release has exactly one open round and it contains five consecutive target dates, Monday through Friday. Weekends are excluded; public holidays are treated as ordinary weekdays.
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
- Every positive bid stores one attendance period: `FULL_DAY`, `MORNING_ONLY`, or `AFTERNOON_ONLY`.
- `FULL_DAY` is the independent default for every date. During the version 1.3 rollout, an omitted period on a positive bid is interpreted as `FULL_DAY`; version 1.3 clients always send it explicitly.
- Bids may be replaced any number of times while the round is `OPEN` and before its cutoff instant.
- Zero is equivalent to no bid and MUST be removed or omitted in persistence. A no-bid date has no persisted period and starts as `FULL_DAY` in a new draft.
- Negative, fractional, duplicate-date, out-of-round, weekend, or over-budget bids are invalid.
- Periods are independent across dates. Changing one MUST NOT change another date, token amount, reservation data, or auto-distribution selection.
- A successful half-day bid spends its full amount. There is no half-day discount or token splitting.
- The backend derives the employee from Quarkus `SecurityIdentity` established by the validated form-authentication cookie; a client-supplied employee identifier is forbidden.
- Other employees' open-round tokens and attendance periods MUST never be returned by any endpoint.

### 5.3 Administrative seat reservation rules

1. Only an authenticated employee whose current database record has `is_admin = true` may list, create, or delete reservations. No REST operation or UI control may grant or revoke administrator status.
2. A reservation has one target date, a positive integer `reservedSeatCount`, and an optional public description. The current release permits at most one reservation record per date; changing it means deleting and recreating it while mutations are still allowed.
3. The target date MUST be today or later in the shared business time zone, MUST be Monday through Friday, and MUST not belong to a round that is at/after cutoff, `PROCESSING`, `COMPLETED`, or `FAILED`. Public holidays remain ordinary weekdays.
4. If the date belongs to the current `OPEN` round, creation and deletion are allowed only strictly before its cutoff. If no round yet contains the future date, the reservation remains mutable until that date is included in an open round and its cutoff is reached.
5. The reserved count MUST NOT exceed the applicable physical capacity: use the containing open round's snapshotted capacity when present and otherwise the current configured capacity. A future capacity change that would place an existing reservation above capacity is invalid and MUST be rejected before the affected round is opened.
6. The description is optional, trimmed, plain text, and at most 500 Unicode code points. An empty normalized description is stored as null. It is visible to every authenticated user in the published assignment view, so the admin UI MUST state that it is public and it MUST never be interpreted as markup.
7. Deletion removes the reservation record. Once the mutation window closes, the reservation is immutable together with the resulting assignments; the current release has no override, update, cancellation, or post-processing adjustment.
8. Reservations consume no tokens, create no bid or seat-assignment record, do not identify an attendee, and never enter deterministic ranking, global fairness, or random selection. They reduce capacity before bid classification begins.

For each target date, `assignableSeatCapacity = physicalSeatCapacity - reservedSeatCount`, where a missing reservation means zero reserved seats. The backend MUST reject inconsistent data rather than silently clamp a negative result.

### 5.4 Half-day pairing and allocation-unit construction

For each target date, construct allocation units before capacity ranking:

1. Partition positive bids into full-day, morning-only, and afternoon-only sets.
2. Set `pairCount = min(morningCount, afternoonCount)`. If it is zero, create no pairs.
3. Every bid on the smaller side is selected for pairing. From a larger side, select its `pairCount` highest-token bids. If an exact-token tie crosses this selection boundary, select only the required number uniformly at random from that tied group. Higher bids cannot be displaced by lower bids.
4. Sort selected morning bids by tokens descending and selected afternoon bids by tokens ascending, then pair positionally. Opposite-order matching deliberately balances combined scores.
5. Equal-token ordering may permit several equivalent pair compositions. Randomly choose among complete compositions allowed by the opposite-order rule, after canonicalizing employees by stable ID so database and collection ordering create no bias.
6. Every selected complementary pair becomes one `HALF_DAY_PAIR` allocation unit with `scoreTokens = morningBid.tokens + afternoonBid.tokens`.
7. Every full-day bid and every excess/unselected half-day bid becomes a `SINGLE` allocation unit with `scoreTokens = bid.tokens`. An unmatched half-day bidder remains fully eligible and, if successful, occupies one physical seat alone.
8. A pair is indivisible: both members succeed or both fail. Ranking, global fairness, and final selection can never split it.
9. Pairing occurs only within one target date. An employee has at most one bid and therefore belongs to at most one unit on that date.
10. Persist selection, composition, member periods, score, and random audit values during processing. Reads MUST never reconstruct or redraw pairs.

Majority-side selection and pair-composition randomness operate only among exact-token-equivalent alternatives and precede capacity-boundary selection. A retry after rollback may choose different equivalent pairs; a completed round is immutable.

Fairness identity is canonical and orientation-independent. A single uses `EMPLOYEE:<employeeId>`; a pair uses `PAIR:<lowerEmployeeId>:<higherEmployeeId>`. The same two employees form the same fairness identity on another date even when their morning/afternoon roles reverse. Different partners form different identities. This intentionally measures weekly boundary fairness over allocation units rather than separately attributing a pair win to both employees.

### 5.5 Allocation rules

Allocation-unit scores remain authoritative for every target date:

1. Construct all allocation units under section 5.4 and group them by `scoreTokens` descending.
2. Calculate assignable capacity after reservations. If it is zero, every unit is unsuccessful. If unit count is at most capacity, every unit succeeds and surplus seats remain unassigned.
3. A score group entirely above the boundary consists of fixed winning units. A score group entirely below it consists of fixed losing units.
4. When an exact-score group crosses the boundary, that group is the date's **boundary tie** and the remaining capacity is its unresolved seat count. Only units in that group are eligible. A lower-score unit can never displace a fixed winner or boundary candidate.
5. Establish all fixed winners and fixed losers for all five target dates before resolving any boundary tie.
6. Collect all boundary units and unresolved seats into one round-level constrained problem. A fairness identity is eligible only on a date where its unit is in the boundary tie, can win at most one unit on that date, and may win on multiple dates.
7. Solve the problem using section 5.5.1's strict objectives. No date may be resolved independently, greedily, or in iteration order.
8. If complete allocations remain equivalent under every objective, select one randomly under section 5.5.2.
9. Persist one individual result for every positive bid, its unit membership, the unit classification and boundary membership, selected global outcome, stable display order, and round audit. Reads of a completed round invoke neither pairing nor optimisation.
10. Charge each member of every successful unit their full individual bid. Members of an unsuccessful unit spend zero. A pair's summed score is never an additional debit.

The conceptual hierarchy is: **individual token bids determine pairing eligibility; allocation-unit scores determine capacity eligibility; global fairness resolves exact boundary ties; randomness resolves only token-equivalent pairing choices or equally fair global solutions.** Historical outcomes, token balances, unit member count, bid cost, carry-over, expiry, and other accounting consequences are not fairness inputs.

#### 5.5.1 Strict global optimisation objectives

The optimiser MUST apply these objectives in strict priority order. A later objective MUST never reduce the quality achieved by an earlier one:

1. **Maximise unresolved seat utilisation.** Fill the maximum possible number of unresolved seats. A seat MUST NOT remain unassigned when an eligible allocation unit can receive it.
2. **Maximise distinct fairness identities winning a boundary tie.** Among maximum-utilisation solutions, maximise the number of single/pair identities with at least one boundary win.
3. **Distribute additional wins by lexicographic max-min fairness.** Count wins for every participating fairness identity, sort counts ascending, and lexicographically maximise the vector. Five equivalent opportunities among three identities produce `2 / 2 / 1`; six produce `2 / 2 / 2`.

The objectives apply to the complete fairness-identity eligibility structure. For example, if identity A is eligible only for Monday while B and C are eligible Monday through Wednesday, every optimal solution assigns Monday to A and distributes the other dates between B and C. The implementation MUST NOT approximate this with a greedy opportunity-count or weekday-order rule.

#### 5.5.2 Final random selection and published order

When multiple complete allocations are equivalent under all three objectives, use the injected random selector, backed by a cryptographically secure generator, to choose uniformly among them. This capacity-boundary randomness is not applied per date. Canonicalize dates, allocation-unit fairness identities, slots, and solutions before invoking it.

The selected solution and its round-level random audit value are persisted in the processing transaction. A retry after rollback may select a different equally optimal solution because no result committed; a `COMPLETED` round MUST never be redrawn, recalculated, or changed.

`algorithm_version` MUST identify reservation-aware capacity, half-day unit construction/pairing, and fairness semantics. Version 1.3 rounds MUST use a new algorithm version; historical rounds retain their original versions and results.

Published ranking places successful units above the physical-seat boundary and unsuccessful units below it. Pair members are adjacent in one visual group at one unit rank, with morning before afternoon; each member shows their individual bid. No physical seat number is assigned.

### 5.6 Visibility

- Before processing, an employee can see only their own bid set, balance, dates, public round/configuration metadata, and each open-round date's public reservation count, assignable capacity, and description.
- After processing, all authenticated employees can see all bidders' names, individual bid amounts, attendance periods, persisted unit grouping/order, and success status.
- Published dates show physical capacity, reserved count, assignable capacity, and the reservation's public description where present.
- Employees who did not bid on a date do not appear in its participant list.

### 5.7 Bid-reminder rules

1. Bid reminders are an employee-level, explicit opt-in and default to disabled. The employee selects one start weekday from `MONDAY` through `FRIDAY`; `MONDAY` is the default retained preference.
2. The enabled flag and start weekday synchronize across clients. Push permission and subscription are device/browser-installation state: configuring reminders on desktop does not grant permission on a phone, and registering one device does not register another.
3. Enabling the account preference does not implicitly subscribe the current browser. Each desired device MUST separately complete a user-initiated Web Push permission and subscription flow. An employee may retain and use multiple active devices.
4. Disabling the account preference stops all bid-reminder sends but retains valid device subscriptions and the selected weekday. Re-enabling therefore reuses them without another permission prompt where the browser and operating system still permit notifications.
5. On each configured scheduler trigger, an employee is eligible only when the current round is `OPEN`, reminders are enabled, the business weekday is at or after the selected Monday–Friday start day, at least one active subscription exists, no positive bid exists for that employee in the current round, and no suppression exists for that employee and round.
6. A complete bid-set replacement containing at least one positive bid stops reminders. An empty/all-zero replacement does not count as having placed bids. If the employee later removes every positive bid while the round remains open, ordinary eligibility resumes unless that round is suppressed.
7. One logical reminder is generated at most once per employee, round, and business date. It is sent independently to every active registered device present when that logical reminder is processed. Multiple devices may therefore display the same reminder.
8. The generic notification states that bids for the next week have not yet been placed and contains no token amounts, bid values, attendance choices, colleague information, or other sensitive business data.
9. Where the browser supports custom notification actions, offer **Place bids** and **Skip reminders this week**. **Place bids** opens the bidding route. **Skip reminders this week** opens the authenticated application and requires an explicit confirmation; it MUST NOT mutate state directly from a notification action, URL, or unauthenticated background request.
10. Because notification actions are not universally supported, tapping the notification body opens the bidding route with an in-app reminder affordance that exposes the same choices. Unsupported actions therefore reduce convenience, not functionality.
11. Confirming **Skip reminders this week** creates an immutable suppression for the current open round. The confirmation explains that it cannot be undone for that round and that reminders automatically resume for the next round if the global preference remains enabled. Disabling and re-enabling the global preference does not remove a suppression.
12. Browser/operating-system notification denial or revocation is independent of the server preference. The application cannot override it and must show current-device guidance where detectable. A registered device entry is not a guarantee that the operating system will display a notification.
13. Web Push is best effort. Focus modes, browser/OS policy, connectivity, expired subscriptions, vendor push services, and application downtime may prevent or delay delivery. The product MUST never claim guaranteed delivery or that a notification was seen.

## 6. Round lifecycle and scheduler

### 6.1 States

`OPEN -> PROCESSING -> COMPLETED`

`FAILED` MAY be used for operational visibility, provided retry behavior is explicit and safe. A completed round is immutable. State transitions and timestamps are persisted.

### 6.2 Initialization

On a fresh database, an idempotent bootstrap service MUST create an open round with the next valid cutoff and corresponding Monday–Friday dates. It creates participation/balance records for all provisioned employees.

### 6.3 Scheduled processing

The allocation `quarkus-scheduler` job runs using its configured Quartz expression and the shared business time zone. The current release assumes one Quarkus instance; it does not implement distributed locking, clustered Quartz, or leader election. The job MUST nevertheless be transactional and idempotent.

At each trigger, the service MUST:

1. Find the due `OPEN` round and atomically mark it `PROCESSING`; do nothing if no due round exists.
2. Lock the round for write and verify that it has not already been processed.
3. Load all five target dates, lock their applicable seat reservations, and load all positive bids for the round.
4. Validate each reservation against the round's physical capacity and calculate each date's assignable capacity.
5. Partition bids by attendance period, select pairable bids by token rank, resolve only exact-token pairing-selection ties, construct opposite-order complementary pairs, and create all allocation units.
6. Rank units by combined score against assignable capacity and classify every unit as a fixed winner, fixed loser, or exact-score boundary candidate.
7. Establish all fixed unit outcomes and identify boundary units and unresolved capacity.
8. Construct the complete round-level unresolved allocation problem, solve objectives 1–3, and randomly choose only among globally equivalent optimal solutions.
9. Persist every allocation unit, its members, one individual result per positive bid, and the round audit including reservations, pairing decisions, and unit scores.
10. Persist `BID_SPEND` for every individual bid belonging to a successful unit only.
11. Calculate participant spending, remaining balance, carry-over, and closing balance. Unsuccessful unit members receive no debit or refund entry.
12. Mark the round `COMPLETED` with `processed_at`.
13. Create exactly one successor `OPEN` round, snapshot configuration, validate reservations, and create its dates.
14. Create participation records for all provisioned employees with `grant + carry-over`.

These operations SHOULD commit in one transaction. If transaction size later becomes a concern, a staged design is allowed only if externally invisible, restartable, and protected by equivalent constraints.

Database constraints MUST prevent duplicate successor rounds, bids, units, unit members, individual results, audit records, participation rows, and ledger effects. Pairing/allocation MUST NOT depend on load, weekday, employee-row, map/set, or insertion order. A rollback retry may select different token-equivalent pairs or an equivalent optimal allocation. A `COMPLETED` round never invokes pairing, classification, optimisation, or randomness again.

### 6.4 Boundary behavior

The backend's current instant is authoritative. A request arriving at or after cutoff MUST be rejected even if the scheduled job has not yet completed. The UI refreshes after cutoff and may temporarily show processing status. The assignment view switches to the newly completed round as soon as it is published; the bidding view switches to its successor.

### 6.5 Scheduled bid reminders

A separate normal Quarkus `@Scheduled` method runs once at each configured bid-reminder cron trigger. Its default is 10:00 Monday through Friday in the shared `Europe/Berlin` business time zone. It is not a polling loop and performs no periodic catch-up scan. If the application is unavailable for a trigger, that day's reminder is missed.

The method MUST have a stable identity, use `concurrentExecution = SKIP`, and reference the configured cron expression and shared time zone through `@Scheduled` property expressions. When its feature-level scheduler flag is disabled, it performs no work. The current single-instance deployment assumptions in section 6.3 also apply.

At each trigger, the service MUST:

1. Resolve the current `OPEN` round and the trigger's business date/weekday using the shared business time zone; stop when there is no open round or the weekday is outside Monday–Friday.
2. Select employees satisfying every eligibility rule in section 5.7 without exposing their bids.
3. Atomically claim at most one logical dispatch per employee, round, and business date before contacting external push services.
4. Snapshot the employee's active subscriptions for that dispatch and attempt one send to each. A failure for one employee or device MUST NOT prevent remaining recipients from being processed.
5. Record aggregate dispatch outcome and per-device attempt outcome without storing payload secrets in logs.
6. Mark permanently rejected/expired subscriptions inactive when the vendor response establishes that they can no longer be used. Temporary failures are recorded but are not retried by a polling scheduler.

The uniqueness claim makes duplicate scheduler invocation safe, but external Web Push is not an exactly-once transport. A push-service acceptance response means only that the vendor accepted the request; it does not prove device display or user visibility.

## 7. Authentication, identity, and security

### 7.1 Account model and provisioning

- Employees are created manually in PostgreSQL before they may use the application. The application never self-registers an unknown email address.
- Email is the only login identifier. It is trimmed, Unicode-normalized as appropriate, converted to lowercase, and matched case-insensitively.
- Every employee is provisioned with email, first name, and last name. A password hash is initially null.
- There is no separate username or external identity provider. Every authenticated employee has the `USER` role. An employee whose manually maintained `is_admin` flag is true additionally has the `ADMIN` role.
- Administrator status is managed only through controlled direct database access. The application provides no endpoint or UI for viewing all users, provisioning employees, or granting/revoking `ADMIN`.

### 7.2 Quarkus Security architecture

- Use Quarkus's built-in form-based HTTP authentication mechanism to process email/password login, create the encrypted persistent authentication cookie, renew it during activity, resolve authenticated requests to `SecurityIdentity`, and perform server-side logout.
- Configure form authentication for SPA behavior: no login, landing, or error redirects. Successful submission returns `200`; invalid credentials return `401`.
- Use a custom Quarkus `IdentityProvider<UsernamePasswordAuthenticationRequest>` only for the missing credential-verification piece. It normalizes the submitted email, loads the employee with Panache, verifies the Argon2id hash through a maintained library, and returns a `SecurityIdentity` whose principal name is the normalized email and whose roles are derived from the employee's current `is_admin` value.
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
- The current release has no password change or forgotten-password flow. An operator may clear the password hash and activation state through a controlled database operation to require activation again; any already issued stateless cookie remains valid until expiry unless the global session-encryption key is rotated.

### 7.6 Persistent form-authentication cookie

Quarkus form authentication stores the authenticated identity and idle expiry in an encrypted cookie rather than in a server-side session table:

- Set the inactivity timeout to 30 days by default and the new-cookie/renewal interval to 24 hours. Active use renews the encrypted cookie no more frequently than the configured interval.
- Set cookie `Max-Age` to 30 days so authentication survives browser/app restarts and is refreshed when Quarkus renews the cookie.
- Configure a strong, externally supplied session-encryption key. Changing that key invalidates all outstanding form-authentication cookies.
- The authentication cookie is host-only and has `Secure`, `HttpOnly`, `SameSite=Strict`, and `Path=/`. Do not set a `Domain` attribute.
- Logout uses `FormAuthenticationMechanism.logout(SecurityIdentity)` and expires the current client's cookie.
- The form cookie is stateless. The current release intentionally has no per-device session database, individual server-side session revocation, or separate absolute session lifetime. A copied cookie remains valid until its encrypted idle expiry unless the global encryption key is rotated.

The PWA relies on the browser cookie jar and cannot read the HttpOnly authentication cookie. Flutter web requests must include credentials.

Android uses the same form login endpoint and encrypted authentication cookie. Its HTTP client must persist the authentication cookie using platform-secure storage and send it through a managed cookie jar. It must not copy the cookie into ordinary preferences, logs, crash reports, analytics, or an `Authorization` header.

### 7.7 CSRF, request authentication, and web security

- `quarkus-rest-csrf` issues a separate CSRF cookie and verifies the matching `X-CSRF-TOKEN` request header on state-changing requests. Configure an HMAC signature key of at least 32 characters, JSON-request support, `Secure`, `SameSite=Strict`, and a JavaScript-readable CSRF cookie. The authentication cookie remains HttpOnly.
- Both PWA and Android obtain the CSRF cookie from the configured token-creation GET endpoint and echo its value in the header for Jakarta REST state-changing requests, including public activation endpoints.
- `/j_security_check` is handled by Quarkus HTTP authentication before Jakarta REST, so it MUST additionally enforce the configured allowed `Origin` (and a safe `Referer` fallback where appropriate) through Quarkus HTTP security customization. Combined with HTTPS and `SameSite=Strict`, this prevents login CSRF without reimplementing the REST CSRF token algorithm.
- All `/api/*` endpoints except the explicitly documented public configuration, CSRF-token creation, and activation-flow endpoints require Quarkus authentication. `/j_security_check` is also public but CSRF-protected. Health and OpenAPI exposure is controlled separately by deployment configuration.
- Resources use Quarkus security annotations/path policies and obtain the acting user from `SecurityIdentity`; they never trust a client-provided employee identity.
- All `/api/admin/*` operations require `ADMIN` through Quarkus path policy and/or `@RolesAllowed("ADMIN")`. Because the form cookie is stateless, every admin operation MUST additionally reload the acting employee and confirm `is_admin = true`; removing the database flag therefore blocks subsequent admin requests even if an older cookie still carries the role. Granting the flag may require a fresh login before the cookie contains `ADMIN`.
- PWA API calls use relative same-origin `/api/...` URLs. Android uses its configured absolute HTTPS base URL; browser CORS, if enabled at all, uses a strict allowlist.
- TLS is mandatory outside local development. Apply CSP, HSTS at the HTTPS boundary, secure headers, request-size limits, dependency scanning, secret rotation procedures, and redaction of authentication and personal data in logs.
- Web Push subscription endpoints and authentication keys are capability-bearing secrets. Return them only where strictly required to register the current authenticated browser; never expose one employee's subscription material to another employee or include it in logs, metrics, traces, problem details, analytics, or notification payloads.
- Validate submitted push endpoints as bounded HTTPS URLs without user information, fragments, loopback/private/link-local destinations, or unsafe redirects. Outbound delivery MUST defend against SSRF and connect only to validated browser push-service endpoints. VAPID private keys are external secrets; only the public application-server key may be returned to an authenticated client.
- Passwords, activation codes, activation authorizations, authentication cookies, stored hashes, session-encryption/CSRF/VAPID private keys, push subscription secrets, and SMTP credentials MUST never be returned by ordinary APIs or written to logs, metrics, or traces.

## 8. Concurrency and transactions

Bid replacement MUST use pessimistic database locking, not an optimistic version column:

1. Begin a transaction.
2. Load the authenticated employee's participation record for the open round with `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`).
3. Re-read round state/cutoff and recalculate balance within the transaction.
4. Validate the entire replacement set.
5. Replace all existing bids atomically and commit.

The lock is scoped to one employee and round, allowing different employees to submit concurrently. A lock timeout/deadlock maps to `409 Conflict` (or `503` if the database is broadly unavailable) with a retryable problem code. No `@Version` column is required.

Scheduler processing locks the due round. Password creation locks the employee and activation rows. All balance- and activation-state changes use database transactions even in the initial single-instance deployment; form-cookie renewal itself is handled by Quarkus and has no database transaction.

Reservation creation/deletion runs transactionally. When the target date belongs to an existing round, the service locks that round before rechecking state and cutoff; scheduler processing takes the same round lock before locking/reading reservations. A unique target-date constraint prevents duplicate reservations. Concurrent admin operations that lose the race return `409` with an authoritative refreshed state. Authorization is revalidated from the current employee database row inside the mutation transaction.

Notification preference updates and subscription registration/removal are transactional and scoped to the authenticated employee. Suppression creation locks or otherwise atomically validates the current open round and is idempotent for the employee/round pair. The reminder scheduler claims a unique employee/round/business-date dispatch before external delivery; concurrent or repeated invocations cannot create a second logical dispatch or a second attempt for the same subscription. Network calls MUST NOT hold locks on bidding, participation, or round-allocation rows.

## 9. Database model

Use PostgreSQL-generated `bigint` identities or UUIDs consistently. The following uses `bigint` for readability. Every table SHOULD include `created_at`; mutable tables SHOULD include `updated_at`. All foreign keys are indexed.

### 9.1 `employee`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK, not null |
| `email` | `varchar(320)` | not null, stored normalized lowercase, unique |
| `first_name` | `varchar(255)` | not null |
| `last_name` | `varchar(255)` | not null |
| `is_admin` | `boolean` | not null, default `false` |
| `password_hash` | `varchar(512)` | nullable encoded Argon2id PHC string |
| `password_set_at` | `timestamptz` | nullable; set with password hash |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

Manual provisioning supplies normalized email, first name, last name, and administrator status. New employees default to non-admin; only controlled direct database maintenance changes `is_admin`. It leaves `password_hash` and `password_set_at` null. Enforce nonblank names/email in application provisioning guidance and a unique normalized email in the database.

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
| `attendance_period` | `varchar(20)` | not null; `FULL_DAY`, `MORNING_ONLY`, or `AFTERNOON_ONLY`; default `FULL_DAY` |

Unique `(round_date_id, participation_id)`. Application code verifies both references belong to the same round.

### 9.7 `seat_reservation`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `target_date` | `date` | not null, unique |
| `reserved_seat_count` | `integer` | not null, check `> 0` |
| `description` | `varchar(500)` | nullable; trimmed public plain text |
| `created_by_employee_id` | `bigint` | FK to `employee`, not null |
| `created_at` | `timestamptz` | not null |

The reservation is date-based rather than linked to `round_date` so an administrator can create it before that future round exists. Application validation restricts dates to Monday–Friday, enforces the applicable capacity, and controls the mutation window. Index `target_date` through its unique constraint and index `created_by_employee_id`. Physical deletion is allowed only while the reservation is mutable; once cutoff closes, the row is immutable business/audit data needed by published assignments.

### 9.8 `allocation_unit`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_date_id` | `bigint` | FK, not null |
| `unit_type` | `varchar(20)` | not null; `SINGLE` or `HALF_DAY_PAIR` |
| `score_tokens` | `integer` | not null, check `> 0`; individual bid or pair sum |
| `fairness_identity` | `varchar(128)` | not null; canonical employee/pair identity |
| `assigned` | `boolean` | not null |
| `score_rank` | `integer` | not null, `>= 1`; dense rank by descending unit score |
| `final_rank` | `integer` | not null, `>= 1`; immutable physical-seat/unit order |
| `resolution` | `varchar(32)` | not null; `FIXED_WINNER`, `FIXED_LOSER`, `GLOBAL_TIE_WINNER`, or `GLOBAL_TIE_LOSER` |
| `boundary_tie_group` | `varchar(64)` | nullable; date/exact-score boundary identifier |
| `created_at` | `timestamptz` | not null |

Unique `(round_date_id, final_rank)` and `(round_date_id, fairness_identity)`. Index resolution and boundary group by date. Checks keep `assigned`, `resolution`, and group nullability coherent. A pair and a single each consume one unit rank and at most one physical seat.

### 9.9 `seat_assignment`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_date_id` | `bigint` | FK, not null |
| `bid_id` | `bigint` | FK, not null, unique |
| `allocation_unit_id` | `bigint` | FK, not null |
| `assigned` | `boolean` | not null |
| `attendance_period` | `varchar(20)` | not null; immutable copy from the bid |
| `unit_member_order` | `smallint` | not null; `1` for a single/morning member, `2` for afternoon pair member |
| `display_rank` | `integer` | not null, `>= 1`; unique employee-row display order within date |
| `created_at` | `timestamptz` | not null |

Persist one result for every positive bid, including both members of a pair and every unsuccessful bid. Unique `(round_date_id, display_rank)` and `(allocation_unit_id, unit_member_order)`. A single has only member order 1. A pair has exactly two members: `MORNING_ONLY` at order 1 and `AFTERNOON_ONLY` at order 2. Application validation and scheduler transactions ensure unit/date consistency, member cardinality, period compatibility, and that member `assigned` matches the unit. Pair members have consecutive display ranks and remain visually grouped.

### 9.10 `round_allocation_audit`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_id` | `bigint` | FK, not null, unique |
| `algorithm_version` | `varchar(32)` | not null |
| `input_fingerprint` | `char(64)` | not null; SHA-256 of the canonical round-level allocation input |
| `objective_summary` | `jsonb` | not null; canonical object containing filled unresolved slots, distinct tie winners, and sorted tie-win vector |
| `selected_solution_fingerprint` | `char(64)` | not null; SHA-256 of the canonical selected complete solution |
| `pairing_audit` | `jsonb` | not null; canonical majority selection, tie/composition choices, and resulting units per date |
| `capacity_selection_value` | `varchar(255)` | nullable; audit value for final equivalent-optimum choice |
| `created_at` | `timestamptz` | not null |

Create exactly one audit row per processed round. Canonical input includes round/reservation capacity, every bid's stable identifiers, tokens and period, pairing candidate sets, constructed units, score classification, boundary eligibility, and unresolved capacities. The selected-solution encoding includes every unit and individual outcome. `pairing_audit` records enough canonical information to explain deterministic choices and random indexes/seeds used among token-equivalent pairing alternatives. Public reservation descriptions are excluded. All formats are versioned by `algorithm_version` and order-independent.

The capacity selection value describes only selection among complete globally optimal solutions. Pairing randomness is represented separately in `pairing_audit`. Outcomes are fully materialized in `allocation_unit` and `seat_assignment`; reads reconstruct nothing.

### 9.11 `token_ledger`

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

The participation row is the efficient balance snapshot; the ledger is the accounting audit source. `BID_SPEND` is written for each individual bid whose unit and assignment are successful. A successful pair therefore produces two separate debits, one for each original bid, and no debit for the summed score. Ledger and participation totals reconcile with individual persisted results.

### 9.12 `employee_notification_settings

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `employee_id` | `bigint` | FK, not null, unique |
| `bid_reminders_enabled` | `boolean` | not null, default `false` |
| `bid_reminder_start_weekday` | `varchar(16)` | not null, default `MONDAY`; Monday–Friday only |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

The row may be created lazily; absence has the same external meaning as disabled with `MONDAY` retained as the default. Disabling reminders changes only the enabled flag and preserves the weekday and subscriptions.

### 9.13 `web_push_subscription

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `employee_id` | `bigint` | FK, not null |
| `endpoint_hash` | `char(64)` | not null, unique; SHA-256 identity for safe matching |
| `endpoint` | `text` | required while active; validated HTTPS capability URL |
| `p256dh_key` | `text` | required while active |
| `auth_key` | `text` | required while active |
| `expires_at` | `timestamptz` | nullable browser-reported expiry |
| `device_label` | `varchar(120)` | not null; sanitized best-effort description, not trusted identity |
| `status` | `varchar(20)` | not null; `ACTIVE`, `USER_REMOVED`, or `INVALID` |
| `last_seen_at` | `timestamptz` | not null; refreshed by current-device registration |
| `last_successful_push_at` | `timestamptz` | nullable; vendor acceptance, not proof of display |
| `invalidated_at` | `timestamptz` | nullable |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

Index active subscriptions by employee. Endpoint and key material are capability-bearing secrets. A user removal or permanent vendor rejection deactivates the row and SHOULD clear usable endpoint/key material while retaining only the hash and non-sensitive audit metadata. Re-registration may safely reactivate/upsert the same employee/hash after validating a fresh browser subscription; an endpoint already bound to a different employee is never silently reassigned.

### 9.14 `bid_reminder_suppression

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_id` | `bigint` | FK, not null |
| `employee_id` | `bigint` | FK, not null |
| `created_at` | `timestamptz` | not null |

Unique `(round_id, employee_id)`. Rows are immutable and are never deleted or bypassed through the application. A suppression affects only its round.

### 9.15 `bid_reminder_dispatch

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `round_id` | `bigint` | FK, not null |
| `employee_id` | `bigint` | FK, not null |
| `business_date` | `date` | not null; date in the round's business zone |
| `scheduled_for` | `timestamptz` | not null; logical configured trigger instant |
| `status` | `varchar(20)` | not null; `PROCESSING`, `COMPLETED`, `PARTIAL`, or `FAILED` |
| `subscription_count` | `integer` | not null, `>= 1` |
| `accepted_count` | `integer` | not null, default `0`, `>= 0` |
| `failed_count` | `integer` | not null, default `0`, `>= 0` |
| `completed_at` | `timestamptz` | nullable |
| `created_at` | `timestamptz` | not null |

Unique `(round_id, employee_id, business_date)`. Checks keep counts and terminal status coherent. The row is the logical idempotency claim and operational record; it does not imply that a vendor-accepted notification was displayed.

### 9.16 `web_push_delivery_attempt`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigint` | PK |
| `dispatch_id` | `bigint` | FK, not null |
| `push_subscription_id` | `bigint` | FK, not null |
| `outcome` | `varchar(24)` | not null; `ACCEPTED`, `TEMPORARY_FAILURE`, or `PERMANENT_FAILURE` |
| `provider_status` | `integer` | nullable, safe HTTP status only |
| `attempted_at` | `timestamptz` | not null |

Unique `(dispatch_id, push_subscription_id)`. Never persist response bodies, endpoint URLs, encryption keys, payload ciphertext, or vendor correlation data that may contain secrets.

### 9.17 Panache mapping

Use explicit entities/repositories for bids with periods, reservations, allocation units, individual assignments, audit JSON/fingerprints, notification settings, subscriptions, suppressions, and dispatch/attempt records. Do not expose entities as DTOs. Fetch published unit/member projections and notification eligibility without N+1 queries. Round processing inserts units, members/results, audit, and individual ledger effects atomically before completion; reminder dispatch persistence uses separate short transactions around external network calls.

## 10. Liquibase

- `db/changelog/db.changelog-master.yaml` is the sole Liquibase entry point configured in Quarkus. It is an orchestration changelog and includes, in this order:
  1. `db/changelog/db.changelog-changes.yaml` for versioned database changes;
  2. `db/changelog/grant-permissions.yaml` for runtime-role permissions.
- `db/changelog/db.changelog-changes.yaml` is the second-level aggregate changelog. It includes ordered change files from `db/changelog/changes/`; the deployed changelog begins with `changes/001-initial-schema.yaml`. Future versioned schema or data changes are added as new, sequentially named files and included from this aggregate rather than directly from the master changelog.
- All changesets in the version 1.2 baseline are immutable for version 1.3. Use one or more new sequential change files to add `bid.attendance_period`, create/backfill `allocation_unit`, link/reshape individual results, and extend audit data. Existing bids become `FULL_DAY`; every existing assignment becomes a one-member `SINGLE` unit preserving outcome/order. Historical pairing audit is the canonical empty value, and any prior final random value is preserved as capacity-selection audit. Existing completed rounds, ledgers, fingerprints, and algorithm versions are never recalculated or labeled half-day-aware.
- Apply the migration in safe stages: add nullable/defaulted structures, backfill and validate existing data, then enforce new constraints. Any legacy ranking/audit columns needed to explain deployed history may be retained read-only; new version 1.3 writes use the normalized allocation-unit model.
- The half-day version 1.3 implementation and its migrations are already committed before this second version 1.3 feature. Do not rewrite those committed changesets for Web Push. Append one or more newly numbered version 1.3 change files for notification settings, subscriptions, suppressions, dispatches, and attempts. Existing employees default to reminders disabled with Monday as the effective start day; no subscription, suppression, or delivery rows are synthesized.
- `db/changelog/grant-permissions.yaml` contains the separate `set-permissions` changeset with `runAlways: true`. It runs after all versioned changes and executes `db/sql/grant-permissions.sql` as one PostgreSQL block with statement splitting disabled and comments retained. The SQL grants `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on every non-Liquibase table in the `public` schema and `USAGE` on every sequence in that schema to the role supplied through the Liquibase `${applicationUser}` change-log parameter. The migrator must have authority to issue those grants.
- The Quarkus Liquibase configuration MUST point to `db/changelog/db.changelog-master.yaml` and provide `applicationUser` from the configured application database username. Permission application therefore covers newly created tables and sequences on every migration run without mixing permission logic into individual versioned change files.
- A separate idempotent application bootstrap mechanism creates the first bidding round; environment-specific employees and runtime round data MUST NOT be inserted by production changelogs.
- Provide rollback blocks where safe. Every changeset applied to any deployed environment is immutable: its identifier, author, path, and contents MUST remain stable, and subsequent changes are always appended through newly numbered change files.
- Quarkus runs Liquibase at application startup. Production deployment MUST ensure only one migrator runs; this follows naturally from the current release's single-instance topology.
- Integration tests MUST both (a) start from an empty PostgreSQL database and apply the complete master changelog through both include levels and (b) exercise each new migration from the preceding deployed schema with representative existing data. Tests verify the resulting schema, preserved/backfilled data, constraints, and permission changes.

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
```
j_username=alex%40example.com&j_password=user-entered-password


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
  "email": "alex@example.com",
  "isAdmin": true
}
```

`isAdmin` is loaded from the current employee database row and controls role-aware presentation only; backend admin endpoints independently enforce and revalidate administrator status.

### 11.4 Notification settings and devices

All endpoints require authentication. State-changing requests require the CSRF cookie/header contract. The acting employee always comes from `SecurityIdentity`; no request accepts an employee ID.

`GET /api/settings/notifications

```json
{
  "bidRemindersEnabled": true,
  "bidReminderStartWeekday": "WEDNESDAY",
  "schedule": {
    "systemEnabled": true,
    "localTime": "10:00",
    "timeZone": "Europe/Berlin",
    "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
  },
  "webPushApplicationServerKey": "base64url-public-vapid-key",
  "currentRound": {
    "roundId": 18,
    "cutoffAt": "2026-08-07T20:00:00Z",
    "suppressed": false,
    "suppressionAvailable": true
  },
  "devices": [{
    "id": 12,
    "label": "iPhone · Home Screen web app",
    "registeredAt": "2026-08-03T08:40:00Z",
    "lastSeenAt": "2026-08-04T09:55:00Z",
    "lastSuccessfulPushAt": null
  }]
}
```

Only active devices are returned. `webPushApplicationServerKey` is the VAPID public key and is not secret. `schedule.systemEnabled` exposes only operational availability, not secrets; the UI clearly reports when reminder dispatch is disabled at deployment level. The backend does not claim that a listed remote device still has OS permission; the current browser combines this response with local feature detection, `Notification.permission`, and its current `PushManager` subscription. When no settings row exists, return disabled and `MONDAY`. `currentRound` is null when no open round exists.

`PUT /api/settings/notifications

```json
{
  "bidRemindersEnabled": true,
  "bidReminderStartWeekday": "WEDNESDAY"
}
```

Both fields are required; the weekday is one of Monday through Friday. The operation atomically creates/replaces the employee preference and returns the authoritative settings representation. Disabling does not delete subscriptions or round suppressions. Enabling does not request browser permission or create a subscription.

`POST /api/settings/notifications/devices

```json
{
  "endpoint": "https://browser-push-service.example/subscription/capability-token",
  "keys": {
    "p256dh": "base64url-key",
    "auth": "base64url-secret"
  },
  "expirationTime": null,
  "deviceLabel": "iPhone · Home Screen web app"
}
```

This upserts the authenticated employee's current browser subscription by endpoint hash and returns `201 Created` for a new device or `200 OK` for a refresh/reactivation, with the non-secret device representation and `Location`. Validate URL, field sizes, key encodings, expiry, and sanitized label. Never echo endpoint or key material. An endpoint bound to another employee returns a generic conflict and is not reassigned.

`DELETE /api/settings/notifications/devices/{deviceId}

Removes/deactivates only the authenticated employee's device and returns `204 No Content`; an unknown or foreign ID returns non-enumerating `404`. This is distinct from disabling reminders globally.

`POST /api/settings/notifications/bid-reminders/current-round/suppression

```json
{"roundId": 18}
```

After the in-app confirmation, create the immutable employee/round suppression and return `204 No Content`. Repeating the same request is idempotent. A stale/non-open round or an employee who has already placed a positive bid returns `409` with refreshed state. There is deliberately no DELETE/undo endpoint.

### 11.5 Administrative seat reservations

All operations in this section require authentication, the `ADMIN` role, and a current database recheck of `employee.is_admin`. State-changing operations also require the CSRF cookie/header contract.

`GET /api/admin/seat-reservations?from=YYYY-MM-DD&to=YYYY-MM-DD`

Both dates are required, inclusive, and `from <= to`; the maximum range is 366 days. The response is ordered by date and returns an empty array when no reservations exist:

```json
{
  "serverTime": "2026-08-11T10:15:00Z",
  "timeZone": "Europe/Berlin",
  "reservations": [{
    "id": 81,
    "date": "2026-08-17",
    "reservedSeatCount": 3,
    "physicalSeatCapacity": 12,
    "description": "Customer workshop",
    "mutable": true,
    "cutoffAt": "2026-08-14T20:00:00Z",
    "roundStatus": "OPEN"
  }]
}
```

For a future date not yet belonging to a round, `cutoffAt` and `roundStatus` are null, `mutable` is true, and `physicalSeatCapacity` is the current configured value.

`POST /api/admin/seat-reservations`

```json
{
  "date": "2026-08-17",
  "reservedSeatCount": 3,
  "description": "Customer workshop"
}
```

Creation returns `201 Created`, a `Location` header, and the authoritative reservation representation. A second reservation for the date returns `409`; the current release does not merge or overwrite it.

`DELETE /api/admin/seat-reservations/{reservationId}`

Deletion returns `204 No Content`. It is allowed only while the reservation is mutable. An unknown ID returns `404`; a reservation whose cutoff/state makes it immutable returns `409`. The service uses the stored target date and never accepts a client-supplied date for deletion.

### 11.6 Bidding context

`GET /api/bidding/current`

```json
{
  "roundId": 18,
  "status": "OPEN",
  "cutoffAt": "2026-08-07T20:00:00Z",
  "cutoffTimeZone": "Europe/Berlin",
  "serverTime": "2026-08-04T10:15:00Z",
  "seatCapacity": 12,
  "startingBalance": 70,
  "bidTotal": 20,
  "availableToBid": 50,
  "days": [
    {
      "date": "2026-08-10",
      "weekday": "MONDAY",
      "tokens": 20,
      "attendancePeriod": "MORNING_ONLY",
      "reservedSeatCount": 2,
      "assignableSeatCapacity": 10,
      "reservationDescription": "Customer workshop"
    },
    {
      "date": "2026-08-11",
      "weekday": "TUESDAY",
      "tokens": 0,
      "attendancePeriod": "FULL_DAY",
      "reservedSeatCount": 0,
      "assignableSeatCapacity": 12,
      "reservationDescription": null
    }
  ]
}
```

The response contains all five dates. `attendancePeriod` is the saved value for a positive bid and `FULL_DAY` for a zero/no-bid draft. `seatCapacity` is physical capacity and each day satisfies `assignableSeatCapacity = seatCapacity - reservedSeatCount`; these reservation fields remain read-only. `availableToBid` is the employee's uncommitted token amount, not physical-seat availability or post-allocation balance, and attendance period never changes it. `serverTime` supports countdown display; `cutoffAt` is authoritative.

### 11.7 Replace bids

`PUT /api/bidding/current/bids`

```json
{
  "roundId": 18,
  "bids": [
    {"date": "2026-08-10", "tokens": 20, "attendancePeriod": "MORNING_ONLY"},
    {"date": "2026-08-11", "tokens": 8, "attendancePeriod": "FULL_DAY"}
  ]
}
```

Semantics:

- `roundId` prevents a stale page from writing into a successor round.
- The array represents the complete replacement set; omitted/zero dates become no bid.
- Each positive entry includes one valid attendance period. During rollout, omission is interpreted as `FULL_DAY`; unknown/null values from a version 1.3 client are rejected.
- On success return `200` with the same authoritative shape as `GET /api/bidding/current`.
- Bidding endpoints never accept an email, employee ID, balance, allocation result, or token-spend decision from the client; the acting employee always comes from Quarkus `SecurityIdentity`.

### 11.8 Published assignments

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
    "reservedSeatCount": 2,
    "assignableSeatCapacity": 10,
    "reservationDescription": "Customer workshop",
    "occupiedSeatCount": 10,
    "assignedEmployeeCount": 11,
    "participants": [{
      "allocationUnitId": 901,
      "unitType": "HALF_DAY_PAIR",
      "unitRank": 4,
      "unitScoreTokens": 30,
      "employeeId": 42,
      "firstName": "Alex",
      "lastName": "Example",
      "tokens": 20,
      "attendancePeriod": "MORNING_ONLY",
      "assigned": true,
      "displayRank": 4,
      "isCurrentUser": true
    }, {
      "allocationUnitId": 901,
      "unitType": "HALF_DAY_PAIR",
      "unitRank": 4,
      "unitScoreTokens": 30,
      "employeeId": 84,
      "firstName": "Sam",
      "lastName": "Example",
      "tokens": 10,
      "attendancePeriod": "AFTERNOON_ONLY",
      "assigned": true,
      "displayRank": 5,
      "isCurrentUser": false
    }]
  }]
}
```

Top-level `seatCapacity` is physical capacity. `occupiedSeatCount` counts successful allocation units and never exceeds assignable capacity; `assignedEmployeeCount` counts successful individual bids and may be higher because a pair contains two employees. `myStatus` remains individual. Participants are ordered by unit rank and member order; pair members share unit ID/rank/score and are adjacent. Singles use `unitType: SINGLE`, and `unitScoreTokens` equals their individual tokens.

The API exposes the persisted unit grouping and combined score needed to explain pairing, but not optimiser/audit internals. For a globally resolved exact-score boundary group, successful units appear above the physical-seat boundary and unsuccessful units below it in immutable unit/member order.

### 11.9 Help

Help content SHOULD be bundled with Flutter for availability without another authenticated call. If centrally managed later, use `GET /api/public/help` with versioned/sanitized content.

### 11.10 Error contract

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
| `400` | malformed/invalid values, dates, duplicates, negatives, total over balance, invalid reservation date/count/description/range, invalid reminder weekday, push endpoint, key, expiry, or device label |
| `401` | invalid credentials; missing, invalid, or expired Quarkus form-authentication cookie |
| `403` | authenticated request lacks required CSRF proof or administrator authorization, or its origin is rejected |
| `404` | no published/open resource, reservation, or employee-owned registered device when applicable |
| `409` | stale round, cutoff passed, immutable/duplicate reservation, round processing, pessimistic lock timeout/concurrent update, push endpoint ownership conflict, or inapplicable reminder suppression |
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
reservation/     admin authorization, dated reservation lifecycle, capacity calculation
allocation/      half-day pairing, unit construction/ranking, global fairness, result mapping
tokens/          participation balances and ledger
notification/    preferences, device subscriptions, Web Push delivery, suppression, reminder scheduler
resource/        REST resource interfaces only
resource/impl/   REST resource implementation classes
dto/             REST request/response DTOs and problem representations
exception/       application exceptions and REST exception mappers
persistence/     Panache entities and repositories
bootstrap/       initial round and newly provisioned participant reconciliation
```

The package named `api` MUST NOT be used. The `resource` package MUST contain only Java interfaces that define the REST contract. These interfaces carry the JAX-RS endpoint annotations and all SmallRye OpenAPI annotations, including operation descriptions, parameters, response codes, media types, security requirements, and schema references. Concrete classes belong in `resource.impl`, implement the corresponding resource interfaces, and delegate immediately to application/domain services. Endpoint and OpenAPI annotations MUST NOT be duplicated on the implementation classes. DTOs and exception mappers belong in their dedicated packages, not in `resource`.

The `allocation/` package implements round-level allocation rather than independent per-date draws. It SHOULD separate:

1. immutable bid/period input and deterministic majority-side token classification;
2. injectable token-equivalent pairing selection, opposite-order unit construction, and canonical fairness identities;
3. deterministic unit-score ranking and boundary classification;
4. construction/optimisation of one immutable round-level unresolved problem;
5. final random selection among globally equivalent solutions; and
6. conversion into unit, individual-result, audit, and accounting inputs.

The core optimiser and fairness logic MUST be pure domain logic without database access. It accepts an immutable canonical problem and returns a complete selected solution plus objective/audit data. The application may use bipartite matching, constrained search, integer optimisation, or another in-process technique appropriate to five target dates and the expected small employee population, but MUST demonstrably preserve the strict objective hierarchy. Do not add an external optimisation service, separate runtime, distributed component, or order-dependent greedy approximation.

Domain services own transactions; resource implementations remain thin. Inject `Clock` and labeled random-selection abstractions for majority-boundary choice, equivalent pair composition, and final global solution. Tests control each choice independently. Production uses cryptographically secure randomness and persists every choice.

The `notification/` package MUST use a maintained standards-based Java Web Push implementation supporting encrypted payloads and VAPID; do not implement Web Push encryption or signing primitives in application code. Separate eligibility selection, idempotent dispatch claiming, payload construction, vendor transport, response classification, subscription invalidation, and REST preference/device management so each is independently testable. Inject the push transport in tests. The reminder scheduled method coordinates the service and contains no REST or UI logic.

### 12.4 Validation

Use Bean Validation for shape constraints and domain validation for cross-field/state rules. The backend repeats all client checks. Bid validation includes attendance enum/default semantics and strips period state when zero is normalized away. Reservation validation remains transactional. Allocation-unit validation rejects duplicate membership, invalid pair periods/cardinality, inconsistent score/outcome, and noncanonical fairness identities. Notification validation covers the Monday–Friday preference, current-round suppression state, bounded device labels, base64url subscription keys, optional expiry, HTTPS endpoint structure, DNS/address/redirect SSRF defenses, employee ownership, and coherent dispatch/attempt outcomes.

### 12.5 OpenAPI and health

- Publish OpenAPI for all REST DTOs and problem responses. The REST interfaces in `resource` are the authoritative source of endpoint-level OpenAPI metadata; contract tests MUST verify that annotations inherited through `resource.impl` produce the expected OpenAPI document.
- Define one OpenAPI cookie security scheme for the Quarkus form-authentication cookie. Public operations declare no authentication requirement; protected operations document the cookie scheme and `X-CSRF-TOKEN` header where relevant. `/j_security_check` is a Quarkus framework endpoint and is documented separately because it is not declared by a resource interface.
- Admin resource interfaces declare the `ADMIN` authorization requirement, CSRF header on mutations, and `403`/`409` problem responses. UI visibility is not a security boundary.
- Notification-setting resource interfaces document synchronized preferences, registered-device metadata, CSRF-protected subscription/suppression mutations, and all validation/conflict responses without exposing subscription secrets.
- Provide liveness and readiness; readiness verifies database connectivity and successful Liquibase state.
- Scheduler or push-provider failure is logged/observed but MUST NOT leak secrets or internal details to clients. External push-service availability is not a readiness dependency.

## 13. Flutter client specification

### 13.1 Shared architecture

Use one Flutter codebase with platform adapters. A recommended structure is feature-first with immutable DTO/domain models, repository/service interfaces, and a predictable state-management package (for example Riverpod). Use `go_router` or equivalent declarative routing.

Routes:

```text
/assignments   initial authenticated route
/bids
/settings
/settings/reminders/skip   authenticated confirmation/deep-link route
/admin/reservations   desktop PWA administrators only
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

- Primary views are **Seat assignments** and **Place bids**. A desktop/wide PWA authenticated as an administrator additionally shows **Admin** as a third destination leading to seat reservations.
- Desktop/wide PWA uses two labeled destinations for ordinary users and three for administrators.
- Mobile uses a two-item tab bar/segmented navigation and MAY allow horizontal swipe; swipe is never the only mechanism.
- The admin destination and route are not shown in compact/mobile PWA layouts and are absent from the Android application, even for an administrator account. Direct compact-web navigation to `/admin/reservations` shows a non-sensitive desktop-required message rather than reservation controls.
- Compact layouts use a hamburger menu. Wider layouts use an equivalent conventional menu.
- The menu always contains **Settings** and **Help**, and conditionally **Get the Android app**. Settings is available to every authenticated user on desktop, compact web, and supported native clients.

### 13.4 Seat assignments view

This is the initial view. It shows exactly five collapsed cards for the latest completed round, sized to fit together on a typical viewport where practical.

Each card displays localized weekday and `dd/MM` date (no year) and has these states:

- neutral: current employee did not bid;
- green: bid and assigned;
- red: bid and not assigned;
- a stronger shade for today when today is among displayed dates;
- a visible accent border and/or **Today** label when today's state is neutral.
- a visible reserved-seat count/badge when one or more seats were reserved, regardless of the current employee's bid status.

Between cutoff and the target week, none may be today; no artificial highlight is applied. Colors MUST have text/icon equivalents and accessible contrast.

Expanding a card displays:

- `occupiedSeatCount of assignableSeatCapacity assignable seats occupied`;
- assigned employee count when it differs from occupied-seat count;
- `reservedSeatCount of seatCapacity total seats reserved` when the count is positive;
- the reservation's public description when present, clearly distinguished from employee assignments;
- participants by persisted allocation-unit rank and member order;
- first and last name (fallback as specified by API);
- each employee's individual token amount;
- `Morning` or `Afternoon` badge for every half-day bidder, including an unmatched half-day single; full-day participants have no period badge;
- one visually grouped row/card for a pair, containing morning then afternoon member and optionally the combined unit score in secondary explanatory text;
- a clear boundary after the last assigned bidder;
- explicit assigned/not-assigned icon/text;
- a highlight for the current employee.

Collapsed/expanded state is local UI state. Loading, empty/bootstrap, processing, authentication, and retryable error states require dedicated UI.

### 13.5 Place bids view

The view displays:

- all five next-round weekdays and `dd/MM` dates;
- one accessible attendance badge/control per date, initially `Full day`, cycling independently through `Full day` → `Morning` → `Afternoon` → `Full day`; each state always combines a visible text label with a distinct semantic icon, while theme-derived background/foreground colors distinguish full-day from half-day attendance;
- each date's read-only reserved-seat count and assignable capacity, with a clear badge/label when the reserved count is positive;
- the public reservation description beneath the affected date when present;
- authoritative starting balance, current bid total, and amount still available to bid;
- numeric token input with increment/decrement controls;
- one auto-distribution selector per day;
- **Auto-distribute** and **Save bids** actions;
- exact cutoff date/time, `Europe/Berlin` (or configured zone), and a periodically refreshed relative countdown;
- when opened from a reminder, a non-intrusive in-app affordance offering **Place bids** and **Skip reminders this week** until the user acts or dismisses the local affordance.

Draft behavior:

- Existing saved bids load into fields.
- The attendance control works by tap/click, keyboard, and screen reader; its current value and cycling behavior are announced and never communicated by color alone.
- `Full day` uses a calendar/full-day icon and the theme's primary-container/on-primary-container color pair. `Morning` uses a sunrise icon, and `Afternoon` uses a daytime/afternoon-sun icon; both half-day states use the same tertiary-container/on-tertiary-container color pair so color communicates the shared half-day category while icon and text communicate the period. Suitable Material examples are `calendar_today_outlined`, `wb_twilight_outlined`, and `light_mode_outlined`; an equivalent icon available in the project's Flutter version MAY be substituted without changing the semantics.
- The icon never replaces the text. The badge has a stable minimum width and alignment so cycling does not shift surrounding controls. Icon/color changes use a short, non-disruptive animation that respects reduced-motion/platform accessibility behavior.
- Desktop/web provides a tooltip such as **Change attendance period**. Accessibility semantics expose label **Attendance period**, the current value, button/toggle behavior, and a hint naming the next state in the cycle. Keyboard focus is clearly visible independently of the state background.
- Changing a period affects only that date. It neither changes token input nor causes an immediate server write.
- A zero-token draft may retain a locally selected period, but saving zero persists no bid/period; reloading that date returns to `Full day`.
- Reservation information is never editable from the bidding page and is visually distinct from bid inputs. It is shown to ordinary users and administrators on every supported platform, including Android.
- Edits remain local until **Save bids**; saving replaces all bids atomically.
- Unsaved changes trigger route/back confirmation where appropriate.
- Inputs accept whole numbers only, never below zero.
- Minus is disabled at zero; plus is disabled when no tokens remain.
- Manual typing may temporarily exceed budget, but fields and summary show an error and saving is disabled.
- Zero counts as empty and remains eligible for auto-distribution.
- A positive field disables its auto-distribution selector.
- Auto-distribution changes token amounts only and preserves every selected attendance period.
- The UI explains that only successful bids are deducted and that all remaining tokens, including unsuccessful bids, are still subject to the carry-over cap.
- The UI explains that reservations reduce the seats available for assignment but do not change the employee's token balance, bid limits, or bid price.
- The UI explains that half-day bids cost the full individual amount, complementary bids may be paired into one higher-scoring unit, and unmatched half-day bids still participate individually.

Auto-distribution:

1. Select one or more eligible zero-value days.
2. Compute `share = remainingTokens ~/ selectedCount`.
3. Add `share` to every selected day.
4. Leave `remainingTokens % selectedCount` unallocated.
5. Resulting values become ordinary editable values.
6. If `share == 0`, do not modify fields and explain that too few tokens remain.

Load current reservation information whenever the bidding context is opened, after a successful save, when the app resumes/returns to the page, and through existing explicit retry/refresh behavior. Real-time delivery of reservation changes is not required and is unrelated to bid-reminder Web Push. Refreshing reservation metadata MUST preserve an unsaved local bid draft when the round ID has not changed; if the round changed, use the existing stale-round handling rather than applying the draft silently.

On `409`, preserve the draft, refresh context, and explain whether cutoff or a concurrent/stale round caused failure. Never silently apply a draft to another round.

### 13.6 Settings and Web Push enrollment

The authenticated Settings page is available on desktop and mobile. Notification preferences are account-level and therefore display the same authoritative enabled state and start weekday everywhere.

The page initially shows a **Bid reminders enabled** toggle, off by default. When off, reminder schedule, enrollment, and device controls are hidden. Switching it off updates the server preference but does not unsubscribe any device or remove the saved start weekday. Switching it on reveals:

- a **Start reminders on** dropdown containing Monday through Friday, defaulting to Monday;
- explanatory text that one reminder is sent at the application-wide time on the selected day and every following weekday until a positive bid is saved or the round is suppressed;
- the configured local reminder time and shared business time zone as read-only information;
- a current-device status and, when appropriate, **Enable notifications on this device**;
- all active registered devices with best-effort labels, registration/last-seen dates, optional last vendor-accepted push date, and an individually confirmed **Remove device** action;
- a warning when reminders are globally enabled but no active device is registered.

If the deployment-level reminder scheduler is disabled, show a non-sensitive unavailable message and do not imply that reminders will be delivered; stored preferences and subscriptions remain visible/manageable according to the enabled-toggle rules.

The global toggle MUST NOT itself subscribe a browser. **Enable notifications on this device** performs capability detection, and its direct tap/click initiates the browser permission request and `PushManager.subscribe` call. Only after permission and subscription succeed does the client register the result with the backend. Permission denial leaves the account preference intact, shows platform-appropriate instructions, and never repeatedly prompts automatically.

Use feature detection for service workers, `PushManager`, and notification display rather than making browser names a security or capability decision. Platform/display-mode hints MAY tailor guidance:

- On iOS/iPadOS, Web Push requires a supported OS and a Home Screen web app. If unavailable in an ordinary browser context, explain how to add and launch the app from the Home Screen; never claim enrollment succeeded merely because an icon exists.
- On supported Android browsers, enrollment works through Web Push without a native application. PWA installation is recommended for the app-like experience but is not a backend requirement.
- A desktop browser may configure the synchronized account preference without registering itself, or may register as another delivery device if it supports Web Push and the user explicitly chooses to do so.

The current client reconciles its local `PushManager` subscription with the backend when Settings opens and after service-worker/subscription changes. It never displays another device as having verified OS permission. Removing a device prevents further server sends to it; globally disabling reminders merely pauses sends to all retained devices.

The reminder deep link opens `/settings/reminders/skip` or the bidding page with the relevant round context. **Skip reminders this week** always shows a confirmation explaining that suppression lasts for the current bidding round, cannot be undone, and automatically clears by scope for the next round. The client posts the authoritative current `roundId`; stale, completed, or already-satisfied state is refreshed and explained rather than suppressed silently.

### 13.7 Administrative seat reservations

This page exists only in the desktop/wide PWA and only when `/api/me` reports `isAdmin: true`. The client-side check controls presentation; every backend call still enforces `ADMIN` and rechecks the database flag.

The page provides:

- an accessible date picker with manual `YYYY-MM-DD` entry fallback;
- a positive whole-number reserved-seat field that displays the applicable physical capacity;
- an optional description field limited to 500 Unicode code points with a clear warning that the text will be visible to all authenticated users;
- an **Add reservation** action;
- a date-range filter and chronologically ordered list of reservations showing date, count, description, round/cutoff state, and whether deletion remains allowed;
- a confirmed **Delete** action for mutable reservations, disabled with an explanation for immutable ones;
- loading, empty, validation, `403`, `409`, retryable error, and successful-create/delete states.

The page provides no editing: an admin deletes and recreates a mutable reservation. It contains no employee list and no administrator-management control. After a successful mutation it reloads authoritative server state. A `403` removes the admin destination from the current UI state and returns to a core page; a `409` refreshes the affected reservation and explains that cutoff, processing, or a concurrent action made the change unavailable.

### 13.8 Help content

Help MUST explain:

- weekly grants, balances, spending, and capped carry-over;
- placing, changing, saving, and auto-distributing bids;
- full-day/morning/afternoon selection, full-price half-day bids, complementary pairing, summed pair scores, and standalone unmatched half-day bids;
- cutoff and privacy before cutoff;
- allocation-unit scoring and global boundary fairness, in ordinary language: full-day and unmatched half-day bids use their individual tokens, complementary pairs use their combined tokens, and equally scored units competing for remaining seats are considered across the week so successful tie-breaks are spread as evenly as possible; equally fair final choices are random;
- successful bids being charged, unsuccessful bids remaining unspent, and the carry-over cap;
- first-time email verification, password creation, persistent form-cookie login, inactivity expiry, and logout;
- assignment colors, today indicator, participant order, and capacity boundary;
- half-day badges, grouped complementary pairs, individual versus combined tokens, and why assigned employee count may exceed occupied seats;
- reserved seats reducing the number available for bidding, with reserved counts, assignable capacity, and public descriptions shown both while bidding and in published assignment cards;
- surplus/unassigned seats being outside application control;
- account-level bid-reminder preferences, the Monday–Friday start-day behavior, positive-bid stopping rule, per-device enrollment, multiple-device delivery, global disabling without unregistration, round-only suppression, and best-effort delivery;
- iPhone Home Screen installation/permission requirements, Android and desktop enrollment, notification/system-setting troubleshooting, and removal of a registered device.

End-user help MUST NOT use implementation terminology such as bipartite matching, optimisation objectives, or lexicographic max-min fairness, and MUST NOT imply that each day's boundary tie is drawn independently.

### 13.9 Android app download promotion

When the PWA detects an Android browser, it MAY show **Get the Android app** in the menu and a non-intrusive footer when space permits. It MUST not displace core controls. Hide it in the native app. Detection is progressive enhancement, not a business/security rule, and the target SHOULD be a managed distribution/store page rather than a raw APK.

### 13.10 Accessibility and localization

- Support keyboard navigation, screen readers, scalable text, touch targets, focus indicators, and WCAG AA contrast.
- Never communicate status by color alone.
- Theme-derived attendance-control foreground/background pairs MUST retain WCAG AA contrast in light, dark, high-contrast, hover, pressed, disabled, and focused states. Icons, persistent text, and semantics remain understandable without color perception or animation.
- Dates are presented as agreed (`dd/MM`) while weekday labels and prose are localizable.
- Store/transport no locale-specific numeric formats; token values are integers.

### 13.11 PWA behavior

- Provide a valid manifest, icons, service worker, and installability metadata.
- Use one application-owned production service worker to receive Web Push. It MUST NOT import Flutter's deprecated generated service worker or depend on Flutter-managed application-shell caching. Every received push is immediately shown as a user-visible notification; silent/background-only push is prohibited.
- Notification payload handling supports a generic title/body, round identifier, safe same-origin bidding/confirmation paths, and best-effort action identifiers. Validate payload type/version and ignore unsafe or unknown URLs/actions.
- `notificationclick` closes the notification, focuses an existing same-origin application window when possible or opens one otherwise, and navigates to the safe route. Unsupported custom actions fall back to opening the ordinary bidding/reminder UI.
- Keep service-worker activation, push, notification-click, and subscription lifecycle logic compatible across upgrades. A replacement worker activates promptly, claims existing clients, and causes at most one controlled reload of an already controlled client. The first standalone-worker migration also reloads existing windows once so clients running the previously deployed bootstrap can recover without manual cache clearing. Navigation and executable application-shell requests bypass stale HTTP cache while the worker controls the client. The worker registration bypasses the ordinary HTTP cache when checking its script and removes the legacy Flutter application-shell cache during migration. Reconcile the current subscription on authenticated Settings entry rather than relying exclusively on `pushsubscriptionchange`, which is not universal.
- Authenticated API responses containing personal data MUST NOT be cached. Application-shell caching MAY be introduced only through a deliberate application-owned strategy that preserves immediate update compatibility.
- Core bidding requires connectivity; offline changes MUST NOT appear saved. Show offline state clearly.
- Mutable application-shell resources, including the HTML entry point, Flutter bootstrap, compiled application bundle, manifests, and service-worker script, MUST be served with immediate revalidation (`no-cache` or `max-age=0`). Updates MUST avoid mixing incompatible client/API versions and SHOULD activate without requiring users to clear browser data manually.

## 14. Web Push delivery

Version 1.3 reminders use standards-based Web Push for installed iPhone/iPad Home Screen web apps, compatible Android browsers/PWAs, and compatible desktop browsers. They do not depend on the optional native Android application, local device alarms, an open browser tab, or a continuously running Flutter process.

The backend signs requests with configured VAPID credentials and encrypts each payload for the target subscription. Browser-vendor push services perform final delivery. Use conservative TTL/urgency/topic values appropriate to a same-day reminder; a later reminder for the same employee/round/date must not create duplicate logical content. Vendor responses are classified into accepted, temporary failure, and permanent failure. Permanent expiry/removal responses invalidate the subscription; redirects are not followed blindly.

The initial user-visible template is title **Seat bidding reminder** and body **You have not placed your bids for next week yet.** Action labels are **Place bids** and **Skip reminders this week** where supported. Content is versioned in the payload so future clients can reject or safely fall back from unknown formats; it remains generic enough for lock-screen display.

The optional native Android application has no device-local reminder requirement in version 1.3. If native push is added later, it should reuse the synchronized account preference, eligibility, suppression, and dispatch concepts through a platform-token adapter rather than introduce an independent reminder schedule. Android users can use the web application/PWA for the current Web Push feature.

## 15. Deployment

### 15.1 Build and image

1. Build Flutter web in release mode.
2. Copy its output into Quarkus `META-INF/resources` during the build.
   Mutable application-shell resources are served with immediate browser revalidation; long-lived caching is permitted only for content-addressed immutable assets.
3. Build the Quarkus container image with Jib using a Java 25-compatible runtime base image. The application MUST run on Java 25 inside the resulting container.
4. Run one application container containing REST API, PWA assets/service worker, allocation and reminder schedulers, Web Push sender, ORM, and Liquibase.
5. Run PostgreSQL separately (container or managed service).

No Node web server, Qute rendering, or separate PWA container is required. The Android APK/AAB is distributed separately.

### 15.2 Runtime topology

```text
Browser/PWA ─┐
             ├─ HTTPS ─> Quarkus application container ─> PostgreSQL
Android app ─┘              │
                            ├─ SMTP/TLS ─> company mail server
                            └─ HTTPS/Web Push ─> browser-vendor push services ─> registered devices
```

Use TLS at a reverse proxy/platform boundary. The PWA calls same-origin relative URLs, so the application hostname is not compiled into it. Forwarded headers, secure-cookie handling, allowed-origin validation, and SPA fallback must be configured safely. The SMTP server and validated browser-vendor push endpoints must be reachable from the application container; mobile clients connect to neither directly. Network policy/firewalls MUST allow required push-service egress (including Apple Web Push endpoints for iPhone/iPad subscribers) without granting unrestricted access to private/internal address ranges.

### 15.3 Future multi-instance deployment

If Kubernetes is introduced later, the agreed direction is a StatefulSet: all pods serve HTTP, but only ordinal zero (hostname ending `-0`) enables scheduled jobs through deployment configuration/hostname logic. Do not add distributed scheduler coordination in the current release. Database transaction and uniqueness rules remain mandatory for concurrent HTTP traffic.

## 16. Quarkus YAML configuration contract

Backend configuration MUST use YAML; an `application.properties` file MUST NOT be used. Shared configuration belongs in `src/main/resources/application.yaml`. Profile-specific values MUST be placed in separate profile-aware files such as `application-dev.yaml`, `application-test.yaml`, and `application-prod.yaml`. Profile-prefixed keys such as `%dev`, `%test`, and `%prod` are prohibited in every YAML file. Each profile file contains ordinary unprefixed keys and overrides the shared configuration through Quarkus's profile-aware file loading.

Names may be adjusted consistently, but all values must be externally configurable. Environment variables continue to provide deployment-specific values and secrets through Quarkus expression expansion:

```yaml
seat-bidding:
  tokens-per-round: 60
  carry-over-cap: 24
  seat-capacity: ${SEAT_CAPACITY}
  time-zone: "${SEAT_BIDDING_TIME_ZONE:Europe/Berlin}"
  scheduler:
    cron: "${SEAT_ASSIGNMENT_CRON:0 0 22 ? * FRI}"
    enabled: ${SEAT_ASSIGNMENT_SCHEDULER_ENABLED:true}
  reminders:
    schedule:
      cron: "${BID_REMINDER_CRON:0 0 10 ? * MON-FRI}"
      enabled: ${BID_REMINDER_SCHEDULER_ENABLED:true}
    web-push:
      vapid-subject: ${WEB_PUSH_VAPID_SUBJECT}
      vapid-public-key: ${WEB_PUSH_VAPID_PUBLIC_KEY}
      vapid-private-key: ${WEB_PUSH_VAPID_PRIVATE_KEY}
      time-to-live: ${WEB_PUSH_TTL:12H}
      connect-timeout: ${WEB_PUSH_CONNECT_TIMEOUT:5S}
      request-timeout: ${WEB_PUSH_REQUEST_TIMEOUT:10S}
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
```
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


OpenTelemetry MUST be configured as the common observability mechanism for logging, metrics, and tracing. Its SDK MUST be disabled by default with `quarkus.otel.sdk.disabled: true`, as shown above. Profile-specific files or deployment environment variables MAY enable it and configure OTLP exporters/endpoints, protocols, resource attributes, service identity, sampling, and signal-specific options. When enabled, logs MUST carry trace and span correlation identifiers where a tracing context exists. Enabling or disabling telemetry MUST require configuration only, not a code change.

File logging MUST use the configuration shown above. It is disabled unless `LOG_TO_FILE=true`, writes to `/logs/application.log`, uses the specified format, and does not rotate merely because the application starts. The container deployment MUST mount a writable volume at `/logs` whenever file logging is enabled. Console logging remains available for normal container operation.

The allocation and reminder `@Scheduled` methods both use `timeZone = "${seat-bidding.time-zone}"`; there are no schedule-specific time-zone properties. Their `cron` attributes reference their respective configured expressions. Both cron expressions, the shared `ZoneId`, feature flags, and reminder transport durations MUST be validated at startup. The default reminder cron fires exactly once at 10:00 Monday through Friday; no minute-based polling or catch-up scheduler is permitted.

Authentication durations, limits, password blocklist, Argon2id parameters, allowed web origins, Quarkus form-authentication settings, mail settings, Web Push endpoint limits, and VAPID key/subject compatibility MUST be validated at startup. Secrets such as the activation-code pepper, form-session encryption key, CSRF signature key, VAPID private key, subscription material, SMTP password, and password hashes must never be exposed through the public configuration endpoint. The authenticated notification-settings response may expose only the VAPID public key. `application-test.yaml` MUST use Quarkus `MockMailbox`, an injected fake Web Push transport, and MUST NOT contact a real SMTP or push service. The Android download URL may be exposed through the public configuration endpoint.

## 17. Testing and verification

The backend test suite MUST use a real PostgreSQL instance supplied by Testcontainers for every test that exercises Panache entities or repositories, Liquibase, SQL constraints, transactions, pessimistic locking, or scheduler persistence. This requirement also applies when such tests are organized or named as unit tests in the project. H2 and other in-memory database substitutes MUST NOT be used. Pure domain-logic tests that have no persistence dependency SHOULD remain ordinary database-free unit tests.

All backend tests and static-analysis/build jobs MUST execute with JDK 25 so local, CI, and production Java versions remain aligned.

### 17.1 Backend unit tests

- Balance/grant/carry-over calculations, including successful-bid deductions, cap, and expiry.
- Successful individual bids are charged in full, including both members of a successful pair; unsuccessful unit members create no debit.
- Configured physical capacity zero is rejected at startup; reservation-derived assignable capacity zero is supported; fewer/equal/more bidders than assignable capacity.
- Reservation count validation and `assignableSeatCapacity = physicalSeatCapacity - reservedSeatCount`.
- Attendance default/enum validation, period independence, zero normalization, and auto-distribution preserving periods.
- Majority-side highest-bid selection, exact-token selection ties, opposite-order pairing, unmatched singles, canonical pair identity, and combined scores.
- Deterministic unit-score classification of fixed winners, fixed losers, exact boundary candidates, and unresolved capacities.
- Round-level optimiser objective hierarchy, canonicalisation, and deterministic injected final selector.
- Friday cutoff, DST transitions, and target-date calculation.
- Shared-business-zone evaluation for both schedules, Monday–Friday reminder start-day eligibility, positive-bid detection, round suppression, and absence of catch-up behavior.
- Reminder eligibility combinations for disabled preferences, no active device, pre-start weekdays, positive bids, all-zero bid sets, deleted final bids, and current-round suppression.
- Generic versioned push-payload construction, safe route/action validation, VAPID transport response classification, and permanent versus temporary subscription failure handling through an injected fake transport.
- Dispatch idempotency, per-subscription failure isolation, count/status reconciliation, and no duplicate logical employee/round/business-date sends.
- Zero normalization and complete bid replacement validation.
- Email normalization and lookup behavior for known, unknown, activated, and unactivated employees.
- Six-digit code generation including leading zeroes, keyed digest verification, expiry, failed-attempt exhaustion, resend invalidation, and cooldown calculations.
- Password-policy length boundaries, Unicode handling, absence of composition rules, matching confirmation, and common/compromised-password blocklist.
- Custom Argon2id identity-provider success/failure, PHC parsing, parameter-upgrade detection, and generic invalid-credential behavior.
- `USER`/`ADMIN` role construction and current database revalidation for admin operations.

#### Allocation optimiser test matrix

The pure-domain suite MUST exercise the complete immutable problem model exhaustively for small cases where practical. PostgreSQL integration tests MUST cover persistence, transactions, ledger reconciliation, and scheduler behavior. At minimum, cover:

A. **Simple single-day boundary tie:** one unresolved seat and three exact boundary candidates produces exactly one winner. Every complete solution is equally fair, so the injected selector determines the selected candidate.

B. **Three equivalent employees across three days:** Alice, Bob, and Carol are eligible for one unresolved seat on Monday, Tuesday, and Wednesday. The only valid win-count distribution is `1 / 1 / 1`; `2 / 1 / 0` and `3 / 0 / 0` are invalid.

C. **Five unresolved seats among three equivalent employees:** when eligibility permits, the distribution is `2 / 2 / 1` in some employee order; `3 / 1 / 1` is invalid.

D. **Six unresolved seats among three equivalent employees:** the distribution is `2 / 2 / 2`.

E. **Employee with one opportunity:** Alice is eligible only on Monday; Bob and Carol are eligible Monday through Wednesday; one unresolved seat exists per day. Alice always receives Monday, while Bob and Carol receive Tuesday and Wednesday in either order.

F. **Unit-score ranking cannot be overridden:** include fixed higher-score units, an exact-score boundary group, and lower-score units. Only boundary candidates are eligible; no lower unit displaces a fixed winner or boundary candidate.

G. **Multiple unresolved seats on one date:** a boundary tie crossing multiple remaining capacity positions produces exactly the required winner count, never assigns an employee twice on that date, and participates correctly in round-level fairness.

H. **Different ties on different dates:** use different boundary token amounts and partially overlapping employee sets. Only each date's actual boundary candidates enter its eligibility edges; unrelated equal-token groups do not participate, and all global objectives hold.

I. **More candidates than unresolved seats:** verify maximum utilisation, then maximum distinct winners, then random selection only among alternatives tied under all objectives.

J. **More opportunities than employees:** after every possible employee has at least one boundary win, additional wins follow the lexicographic max-min objective.

K. **Multiple equivalent global optima:** a deterministic injected selector can choose different canonical alternatives, and every selectable alternative independently satisfies objectives 1–3. Statistical testing is not a substitute for verifying the selector's unbiased contract.

L. **Persistence stability:** after commit, repeated assignment queries invoke neither optimiser nor selector and always return the same selected solution and final ordering.

M. **Retry semantics:** a failure rolling back the processing transaction may lead a retry to another equivalent optimum. A round already committed as `COMPLETED` is neither recalculated nor redrawn.

N. **Token accounting after global allocation:** winners are charged exactly their bids; all losers, including global-tie losers, are charged zero; `successful_bid_tokens`, remaining balance, carry-over, expiry, `BID_SPEND`, participation snapshots, and ledger entries reconcile exclusively with the persisted final solution.

O. **Input-order independence:** permute bids, employees, target dates, insertion order, and collection order. With the same deterministic selector output, canonicalisation produces the same selected solution.

P. **No boundary ties:** deterministic ranking alone produces all results and the optimiser/random selector is not invoked unnecessarily. A round-level audit record is still persisted with a null random value.

Q. **Surplus capacity:** when bidders are fewer than seats, every bidder wins, surplus seats remain unassigned, and the fairness mechanism invents neither candidates nor assignments.

R. **Reservation reduces capacity before ranking:** with physical capacity 4 and one seat reserved, allocation behaves exactly as capacity 3 while the reservation itself creates no bidder, assignment, or token entry.

S. **All seats reserved:** with reserved count equal to physical capacity, assignable capacity is zero, every positive bidder loses and spends zero tokens, and no unresolved fairness slot is created.

T. **Reservation and global fairness:** reserved capacity changes the actual boundary group and unresolved slot count before the round-level problem is constructed; only that resulting boundary group participates.

U. **Non-allocation description:** changing only a mutable public description before cutoff does not affect allocation input semantics, although the current release performs the change through delete/recreate rather than an update endpoint.

V. **Balanced complementary counts:** two morning and two afternoon bids create two pairs; no half-day single remains.

W. **Excess half-day bidders:** morning bids `30, 20, 10` and two afternoon bids select the `30` and `20` morning bidders for pairing; the `10` bidder remains a `SINGLE` and remains fully eligible.

X. **Majority selection boundary tie:** when the final pairable position crosses equal bids, only that exact-token group is randomized. Higher bids always pair and lower bids never displace them.

Y. **Opposite-order pairing:** mornings `100, 50` and afternoons `40, 10` form scores `110` and `90`, not `140` and `60`. Equal-token composition alternatives use the injected pairing selector and are auditable.

Z. **Pair indivisibility and charging:** a winning pair assigns both members and charges both individual bids; a losing pair assigns neither and charges neither. No ledger debit equals the summed score.

AA. **Pair at capacity boundary:** a pair whose combined score is in an exact-score boundary tie is one indivisible fairness candidate and either both members win or both lose.

AB. **Fairness identity across dates:** the same two employee IDs form the same canonical pair identity even with reversed periods; a changed partner is a different identity. Global objective counts follow those identities.

AC. **Assigned employees exceed seats:** two winning pairs occupying two seats produce four assigned employees; occupied units never exceed assignable capacity.

AD. **Unmatched half-day winner:** an unpaired morning/afternoon single may win, pays the full bid, occupies one seat, and displays the half-day badge.

AE. **Reservation plus pairing:** reservations reduce capacity before units compete but do not reduce pair count or score; resulting unit boundary/fairness remains correct.

AF. **Input-order independence:** permuting half-day bids and equal-token candidates with deterministic injected selectors produces the same canonical pairs, units, and selected allocation.

### 17.2 Integration tests with PostgreSQL

- Liquibase from an empty database and constraint enforcement.
- Upgrade from the version 1.2 schema adds bid periods and allocation units without modifying baseline changesets; existing bids become full-day and existing assignments become one-member single units with unchanged outcomes/order/audit versions.
- Applying the notification migration after the already committed half-day version 1.3 changes leaves those changesets untouched, creates the notification tables/constraints/indexes, and gives every existing employee the effective disabled/Monday default without synthesizing subscriptions, suppressions, dispatches, or attempts.
- Pessimistic serialization of simultaneous submissions for the same participant.
- Independent employees can update concurrently.
- Cutoff racing with bid update.
- Admin reservation create/delete racing with cutoff and scheduler processing serializes on the round; no reservation can change after cutoff or after completion.
- Reservation uniqueness, weekday/date/count/description/capacity constraints and application validation.
- Non-admin, stale-role-cookie, and removed-admin attempts against `/api/admin/*` return `403`; granting admin takes effect after fresh authentication.
- Scheduler rollback/retry and no duplicate units, members/results, ledger entries, or successor round.
- Processing atomically persists pairing audit, allocation units, individual results, canonical fingerprints, objective summary, and capacity-selection value.
- Allocation audit input includes reservation identifier/count and assignable capacity but excludes the public description; published reservation data remains stable after completion.
- Database/application constraints enforce unit type/cardinality, compatible periods, canonical identity, coherent outcome, consecutive pair display, and one immutable result per positive bid.
- Round configuration snapshots remain unchanged after runtime configuration changes.
- Ledger reconciles with participation balances.
- Ledger and participation values are calculated only after, and reconcile exactly with, the globally selected persisted allocation.
- Open bids cannot be queried through published endpoints.
- Activation start/resume/resend against a captured test mailbox; no test may contact a real SMTP server.
- Concurrent activation/resend/password-creation requests cannot reuse a code or create conflicting password state.
- Codes and activation authorizations are stored only as digests/hashes and never appear in captured logs or problem responses.
- Argon2id password creation and verification, generic invalid-credential behavior, and configurable rehash detection.
- `/j_security_check` delegates credential verification to the custom Quarkus identity provider and sets the exact encrypted cookie attributes for both PWA and Android.
- Quarkus form-cookie inactivity expiration, 24-hour renewal interval, persistence across restarts, malformed/tampered-cookie rejection, logout cookie removal, and global invalidation after encryption-key rotation.
- `quarkus-rest-csrf` creates and signs the CSRF cookie and rejects missing, mismatched, malformed, and unsigned tokens on Jakarta REST JSON state-changing requests for both platforms; separate tests verify allowed-origin enforcement on `/j_security_check`.
- Authentication `401`/`403`/`429` behavior and `Retry-After` where applicable.
- Notification preference and device mutations are employee-scoped, CSRF-protected, and safe under concurrent registration/removal.
- Reminder suppression is idempotent, immutable, round-scoped, rejects stale/non-open rounds, and cannot be bypassed by toggling the global preference.
- The normal Quarkus reminder schedule fires from its configured cron in the shared zone, skips concurrent execution, creates one dispatch per eligible employee/date, sends to every snapshotted active subscription, isolates failures, and performs no polling/catch-up execution.
- Push transport tests use a local fake/injected transport, never Apple, Google, Mozilla, or another real vendor. Accepted, temporary-failure, permanent-failure, timeout, malformed response, and invalidation paths reconcile dispatch/attempt rows.
- Endpoint validation blocks non-HTTPS, credential-bearing, oversized, redirect-based, loopback, link-local, private-network, and unsafe DNS-resolution cases without leaking endpoint/key material.

Use Quarkus tests plus a shared Testcontainers PostgreSQL setup. The container SHOULD be reused across test classes or the test suite where supported to keep execution time reasonable, while database state MUST be isolated or reset between tests. The full Liquibase changelog MUST initialize the test database so tests exercise the production schema.

### 17.3 API contract tests

- OpenAPI matches implemented DTOs.
- Authentication start, form login, resend, verification, password creation, CSRF, logout, and cookie contracts match the documented OpenAPI operations and cookie security scheme.
- `/api/me` administrator flag, admin list/create/delete authorization and CSRF contracts, validation limits, and `403`/`404`/`409` responses.
- Notification settings return synchronized account preferences, shared-zone schedule information, public VAPID key, current-round suppression state, and non-secret active-device metadata. Preference, registration, removal, and suppression contracts enforce authentication, CSRF, ownership, validation, idempotency, and non-enumerating errors.
- Bidding context and successful bid-replacement responses expose physical, reserved, and assignable capacities plus the optional public reservation description for every open-round date without exposing other employees' bids.
- Bidding requests/responses enforce and round-trip attendance periods, including rollout omission as full day and invalid enum rejection.
- Published assignments expose occupied seats, assigned employees, unit ID/type/rank/score, individual tokens/period/display rank, pair adjacency, and reservation capacities/description.
- Problem details and all status mappings.
- Stale `roundId`, dates outside round, duplicate dates, negative/fractional/overspent bids.
- No employee identifier is accepted or honored.

### 17.4 Flutter tests

- Widget/golden tests for all assignment colors, today marker, reservation information, assignable-capacity boundary, and responsive layouts.
- Bid balance, spinner states, carry-over explanation, integer division/remainder, zero behavior, and disabled save.
- Bidding-page reservation badges/counts, assignable capacity, public description, read-only behavior, all-platform visibility, refresh after save/resume, and preservation of an unsaved draft during same-round metadata refresh.
- Independent three-state attendance controls: initial/load state, tap cycle, keyboard/screen-reader operation, calendar/sunrise/afternoon icon mapping, full-day versus shared half-day theme colors, visible text in every state, stable sizing, tooltip, next-state hint, focus and contrast, reduced-motion-safe transition, no color-only state, zero-save reset, period preservation through auto-distribution/save/refresh, and unsaved-change handling. Widget/golden coverage includes light and dark themes and compact/wide layouts.
- Navigation by tap, keyboard, and swipe; unsaved-change handling.
- Email-first login branching, password login, pending-code resume, resend cooldown, code verification, password requirements/confirmation, automatic form login after password creation, persistent-cookie restoration, inactivity expiry, CSRF recovery, logout, and generic authentication error states.
- Settings navigation and synchronized reminder controls appear in wide and compact PWA layouts; disabled state hides subordinate controls, enabled state shows weekday/schedule/device management, and Android promotion remains limited to eligible PWA contexts.
- Web Push platform tests cover capability detection, current-device reconciliation, direct-gesture permission requests, granted/denied/default permission states, iOS Home Screen guidance, Android/desktop enrollment, multiple-device metadata, global disable without unsubscribe, individual removal, and no automatic repeated prompts.
- Service-worker tests cover visible push display, safe payload/version handling, supported action routing, actionless fallback, focus-versus-open behavior, same-origin route enforcement, subscription reconciliation, standalone worker activation, one-time migration reload, legacy-cache removal, cache-bypassing update checks, controlled reload, and application-shell revalidation.
- Reminder deep-link/widget tests cover positive-bid completion, all-zero behavior, confirmed immutable current-round suppression, stale/already-satisfied state, and the absence of an undo action.
- Desktop PWA tests show the third admin destination and reservation CRUD UI only for admins at the wide breakpoint; compact web and Android never expose the controls or route.
- Assignment widgets group pair members, show morning/afternoon badges only for half-day bids, suppress full-day badges, distinguish occupied seats from employee count, and preserve persisted unit/member order.
- End-to-end flow: login, load bids, edit/save, cutoff simulation, published result.

### 17.5 Acceptance scenarios

1. With the maximum ordinary starting balance of 84 and a successful 20-token bid, an employee has 64 remaining; only 24 carries out when the cap is 24.
2. With a starting balance of 84 and an unsuccessful 20-token bid, no tokens are deducted; the remaining balance is 84 and only 24 carries out when the cap is 24.
3. With successful bids of 20 and 8 plus an unsuccessful bid of 15, exactly 28 tokens are deducted.
4. At capacity 2 with bids `20, 10, 10`, the 20-token bidder is a fixed winner and exactly one 10-token bidder wins the boundary tie. If this is the round's only unresolved tie, its two complete alternatives are globally equivalent and the final selector chooses between them. The winners are charged 20 and 10 respectively, the unsuccessful 10-token bidder is charged zero, and the persisted result never changes.
5. At capacity 4 with three positive bidders, all three win, all three bids are charged, and one seat remains unassigned.
6. One minute before cutoff, bids can be replaced; at/after cutoff, replacement returns `409` even before scheduler completion.
7. Two simultaneous updates for the same employee serialize under a pessimistic lock and cannot reserve more than the starting balance.
8. A provisioned employee with no password starts activation, receives one captured six-digit email code, verifies it, creates a compliant password, is automatically form-authenticated, and enters the application without another user action.
9. Returning with a valid pending code resumes at code entry; resend invalidates the old code, observes cooldown, and only the new code succeeds.
10. A successful login on either platform survives an app restart through the securely persisted encrypted cookie, uses Quarkus's 30-day inactivity expiry, and renews no more than once per 24 hours.
11. Logout removes the current client's cookie; an expired, malformed, tampered, or cookie encrypted with an obsolete key returns `401`.
12. PWA and Android bid replacement without a valid Quarkus REST CSRF cookie/header pair is rejected; the same request succeeds with the signed matching token.
13. Refreshing `/bids` directly serves Flutter `index.html`; `/api/unknown` remains an API `404`, not SPA HTML.
14. Alice, Bob, and Carol are each in the boundary tie for one unresolved seat on Monday, Tuesday, and Wednesday. Processing assigns exactly one boundary-tie seat to each employee. Which employee receives which day may be random among the `1 / 1 / 1` mappings, but no employee receives two or three while another receives none.
15. Alice is eligible only for the unresolved Monday boundary seat; Bob and Carol are eligible for the unresolved boundary seats on Monday, Tuesday, and Wednesday; one exists per day. Processing assigns Monday to Alice and distributes Tuesday and Wednesday between Bob and Carol. Randomness may decide which of Bob and Carol receives which remaining day.
16. Three otherwise equivalent employees are eligible for all five unresolved opportunities. Processing produces boundary-win counts of `2 / 2 / 1` in some employee order. A `3 / 1 / 1` solution is not equally fair and MUST NOT be eligible for random selection.
17. With physical capacity 12 and a three-seat reservation, published data shows 3 reserved and 9 assignable seats. At most nine bidders are assigned; the reservation has no bid, assignment, or token-ledger entry, and its public description is visible.
18. An administrator creates and deletes a future reservation before cutoff. A non-admin receives `403` for both operations. After cutoff, the same deletion returns `409` and the completed assignment view remains unchanged.
19. With every physical seat reserved, all positive bidders are unsuccessful, no bid is charged, and the published day clearly shows zero assignable capacity.
20. After `is_admin` is removed directly in the database, an admin mutation using a previously issued role-bearing cookie is rejected by the mandatory database recheck. No application endpoint can restore the role.
21. Before placing bids, every employee sees that a date with physical capacity 12 has two reserved seats, ten assignable seats, and the public reservation description. The information is read-only, appears in the bidding context on PWA and Android, does not alter token availability, and remains visible in the published assignment card after processing.
22. A new bidding draft shows `Full day` with a calendar icon and primary-container styling on every date. Toggling Monday once shows `Morning` with a sunrise icon and shared half-day styling; toggling it again shows `Afternoon` with an afternoon-sun icon and the same half-day styling, without changing Tuesday or any tokens. Every state retains its text, stable layout, keyboard focus, and announced value/next action. Saving a positive bid persists the period; auto-distribution does not alter it.
23. Morning bids `100, 50` and afternoon bids `40, 10` form two units scoring `110` and `90`. If both units win, all four employees are assigned, two physical seats are occupied, and each employee pays only their individual bid.
24. With morning bids `30, 20, 10` and two afternoon bids, the `30` and `20` morning bids pair while the `10` morning bid remains an eligible single. It is not automatically rejected for lacking a partner.
25. A half-day pair tied at the capacity boundary is handled as one indivisible unit. The selected global solution assigns both pair members or neither; individual charging follows that outcome.
26. The same two employees paired across two dates share one canonical fairness identity even if their periods reverse. If either employee pairs with someone else, that is a different fairness identity.
27. Published results group a pair's morning and afternoon employees adjacently, show their individual tokens and period badges, and may report more assigned employees than occupied seats. A full-day employee displays no period badge.
28. Upgrading a populated version 1.2 database sets existing bids to full day and maps every historical assignment to a single allocation unit without changing completed results, ledger entries, published ordering, or historical algorithm version.
29. A migrated or newly provisioned employee has reminders disabled and receives nothing until explicitly enabling the account preference and registering at least one device.
30. An employee enables reminders and chooses Wednesday on desktop, then registers an iPhone Home Screen PWA. The synchronized settings remain Wednesday on both clients; only the phone receives delivery unless the desktop is separately enrolled.
31. On iOS, opening the ordinary browser page shows Home Screen guidance instead of claiming notification support. Opening the installed supported PWA and tapping **Enable notifications on this device** invokes the system permission prompt; no prompt occurs automatically on page load.
32. With a Wednesday preference and no positive bid, Monday and Tuesday generate no dispatch, while Wednesday at 10:00 does. Saving one positive bid before Thursday prevents Thursday and Friday reminders.
33. Saving an all-zero bid set does not stop reminders. Saving a positive bid stops them; removing the last positive bid before a later eligible trigger makes the employee eligible again.
34. Confirming **Skip reminders this week** creates one immutable suppression for the open round. No later trigger sends for that round, disabling/re-enabling does not bypass it, no undo is offered, and the next round is eligible again.
35. Globally disabling reminders stops sends without removing registered devices or the selected weekday. Re-enabling reuses still-valid subscriptions without another permission prompt.
36. An eligible employee with two active devices produces one logical daily dispatch and one attempt to each device. Removing one device stops future attempts to it without affecting the other.
37. A supporting Android browser displays **Place bids** and **Skip reminders this week** actions. An iPhone/browser without custom actions opens the same in-app bidding/reminder choices when the notification body is tapped; suppression always requires authenticated confirmation.
38. Repeated or concurrent invocation for the same trigger cannot create a second employee/round/business-date dispatch or duplicate device attempt. One device failure does not prevent another employee or device from being processed; a permanent rejection invalidates only that subscription.
39. If the application is unavailable at the configured 10:00 trigger, no polling or later catch-up reminder is generated for that business date.
40. Both the Friday allocation job and weekday reminder job use the single configured `Europe/Berlin` zone, remain at their intended local times across DST changes, and never use a client, JVM, or database default zone for business weekday decisions.
41. Adding the notification feature after the committed half-day implementation appends new version 1.3 changesets without modifying the committed half-day changesets or any version 1.2 deployed changeset.

## 18. Observability and operations

- OpenTelemetry is the required instrumentation path for logs, metrics, and traces, while the SDK remains disabled by default. Production operators explicitly enable and configure export through profile YAML and/or environment variables.
- Structured logs include round ID, employee internal ID where necessary, operation, outcome, and trace ID; never passwords, activation codes/authorizations, authentication cookies, password hashes, encryption/signature keys, SMTP credentials, or unnecessary bid details.
- OpenTelemetry metrics SHOULD cover bid-save success/failure, scheduler duration/status, bidders per date, boundary-tie candidate and unresolved-slot counts, allocation duration, achieved objective summaries, lock conflicts, and open/completed round counts. Employee identities and complete eligibility patterns MUST NOT be metric labels.
- Allocation metrics SHOULD include aggregate morning/afternoon/full-day bid counts, pair/single unit counts, and occupied-seat versus assigned-employee counts without identity labels. Pair compositions and bids are not logged or traced as attributes.
- Authentication metrics SHOULD cover aggregate start/login/activation success and failure, rate limiting, email delivery failure, and form-cookie authentication failure without using email addresses or other high-cardinality personal identifiers as metric labels.
- Admin reservation metrics SHOULD cover aggregate create/delete success, validation/authorization failure, and conflicts. Structured audit logs record reservation ID, target date, count, acting employee ID, and outcome, but not the public description.
- Reminder metrics SHOULD cover scheduler execution status/duration, eligible employees, logical dispatches, attempted subscriptions, vendor-accepted sends, temporary/permanent failures, invalidated subscriptions, suppression creation, and preference/device mutations. Do not use employee IDs, device labels, endpoints, keys, round-specific eligibility sets, or vendor capability tokens as metric labels.
- Reminder structured logs may include internal dispatch/round/subscription IDs, business date, classified outcome, safe provider status, and trace ID. They MUST NOT contain endpoint URLs, encryption/auth keys, VAPID private material, payload ciphertext, notification content beyond a stable template identifier, or user-agent strings.
- OpenTelemetry traces SHOULD cover inbound REST requests, database operations, scheduled round processing, and scheduled reminder dispatch, with custom spans around deterministic classification, global optimisation, final selection, persistence, accounting, eligibility selection, and aggregate push transport where they materially improve diagnosis. Do not record bids, candidate identities, subscription material, random values, or complete solutions as span attributes.
- Alert when a due round is not completed, no open successor exists, the reminder job fails, push permanent/temporary failure rates exceed an operational threshold, Liquibase fails, or ledger reconciliation fails.
- Document a manual operational retry that calls the same idempotent processing service; do not repair results with ad hoc duplicate inserts.
- Back up PostgreSQL. Published history and token ledger are business/audit data.

## 19. Implementation order

1. Create Quarkus/Flutter projects and shared build packaging.
2. Add Liquibase schema, Panache entities/repositories, configuration validation, and bootstrap.
3. Implement employee provisioning schema, SMTP activation, the custom Argon2id Quarkus identity provider, built-in form authentication, Quarkus REST CSRF, and request authorization.
4. Implement bidding context/replacement with attendance periods and pessimistic locking.
5. Add administrator-role mapping/revalidation, reservation migration/entity/service/resources, and cutoff-safe concurrency.
6. Implement pure half-day selection/pairing/unit construction, unit-score classification, round-level fairness, labeled random selectors, normalized persistence/audit, ledger derivation, scheduler orchestration, and idempotency.
7. Implement published unit/member assignments and problem/OpenAPI contracts, including reservation information.
8. Build Flutter authentication, attendance-aware bidding, grouped assignments, desktop admin reservations, help, and responsive navigation.
9. Append the second version 1.3 migration and implement notification preferences, subscription lifecycle, round suppression, idempotent dispatch persistence, maintained Web Push transport, and the once-per-trigger Quarkus reminder scheduler without altering the committed half-day changes.
10. Add the cross-platform Settings page, capability/permission enrollment, service-worker push/click behavior, safe reminder deep links/confirmation, help, and platform-specific iOS/Android guidance.
11. Complete integration, migration, authorization, SSRF, concurrency, accessibility, service-worker, container, and end-to-end verification.

## 20. Future extensions

### 20.1 Deferred enhancements

- Configurable cadences beyond weekly and corresponding UI semantics.
- Public holidays, exceptional closures, and configurable workdays.
- Per-day capacity, multiple offices, physical seat numbers, and office attendance management.
- Additional administration functions, including employee provisioning, administrator-role management, reservation editing, post-cutoff overrides, and application settings.
- Password change, forgotten-password recovery, operator-assisted reset UI, and self-service session management.
- Additional notification types, per-user reminder times or individual weekdays, weekend reminders, email/SMS fallback, notification localization, editable device names, and administrator notification controls.
- Native Android push-token delivery integrated with the shared reminder preferences and suppression model.
- Kubernetes StatefulSet deployment with ordinal-zero scheduler activation.
- Historical browsing, analytics, exports, and privacy/retention controls beyond the latest published round.
- Localization beyond the initial language and date convention.
- Configurable attendance time ranges, half-day discounts, manual pairing, pair preferences, and employee-level fairness attribution across changing half-day partners.

## 21. Definition of done

Version 1.3 is complete when all mandatory rules in this document are implemented, the backend builds/tests/runs on Java 25, Liquibase provisions an empty PostgreSQL database and safely upgrades version 1.2 data, security and concurrency requirements pass against PostgreSQL, the PWA is served from the Jib-built Quarkus image, bid privacy is verified, and the responsive PWA supports the complete workflow without requiring the optional Android app.

Allocation completion requires individual token authority during pairing selection; opposite-order complementary pairing; eligible unmatched half-day singles; indivisible pair units scored by bid sum; unit-score capacity ranking; global fairness over canonical single/pair identities; maximum utilisation, distinct winners, and lexicographic max-min distribution; randomness only among token-equivalent pairing choices or globally equivalent solutions; order independence; stable persisted units/member results/audit; and individual successful-bid-only accounting.

The administration extension is complete when `is_admin` is manually maintainable but never application-manageable; backend authorization and current-database revalidation protect every admin operation; a newly numbered Liquibase migration preserves the deployed schema/data; desktop PWA admins can list, create, and delete valid reservations; compact web, ordinary users, and Android expose no management UI; cutoff makes reservations immutable; allocation uses reservation-derived assignable capacity; every employee sees read-only reservation counts, assignable capacity, and descriptions while bidding; published assignments show the same reservation information; and all migration, authorization, concurrency, capacity, UI, and acceptance tests in section 17 pass.

The half-day extension is complete when independent accessible three-state controls round-trip through the API; their persistent labels, semantic icons, theme-derived full-day/half-day colors, stable sizing, focus, tooltip, contrast, animation, and screen-reader behavior satisfy section 13; deployed data migrates to full-day single units without historical changes; pairing, unit ranking, fairness, persistence, display, and charging satisfy sections 5–9; paired members are grouped with badges and individual bids; occupied seats are distinguished from assigned employees; and every half-day, integration, contract, Flutter, migration, and acceptance test in section 17 passes.

The Web Push extension is complete when notification preferences are disabled by default and synchronize across clients; Monday–Friday start-day behavior, positive-bid completion, immutable round suppression, retained global-disable subscriptions, and multi-device delivery satisfy sections 5–14; the normal Quarkus job runs once per configured trigger in the shared business zone without polling/catch-up; subscription secrets and outbound endpoints are secured; iOS Home Screen, Android web, desktop, permission, service-worker, action/fallback, and safe deep-link flows work as specified; new changesets append after rather than modify the committed half-day work; fake-transport PostgreSQL tests cover dispatch idempotency and failure isolation; and all notification acceptance scenarios in section 17 pass.